package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.Render3DEvent;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.render.Renderer3D;
import me.collective.render.ShapeMode;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.ExplosionDamageUtil;
import me.collective.util.FindItemResult;
import me.collective.util.GrimUtils;
import me.collective.util.AuraCoordination;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import me.collective.util.SwapManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class GrimAnchorAura extends Module {
    private static final int ROTATION_PRIORITY = 40;

    // Случайный генератор для рандомизации курсора (обход детекта фиксированных координат)
    private static final Random RANDOM = new Random();

    public enum RotationMode {
        SILENT, CLIENT, BOTH
    }

    // Цвета боксов визуализации: новая установка — фиолетовая, работа
    // с существующим якорем — янтарная.
    private static final Color PLACE_FILL  = new Color(124, 92, 255, 24);
    private static final Color PLACE_LINE  = new Color(124, 92, 255, 160);
    private static final Color ANCHOR_FILL = new Color(255, 176, 66, 24);
    private static final Color ANCHOR_LINE = new Color(255, 176, 66, 160);

    private enum ActionType {
        PLACE, CHARGE, EXPLODE
    }

    public final EnumSetting<RotationMode> rotationMode     = add(new EnumSetting<>("Rotation", RotationMode.SILENT));
    // Коррекция движения при тихой ротации.
    //   true  — клиент считает вектор движения по ОТПРАВЛЕННОМУ серверу углу,
    //           ввод W/A/S/D не трогаем. Совпадение с серверным предиктом точное.
    //   false — старое поведение: ввод перемапливается на шаги по 45° и скорость
    //           подравнивается. Ошибка до ±22.5°, GrimAC ловит как Strafe.
    public final BooleanSetting syncMovement                  = add(new BooleanSetting("SyncMovement", true));
    public final NumberSetting targetRange                   = add(new NumberSetting("TargetRange", 10.0, 1.0, 15.0, 0.5));
    public final NumberSetting placeReach                    = add(new NumberSetting("Reach", 4.2, 1.0, 4.5, 0.1));
    public final NumberSetting turnSpeed                     = add(new NumberSetting("TurnSpeed", 65.0, 15.0, 180.0, 5.0));
    public final NumberSetting smoothness                    = add(new NumberSetting("Smoothness", 0.65, 0.0, 0.95, 0.05));
    public final BooleanSetting targetCreative               = add(new BooleanSetting("TargetCreative", true));
    public final BooleanSetting targetMobs                   = add(new BooleanSetting("TargetMobs", false));
    public final BooleanSetting testAlone                    = add(new BooleanSetting("TestAlone", false));
    public final BooleanSetting predict                      = add(new BooleanSetting("Predict", false));

    public final NumberSetting placeDelay                    = add(new NumberSetting("PlaceDelay", 1.0, 0.0, 5.0, 1.0));
    public final NumberSetting chargeDelay                   = add(new NumberSetting("ChargeDelay", 1.0, 0.0, 5.0, 1.0));
    public final NumberSetting breakDelay                    = add(new NumberSetting("BreakDelay", 1.0, 0.0, 5.0, 1.0));

    // Тики ожидания подтверждения с сервера после действия.
    // GrimAC обрабатывает блоки не мгновенно — нужно дождаться
    // хотя бы 1-2 тика прежде чем считывать состояние мира.
    public final NumberSetting serverConfirmTicks            = add(new NumberSetting("ServerConfirm", 2.0, 1.0, 6.0, 1.0));

    public final BooleanSetting checkDimension               = add(new BooleanSetting("CheckDimension", true));
    public final BooleanSetting selfDamageSafety             = add(new BooleanSetting("Safety", false));
    public final NumberSetting minSafeDistance               = add(new NumberSetting("SafeDist", 3.0, 1.0, 6.0, 0.5));
    public final BooleanSetting replenish                    = add(new BooleanSetting("Replenish", false));
    // Друзья не получают больше FriendDmg урона от взрыва; они и в цели не берутся.
    public final BooleanSetting antiFriend                   = add(new BooleanSetting("AntiFriend", true));
    public final NumberSetting friendDamageLimit             = add(new NumberSetting("FriendDmg", 6.0, 0.0, 20.0, 0.5));
    // Бокс вокруг активной позиции якоря: фиолетовая — ставим, янтарная — заряжаем/взрываем.
    public final BooleanSetting render                       = add(new BooleanSetting("Render", true));

    // ── Настройки урона ──────────────────────────────────────────
    // Выбирать позицию размещения якоря по максимальному уроку цели
    public final BooleanSetting smartPlacement               = add(new BooleanSetting("SmartPlacement", true));
    // Минимальный урон по цели для выстрела (HP). Ниже — не взрываем
    public final NumberSetting minTargetDamage               = add(new NumberSetting("MinTargetDmg", 4.0, 0.0, 20.0, 0.5));
    // Максимально допустимый урон по себе (HP). Выше — пропускаем позицию
    public final NumberSetting maxSelfDamage                 = add(new NumberSetting("MaxSelfDmg", 8.0, 0.0, 20.0, 0.5));
    // Не взрывать если урон по цели меньше нашего урона на этот коэффициент
    public final NumberSetting damageRatio                   = add(new NumberSetting("DamageRatio", 1.5, 0.5, 5.0, 0.1));
    // Сколько позиций считать точным расчётом. Каждая позиция — это ~45 мировых
    // raycast'ов на каждую сущность, поэтому точная оценка идёт только по коротлисту.
    public final NumberSetting smartCandidates               = add(new NumberSetting("SmartCandidates", 8.0, 1.0, 17.0, 1.0));

    // ── Авто-атака возвышенных целей ─────────────────────────────
    // Если противник выше нас больше чем на указанную высоту (по дефолту 0.5 блока),
    // автоматически ставить якорь вплотную к нему и мгновенно взрывать
    public final BooleanSetting elevatedTarget              = add(new BooleanSetting("ElevatedTarget", true));
    public final NumberSetting elevatedThreshold            = add(new NumberSetting("ElevatedDiff", 0.5, 0.1, 2.0, 0.1));
    // При якоре вплотную к возвышенной цели урон по нам и по ней почти равный,
    // поэтому гейт соотношения ослабляется до 1.0, а самоурон выносится в проверку
    // «тотем / здоровье» в момент взрыва.
    public final NumberSetting elevatedRatio                 = add(new NumberSetting("ElevatedRatio", 1.0, 0.5, 5.0, 0.1));
    // Порог урона по цели для возвышенных — по умолчанию стреляем при любом уроне
    public final NumberSetting elevatedMinDamage             = add(new NumberSetting("ElevatedMinDmg", 1.0, 0.0, 20.0, 0.5));

    private BlockPos currentAnchorPos;
    private Entity currentTarget;
    private int actionTimer;
    private boolean aiming;

    /**
     * Закреплённая позиция установки.
     *
     * <p>Подбор позиции идёт каждый тик, и пока игрок движется, лучший по
     * урону кандидат скачет между блоками. Каждый скачок — это новая точка
     * наведения, и плавная ротация начинается заново, поэтому прицел почти
     * никогда не доводится до блока и установка растягивается. Держим позицию,
     * пока она остаётся примерно так же выгодной.
     */
    private BlockPos stickyPos;

    /** Id цели, для которой закреплена позиция: смена цели сбрасывает закреп. */
    private int stickyTargetId = -1;

    /** Доля от лучшего результата, ниже которой менять закреплённую позицию уже смысла нет. */
    private static final double STICKY_KEEP_RATIO = 0.75;

    private ActionType pendingAction;
    private BlockHitResult pendingHit;
    private BlockPos lastPlacedAnchorPos;
    private int placedWaitTimer;
    private BlockPos lastChargedAnchorPos;
    private int chargedWaitTimer;
    private BlockPos lastExplodedAnchorPos;
    private int explodedWaitTimer;

    public GrimAnchorAura() {
        super("GrimAnchorAura", "Automated Respawn Anchor placement, charging, and exploding for GrimAC.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        this.currentAnchorPos = null;
        this.currentTarget = null;
        this.stickyPos = null;
        this.stickyTargetId = -1;
        this.actionTimer = 0;
        this.lastPlacedAnchorPos = null;
        this.placedWaitTimer = 0;
        this.lastChargedAnchorPos = null;
        this.chargedWaitTimer = 0;
        this.lastExplodedAnchorPos = null;
        this.explodedWaitTimer = 0;
        cancelPending();
    }

    @Override
    public void onDisable() {
        RotationUtils.getInstance().release(this);
        cancelPending();
        this.currentAnchorPos = null;
        this.currentTarget = null;
        this.stickyPos = null;
        this.stickyTargetId = -1;
        this.lastPlacedAnchorPos = null;
        this.placedWaitTimer = 0;
        this.lastChargedAnchorPos = null;
        this.chargedWaitTimer = 0;
        this.lastExplodedAnchorPos = null;
        this.explodedWaitTimer = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        // Кэш ExplosionDamageUtil сбрасывается централизованно в CollectiveClient
        // на START_CLIENT_TICK — до всех модулей.
        this.aiming = false;
        tickAura();

        // Если в этом тике модуль ни разу не наводился, серверный угол больше не
        // нужен. Без этого MixinEntity продолжает подставлять его в
        // movementInputToVelocity, и движение уезжает даже когда аура просто
        // стоит у якоря и ничего не делает.
        if (!this.aiming) RotationUtils.getInstance().release(this);
    }

    private void tickAura() {
        // Якорь взрыва работает только вне Незера
        if (this.checkDimension.getValue() && mc.world.getRegistryKey() == World.NETHER) {
            cancelPending();
            RotationUtils.getInstance().release(this);
            return;
        }

        if (this.placedWaitTimer > 0) this.placedWaitTimer--;
        if (this.chargedWaitTimer > 0) this.chargedWaitTimer--;
        if (this.explodedWaitTimer > 0) this.explodedWaitTimer--;

        if (this.actionTimer > 0) {
            this.actionTimer--;
            // Продолжаем удерживать прицел пока ждём задержки действия.
            // Провод не отпускаем: серверный угол остаётся у грани действия,
            // следующий шаг цепочки (заряд/взрыв) не заводится заново.
            this.aiming = true;
            if (this.pendingHit != null && AuraCoordination.claim(this, mc.world.getTime())) {
                applyAim(this.pendingHit.getPos());
            }
            return;
        }

        this.currentTarget = findTarget();
        int targetId = this.currentTarget != null ? this.currentTarget.getId() : -1;
        if (targetId != this.stickyTargetId) {
            this.stickyTargetId = targetId;
            this.stickyPos = null;
        }

        if (this.currentTarget == null && !this.testAlone.getValue()) {
            cancelPending();
            this.currentAnchorPos = null;
            RotationUtils.getInstance().release(this);
            return;
        }

        // Синхронизация с CrystalAura: в тике действует одна аура — обе
        // держат один RotationUtils-провод, и одновременный заказ перезаписи
        // углов ломает проверки прицела друг друга. Слот берём только когда
        // есть цель: простаивающий якорь не съедает тики кристалла.
        // Отказ слота наведение не отменяет — доворот доживёт до своего тика.
        this.aiming = true;
        if (!AuraCoordination.claim(this, mc.world.getTime())) return;

        double reach = this.placeReach.getValue();

        BlockPos existingAnchor = findExistingAnchor(this.currentTarget, reach);
        if (existingAnchor != null) {
            this.currentAnchorPos = existingAnchor;
            this.lastPlacedAnchorPos = null;
            this.placedWaitTimer = 0;
            handleAnchorInteraction(existingAnchor, reach);
            return;
        }

        if (this.currentTarget == null) {
            cancelPending();
            this.currentAnchorPos = null;
            RotationUtils.getInstance().release(this);
            return;
        }

        // Ждём подтверждения размещённого якоря с сервера
        if (this.lastPlacedAnchorPos != null && this.placedWaitTimer > 0) {
            if (this.pendingHit != null) {
                applyAim(this.pendingHit.getPos());
            }
            return;
        }

        BlockPos candidate = findPlacementPosition(this.currentTarget, reach);
        if (candidate == null) {
            cancelPending();
            this.currentAnchorPos = null;
            RotationUtils.getInstance().release(this);
            return;
        }

        this.currentAnchorPos = candidate;
        handlePlacement(candidate, reach);
    }

    // ─────────────────────────────────────────────────────────────
    // Обработка взаимодействия с уже установленным якорем
    // ─────────────────────────────────────────────────────────────

    private void handleAnchorInteraction(BlockPos anchorPos, double reach) {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) {
            cancelPending();
            return;
        }

        int charges = state.get(RespawnAnchorBlock.CHARGES);

        if (charges == 0) {
            // Якорь пустой — нужно зарядить световым камнем
            if (this.lastChargedAnchorPos != null
                    && this.lastChargedAnchorPos.equals(anchorPos)
                    && this.chargedWaitTimer > 0) {
                if (this.pendingHit != null) applyAim(this.pendingHit.getPos());
                return;
            }

            FindItemResult glowstone = findGlowstone();
            if (!glowstone.found()) {
                cancelPending();
                return;
            }

            BlockHitResult hit = findBestInteractionHit(anchorPos, reach);
            if (hit == null) {
                cancelPending();
                return;
            }

            this.pendingAction = ActionType.CHARGE;
            this.pendingHit = hit;
            applyAim(faceCenter(hit));

            if (isAimedViaSentRot(hit, reach)) {
                if (!SwapManager.getInstance().isActionConflictTick()) {
                    executeCharge(anchorPos);
                }
            }

        } else {
            // Якорь заряжен — взрываем
            this.lastChargedAnchorPos = null;
            this.chargedWaitTimer = 0;

            if (this.lastExplodedAnchorPos != null
                    && this.lastExplodedAnchorPos.equals(anchorPos)
                    && this.explodedWaitTimer > 0) {
                if (this.pendingHit != null) applyAim(this.pendingHit.getPos());
                return;
            }

            if (this.selfDamageSafety.getValue() && isUnsafeToExplode(anchorPos)) {
                cancelPending();
                return;
            }

            // Взрыв не должен больше лимита задеть друзей поблизости.
            if (isFriendDamaged(anchorPos)) {
                cancelPending();
                return;
            }

            // ── Проверка урона перед взрывом ─────────────────────
            // Считаем сколько реально нанесём цели и себе
            if (this.currentTarget instanceof LivingEntity livingTarget) {
                boolean elevated = isTargetElevated(livingTarget);
                float targetDmg = ExplosionDamageUtil.getDamage(mc.world, anchorPos, livingTarget);
                float selfDmg   = ExplosionDamageUtil.getDamage(mc.world, anchorPos, mc.player);

                float minDmg  = effectiveMinTargetDamage(livingTarget);
                double ratio  = effectiveDamageRatio(livingTarget);

                // Общий порог: цель должна что-то получить
                if (targetDmg < minDmg) {
                    cancelPending();
                    return;
                }

                if (elevated) {
                    // Противник выше нас больше чем на полблока: якорь уже стоит
                    // вплотную, радиус взрыва (10) шире reach (4.2), поэтому
                    // самоурон почти неизбежен. Отменяем только если он летальный.
                    if (!selfDamageSurvivable(selfDmg)) {
                        cancelPending();
                        return;
                    }

                    // Соотношение здесь почти всегда ~1:1, поэтому гейт мягкий
                    if (selfDmg > 0.5f && targetDmg / selfDmg < ratio) {
                        cancelPending();
                        return;
                    }
                } else {
                    // Не взрываем если себе слишком много урона
                    if (selfDmg > this.maxSelfDamage.getValue().floatValue()) {
                        cancelPending();
                        return;
                    }

                    // Не взрываем если урон по цели не превышает наш урон в нужное кол-во раз
                    // selfDmg == 0 → всегда хорошо, не делим на ноль
                    if (selfDmg > 0.5f && targetDmg / selfDmg < ratio) {
                        cancelPending();
                        return;
                    }
                }
            }

            FindItemResult breakItem = findBreakItem();
            if (!breakItem.found()) {
                cancelPending();
                return;
            }

            BlockHitResult hit = findBestInteractionHit(anchorPos, reach);
            if (hit == null) {
                cancelPending();
                return;
            }

            this.pendingAction = ActionType.EXPLODE;
            this.pendingHit = hit;
            applyAim(faceCenter(hit));

            if (isAimedViaSentRot(hit, reach)) {
                if (!SwapManager.getInstance().isActionConflictTick()) {
                    executeExplode(anchorPos);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Размещение якоря
    // ─────────────────────────────────────────────────────────────

    private void handlePlacement(BlockPos placePos, double reach) {
        if (this.lastExplodedAnchorPos != null
                && this.lastExplodedAnchorPos.equals(placePos)
                && this.explodedWaitTimer > 0) {
            cancelPending();
            return;
        }

        FindItemResult anchor = findAnchor();
        if (!anchor.found()) {
            cancelPending();
            return;
        }

        BlockHitResult hit = findBestSupportHit(placePos, reach);
        if (hit == null) {
            cancelPending();
            return;
        }

        this.pendingAction = ActionType.PLACE;
        this.pendingHit = hit;
        // Целимся в центр грани, а не в точку попадания луча: она смещается
        // вместе с игроком на доли блока, и прицел никогда не сходится.
        applyAim(faceCenter(hit));

        if (isAimedViaSentRot(hit, reach)) {
            if (!SwapManager.getInstance().isActionConflictTick()) {
                executePlace(placePos);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Проверка прицела через ОТПРАВЛЕННЫЕ серверу углы
    // ─────────────────────────────────────────────────────────────

    /**
     * Проверяет: попадает ли raycast по нужному блоку, используя те углы,
     * которые реально ушли в пакетах движения (getSentYaw / getSentPitch).
     *
     * Важно: GrimAC делает raycast именно по OUTLINE-форме блока для
     * взаимодействий (interact), поэтому используем OUTLINE, а не COLLIDER.
     */
    private boolean isAimedViaSentRot(BlockHitResult expected, double reach) {
        if (mc.player == null || mc.world == null || expected == null) return false;

        RotationUtils rotUtils = RotationUtils.getInstance();
        Vec3d eye = mc.player.getEyePos();

        float yaw, pitch;
        if (this.rotationMode.getValue() == RotationMode.CLIENT) {
            yaw   = mc.player.getYaw();
            pitch = mc.player.getPitch();
        } else {
            yaw   = rotUtils.getSentYaw();
            pitch = rotUtils.getSentPitch();
        }

        Vec3d look = RotationUtils.getRotationVector(pitch, yaw);
        Vec3d end  = eye.add(look.multiply(reach + 0.1));

        // GrimAC использует OUTLINE для interact-пакетов
        BlockHitResult clip = mc.world.raycast(new RaycastContext(
            eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
        ));

        if (clip == null || clip.getType() != HitResult.Type.BLOCK) return false;
        if (!clip.getBlockPos().equals(expected.getBlockPos()))      return false;
        if (eye.squaredDistanceTo(clip.getPos()) > reach * reach)    return false;

        // Для плейсмента — грань должна совпасть, для interact — не важно
        if (this.pendingAction == ActionType.PLACE
                && expected.getSide() != null
                && clip.getSide() != expected.getSide()) {
            return false;
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────
    // Применение ротаций
    // ─────────────────────────────────────────────────────────────

    private void applyAim(Vec3d targetPos) {
        this.aiming = true;

        float[] rot      = RotationUtils.getRotationsTo(mc.player.getEyePos(), targetPos);
        double maxSpeed  = this.turnSpeed.getValue();
        double smooth    = this.smoothness.getValue();
        RotationMode mode = this.rotationMode.getValue();

        if (mode == RotationMode.CLIENT || mode == RotationMode.BOTH) {
            float curYaw   = mc.player.getYaw();
            float curPitch = mc.player.getPitch();

            float dy = MathHelper.wrapDegrees(rot[0] - curYaw);
            float dp = rot[1] - curPitch;

            double totalDist = Math.hypot(dy, dp);

            if (maxSpeed >= 180.0 && smooth <= 0.0) {
                mc.player.setYaw(curYaw + dy);
                mc.player.setPitch(MathHelper.clamp(rot[1], -90.0f, 90.0f));
            } else if (totalDist > 0.0001) {
                double decelRange = Math.max(16.0, maxSpeed * 0.40);
                double easePow    = 0.50 + (smooth * 0.30);
                double speedFactor = totalDist < decelRange
                    ? Math.pow(totalDist / decelRange, easePow) : 1.0;
                double desiredSpeed = Math.max(maxSpeed * speedFactor, 3.0);
                double step         = Math.min(totalDist, Math.min(desiredSpeed, maxSpeed));

                double dirY = dy / totalDist;
                double dirP = dp / totalDist;

                double stepY = dirY * step;
                double stepP = dirP * step;

                // GCD-квантование для имитации реального мышиного ввода
                double sens = mc.options.getMouseSensitivity().getValue();
                double f    = sens * 0.6 + 0.2;
                double gcd  = f * f * f * 8.0 * 0.15;
                if (gcd > 0.001) {
                    double qY = Math.round(stepY / gcd) * gcd;
                    double qP = Math.round(stepP / gcd) * gcd;
                    if (qY == 0 && Math.abs(dy) > gcd * 0.5) qY = Math.signum(dy) * gcd;
                    if (qP == 0 && Math.abs(dp) > gcd * 0.5) qP = Math.signum(dp) * gcd;
                    stepY = qY;
                    stepP = qP;
                }

                mc.player.setYaw(curYaw + (float) stepY);
                mc.player.setPitch(MathHelper.clamp(curPitch + (float) stepP, -90.0f, 90.0f));
            }
        }

        if (mode == RotationMode.SILENT || mode == RotationMode.BOTH) {
            RotationUtils rotUtils = RotationUtils.getInstance();
            if (mode == RotationMode.BOTH) {
                // BOTH: клиентский угол уже обновлён выше, серверный равен ему —
                // setRotationFullInstant сам ставит syncMovement = true.
                rotUtils.setRotationFullInstant(this, ROTATION_PRIORITY,
                    mc.player.getYaw(), mc.player.getPitch());
            } else {
                // SILENT — плавный серверный поворот, камера не двигается.
                //
                // syncMovement решает, как клиент посчитает вектор движения
                // относительно угла, который ушёл серверу:
                //   true  — MixinEntity подставляет в movementInputToVelocity
                //            серверный yaw, а MixinClientPlayerEntity не трогает
                //            ввод. Вектор совпадает с серверным до миллиметра.
                //   false — MixinEntity подставляет serverYaw + k*45, и ввод
                //            дополнительно перемапливается на k шагов. Две
                //            коррекции складываются и дают уход до 45°,
                //            именно это GrimAC и называет Strafe.
                rotUtils.setRotationSmooth(this, ROTATION_PRIORITY,
                    rot[0], rot[1], maxSpeed, this.syncMovement.getValue());
            }
            rotUtils.keepWireFresh();
        } else {
            RotationUtils.getInstance().release(this);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Выполнение действий — Place / Charge / Explode
    // ─────────────────────────────────────────────────────────────

    private void executePlace(BlockPos placePos) {
        BlockHitResult hit = this.pendingHit;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            cancelPending();
            return;
        }

        // Финальная перепроверка через отправленные углы
        if (!isAimedViaSentRot(hit, this.placeReach.getValue())) return;

        FindItemResult anchor = findAnchor();
        if (!anchor.found()) { cancelPending(); return; }

        if (!SwapManager.getInstance().hold(this, anchor.slot(), SwapManager.Priority.PLACE, 2)) return;

        // Рандомизируем курсор на грани блока: GrimAC детектит
        // всегда один и тот же offset (0.5,0.5,0.5) как бот
        BlockHitResult finalHit = randomizeCursor(hit);

        int seq = GrimUtils.nextSequence();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, finalHit, seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (this.predict.getValue()) {
            mc.world.setBlockState(placePos, Blocks.RESPAWN_ANCHOR.getDefaultState());
        }

        this.lastPlacedAnchorPos = placePos;
        // Ждём serverConfirmTicks — даём GrimAC время обработать размещение
        this.placedWaitTimer = this.serverConfirmTicks.getValue().intValue() + 1;
        this.actionTimer     = this.placeDelay.getValue().intValue();
        cancelPending();
    }

    private void executeCharge(BlockPos anchorPos) {
        BlockHitResult hit = this.pendingHit;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            cancelPending();
            return;
        }

        if (!isAimedViaSentRot(hit, this.placeReach.getValue())) return;

        FindItemResult glowstone = findGlowstone();
        if (!glowstone.found()) { cancelPending(); return; }

        if (!SwapManager.getInstance().hold(this, glowstone.slot(), SwapManager.Priority.PLACE, 2)) return;

        BlockHitResult finalHit = randomizeCursor(hit);

        int seq = GrimUtils.nextSequence();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, finalHit, seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (this.predict.getValue()) {
            mc.world.setBlockState(anchorPos,
                Blocks.RESPAWN_ANCHOR.getDefaultState().with(RespawnAnchorBlock.CHARGES, 1));
        }

        this.lastChargedAnchorPos = anchorPos;
        this.chargedWaitTimer     = this.serverConfirmTicks.getValue().intValue() + 1;
        this.actionTimer          = this.chargeDelay.getValue().intValue();
        cancelPending();
    }

    private void executeExplode(BlockPos anchorPos) {
        BlockHitResult hit = this.pendingHit;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            cancelPending();
            return;
        }

        if (!isAimedViaSentRot(hit, this.placeReach.getValue())) return;

        FindItemResult breakItem = findBreakItem();
        if (!breakItem.found()) { cancelPending(); return; }

        if (!SwapManager.getInstance().hold(this, breakItem.slot(), SwapManager.Priority.PLACE, 2)) return;

        BlockHitResult finalHit = randomizeCursor(hit);

        int seq = GrimUtils.nextSequence();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, finalHit, seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (this.predict.getValue()) {
            mc.world.setBlockState(anchorPos, Blocks.AIR.getDefaultState());
        }

        this.lastExplodedAnchorPos = anchorPos;
        this.explodedWaitTimer     = this.serverConfirmTicks.getValue().intValue() + 1;
        this.lastPlacedAnchorPos   = null;
        // Якоря на позиции больше нет — следующий надо ставить заново,
        // иначе keepSticky будет вечно упираться в заблокированный блок.
        this.stickyPos = null;
        this.placedWaitTimer       = 0;
        this.currentAnchorPos      = null;
        this.actionTimer           = this.breakDelay.getValue().intValue();
        cancelPending();
    }

    private void cancelPending() {
        this.pendingAction = null;
        this.pendingHit    = null;
    }

    // ─────────────────────────────────────────────────────────────
    // Рандомизация курсора на грани блока
    // ─────────────────────────────────────────────────────────────

    /**
     * GrimAC фиксирует, если курсор взаимодействия всегда равно центру грани (0.5, 0.5).
     * Добавляем случайное смещение в пределах ±0.35 от центра, имитируя реального игрока.
     *
     * Оффсет передаётся в поле cursorOnFace пакета PlayerInteractBlockC2SPacket
     * как Vec3d с координатами [0..1] относительно блока.
     */
    private BlockHitResult randomizeCursor(BlockHitResult original) {
        Vec3d hitPos = original.getPos();
        BlockPos blockPos = original.getBlockPos();
        Direction face = original.getSide();

        // Базовые координаты центра грани
        double bx = blockPos.getX();
        double by = blockPos.getY();
        double bz = blockPos.getZ();

        double rx, ry, rz;

        // Диапазон случайного смещения: ±0.30 от центра грани
        double RANGE = 0.30;

        switch (face) {
            case UP -> {
                rx = bx + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                ry = by + 1.0;
                rz = bz + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
            }
            case DOWN -> {
                rx = bx + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                ry = by;
                rz = bz + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
            }
            case NORTH -> {
                rx = bx + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                ry = by + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                rz = bz;
            }
            case SOUTH -> {
                rx = bx + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                ry = by + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                rz = bz + 1.0;
            }
            case WEST -> {
                rx = bx;
                ry = by + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                rz = bz + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
            }
            case EAST -> {
                rx = bx + 1.0;
                ry = by + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
                rz = bz + 0.5 + (RANDOM.nextDouble() - 0.5) * 2.0 * RANGE;
            }
            default -> { return original; }
        }

        return new BlockHitResult(new Vec3d(rx, ry, rz), face, blockPos, original.isInsideBlock());
    }

    // ─────────────────────────────────────────────────────────────
    // Поиск лучшей грани опорного блока для размещения якоря
    // ─────────────────────────────────────────────────────────────

    private BlockHitResult findBestSupportHit(BlockPos placePos, double reach) {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eye    = mc.player.getEyePos();
        double reachSq = reach * reach;

        BlockHitResult best     = null;
        double bestDist         = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = placePos.offset(dir);

            // Не кликаем по только что взорванной позиции — GrimAC флагует
            if (neighbor.equals(this.lastExplodedAnchorPos) && this.explodedWaitTimer > 0) continue;

            BlockState state = mc.world.getBlockState(neighbor);
            // Блок должен быть твёрдым, не заменяемым и не быть якорём
            if (state.isReplaceable()) continue;
            if (state.getCollisionShape(mc.world, neighbor).isEmpty()) continue;
            if (state.isOf(Blocks.RESPAWN_ANCHOR)) continue;
            if (!state.isSolidBlock(mc.world, neighbor)) continue;

            Direction clickFace = dir.getOpposite();

            // Грань должна смотреть на глаза — иначе GrimAC отклонит interact
            if (!isFaceFacingEye(eye, neighbor, clickFace)) continue;

            // Используем центр грани для raycast-проверки
            Vec3d sample = getFaceSamplePoint(neighbor, clickFace);
            Vec3d dirVec = sample.subtract(eye);
            if (dirVec.lengthSquared() < 1.0E-6) continue;

            Vec3d end = eye.add(dirVec.normalize().multiply(reach + 0.1));

            // COLLIDER — для физических взаимодействий (совместимо с Grim-placecheck)
            BlockHitResult clip = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player
            ));

            if (clip.getType() == HitResult.Type.BLOCK
                    && clip.getBlockPos().equals(neighbor)
                    && clip.getSide() == clickFace) {
                double d = eye.squaredDistanceTo(clip.getPos());
                if (d <= reachSq && d < bestDist) {
                    bestDist = d;
                    best = clip;
                }
            }
        }

        return best;
    }

    // ─────────────────────────────────────────────────────────────
    // Поиск лучшей грани самого якоря для заряда/взрыва
    // ─────────────────────────────────────────────────────────────

    private BlockHitResult findBestInteractionHit(BlockPos anchorPos, double reach) {
        if (mc.player == null || mc.world == null) return null;

        Vec3d eye    = mc.player.getEyePos();
        double reachSq = reach * reach;

        BlockHitResult best     = null;
        double bestDist         = Double.MAX_VALUE;

        for (Direction side : Direction.values()) {
            if (!isFaceFacingEye(eye, anchorPos, side)) continue;

            Vec3d sample = getFaceSamplePoint(anchorPos, side);
            Vec3d dirVec = sample.subtract(eye);
            if (dirVec.lengthSquared() < 1.0E-6) continue;

            Vec3d end = eye.add(dirVec.normalize().multiply(reach + 0.1));

            // Для взаимодействия с уже установленным блоком используем OUTLINE
            BlockHitResult clip = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
            ));

            if (clip.getType() == HitResult.Type.BLOCK
                    && clip.getBlockPos().equals(anchorPos)
                    && clip.getSide() == side) {
                double d = eye.squaredDistanceTo(clip.getPos());
                if (d <= reachSq && d < bestDist) {
                    bestDist = d;
                    best = clip;
                }
            }
        }

        return best;
    }

    // ─────────────────────────────────────────────────────────────
    // Вспомогательная геометрия
    // ─────────────────────────────────────────────────────────────

    /** Фиксированная точка наведения — центр грани блока. */
    private Vec3d faceCenter(BlockHitResult hit) {
        Direction side = hit.getSide();
        return side != null ? getFaceSamplePoint(hit.getBlockPos(), side) : hit.getPos();
    }

    /**
     * Грань считается «видимой» с позиции глаз, если глаза находятся
     * с правильной стороны блока. Это нужно для валидации GrimAC,
     * которая отклоняет клики по невидимым граням.
     */
    private boolean isFaceFacingEye(Vec3d eye, BlockPos pos, Direction face) {
        return switch (face) {
            case DOWN  -> eye.y < pos.getY();
            case UP    -> eye.y > pos.getY() + 1.0;
            case NORTH -> eye.z < pos.getZ();
            case SOUTH -> eye.z > pos.getZ() + 1.0;
            case WEST  -> eye.x < pos.getX();
            case EAST  -> eye.x > pos.getX() + 1.0;
        };
    }

    /**
     * Центральная точка грани блока — используется как начальное направление raycast.
     */
    private Vec3d getFaceSamplePoint(BlockPos pos, Direction face) {
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();

        return switch (face) {
            case DOWN  -> new Vec3d(minX + 0.5, minY,       minZ + 0.5);
            case UP    -> new Vec3d(minX + 0.5, minY + 1.0, minZ + 0.5);
            case NORTH -> new Vec3d(minX + 0.5, minY + 0.5, minZ);
            case SOUTH -> new Vec3d(minX + 0.5, minY + 0.5, minZ + 1.0);
            case WEST  -> new Vec3d(minX,        minY + 0.5, minZ + 0.5);
            case EAST  -> new Vec3d(minX + 1.0,  minY + 0.5, minZ + 0.5);
        };
    }

    // ─────────────────────────────────────────────────────────────
    // Проверка безопасности самоподрыва
    // ─────────────────────────────────────────────────────────────

    private boolean isUnsafeToExplode(BlockPos anchorPos) {
        if (this.testAlone.getValue() || !this.selfDamageSafety.getValue()) return false;

        boolean hasTotem = mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        Vec3d center     = anchorPos.toCenterPos();
        double playerDist = mc.player.getEyePos().distanceTo(center);

        if (hasTotem && playerDist >= 1.2) return false;

        double safeDist = this.minSafeDistance.getValue();
        if (playerDist < safeDist) {
            if (this.currentTarget != null) {
                double targetDist = this.currentTarget.getEyePos().distanceTo(center);
                if (playerDist <= targetDist - 0.5) return true;
            } else {
                return true;
            }
        }

        return !hasTotem && (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= 6.0f && playerDist < 4.0;
    }

    /**
     * Клетку заняла какая-нибудь сущность? Серверный canPlace для
     * полноблочного якоря отсекает столкновения со всеми сущностями —
     * от падёжных дропов до мобов, иначе клик молча отлетит.
     */
    private boolean entityBlocksCell(Box cell) {
        for (Entity other : mc.world.getOtherEntities(null, cell)) {
            if (other.getBoundingBox().intersects(cell)) return true;
        }
        return false;
    }

    /**
     * Взрыв на позиции заденет дружелюбного игрока сильнее лимита?
     * Сначала дешёвая верхняя граница (все лучи видимы), точный расчёт
     * только для кандидатов выше лимита — как в CrystalAura.
     */
    private boolean isFriendDamaged(BlockPos pos) {
        float limit = this.friendDamageLimit.getValue().floatValue();
        if (!this.antiFriend.getValue() || limit <= 0.0f) return false;

        Vec3d center = pos.toCenterPos();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isDead()) continue;
            if (!FriendManager.INSTANCE.isFriend(player)) continue;

            float upper = ExplosionDamageUtil.getDamageUpperBound(center, ExplosionDamageUtil.ANCHOR_POWER, player);
            if (upper <= limit) continue;

            float dmg = ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.ANCHOR_POWER, player);
            if (dmg > limit) return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────
    // Поиск якоря, цели и позиции для размещения
    // ─────────────────────────────────────────────────────────────

    private BlockPos findExistingAnchor(Entity target, double reach) {
        double reachSq = reach * reach;

        // Быстрый путь: якорь, который мы поставили сами. После установки
        // клиентский мир обновляется с задержкой, и полный скан в эти тики
        // всё равно ничего не находит — только тратит 1331 getBlockState.
        if (this.lastPlacedAnchorPos != null && this.placedWaitTimer > 0) {
            BlockState placed = mc.world.getBlockState(this.lastPlacedAnchorPos);
            if (placed.isOf(Blocks.RESPAWN_ANCHOR)
                    && GrimUtils.closestEyeDistanceSqTo(mc.player, this.lastPlacedAnchorPos.toCenterPos()) <= reachSq) {
                return this.lastPlacedAnchorPos;
            }
        }

        // Сначала проверяем текущий закешированный якорь
        if (this.currentAnchorPos != null) {
            BlockState curState = mc.world.getBlockState(this.currentAnchorPos);
            if (curState.isOf(Blocks.RESPAWN_ANCHOR)
                    && GrimUtils.closestEyeDistanceSqTo(mc.player, this.currentAnchorPos.toCenterPos()) <= reachSq) {
                return this.currentAnchorPos;
            }
        }

        // Скан вокруг цели: якорь ставится у неё, вокруг игрока пришлось бы
        // перебирать сильно больше пустых клеток с прежним радиусом.
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos scanCenter = target != null ? target.getBlockPos() : playerPos;
        int r = (int) Math.ceil(reach) + (target != null ? 1 : 0);
        BlockPos best     = null;
        double closestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            // Вертикально якорям хватать ±3: выше и ниже интерактив всё равно
            // не дотянется — вдвое меньше getBlockState на просмотр.
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = scanCenter.add(x, y, z);
                    if (GrimUtils.closestEyeDistanceSqTo(mc.player, p.toCenterPos()) > reachSq) continue;

                    BlockState state = mc.world.getBlockState(p);
                    if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
                        double d = target != null
                            ? target.squaredDistanceTo(p.toCenterPos())
                            : mc.player.squaredDistanceTo(p.toCenterPos());
                        if (target == null || d <= 36.0) {
                            if (d < closestDist) {
                                closestDist = d;
                                best = p;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }


    // ─────────────────────────────────────────────────────────────
    // Возвышенные цели
    // ─────────────────────────────────────────────────────────────

    /**
     * Противник стоит выше нас больше чем на {@link #elevatedThreshold}.
     *
     * <p>Сравниваются позиции НОГ ({@code getY()}), а не центры хитбоксов —
     * ровно то, что использует серверный расчёт урона, так что порог
     * «выше на пол блока» трактуется одинаково на клиенте и на сервере.
     */
    private boolean isTargetElevated(Entity target) {
        if (!this.elevatedTarget.getValue()) return false;
        if (target == null || mc.player == null) return false;
        return (target.getY() - mc.player.getY()) > this.elevatedThreshold.getValue();
    }

    /** Порог соотношения урона: для возвышенной цели он ослаблен. */
    private double effectiveDamageRatio(Entity target) {
        return isTargetElevated(target)
                ? this.elevatedRatio.getValue()
                : this.damageRatio.getValue();
    }

    /** Порог минимального урона по цели: для возвышенной он снижен. */
    private float effectiveMinTargetDamage(Entity target) {
        return isTargetElevated(target)
                ? this.elevatedMinDamage.getValue().floatValue()
                : this.minTargetDamage.getValue().floatValue();
    }

    /**
     * Есть ли тотем, который переживёт самоурон.
     * У возвышенной цели радиус взрыва (10 блоков) заведомо больше reach (4.2),
     * поэтому выстрелить без тотема можно только если здоровья хватает.
     */
    private boolean selfDamageSurvivable(float selfDmg) {
        if (selfDmg <= 6.0f) return true;

        boolean hasTotem = mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (hasTotem) return true;

        return (mc.player.getHealth() + mc.player.getAbsorptionAmount()) > selfDmg;
    }

    private BlockPos findPlacementPosition(Entity target, double reach) {
        double reachSq = reach * reach;
        BlockPos targetPos = target.getBlockPos();
        boolean elevated = isTargetElevated(target);

        // ── Пул кандидатов ─────────────────────────────────────────────
        // Возвышенная цель: берём только блоки НА ЕЁ УРОВНЕ — блок под ногами
        // и соседи по сторонам. Якорь вплотную вышибает её с опоры, а позиции
        // ниже уровня цели в таком раскладе бьют в основном по нам.
        List<BlockPos> candidates = new ArrayList<>();
        if (elevated) {
            candidates.add(targetPos);
            candidates.add(targetPos.north());
            candidates.add(targetPos.south());
            candidates.add(targetPos.east());
            candidates.add(targetPos.west());
            candidates.add(targetPos.north().east());
            candidates.add(targetPos.north().west());
            candidates.add(targetPos.south().east());
            candidates.add(targetPos.south().west());
            candidates.add(targetPos.up(1));
        } else {
            candidates.add(targetPos);
            candidates.add(targetPos.up(1));
            candidates.add(targetPos.up(2));
            candidates.add(targetPos.north());
            candidates.add(targetPos.south());
            candidates.add(targetPos.east());
            candidates.add(targetPos.west());
            candidates.add(targetPos.down());
            // Диагонали
            candidates.add(targetPos.north().east());
            candidates.add(targetPos.north().west());
            candidates.add(targetPos.south().east());
            candidates.add(targetPos.south().west());
            // На 1 блок дальше по кардинальным направлениям
            candidates.add(targetPos.north(2));
            candidates.add(targetPos.south(2));
            candidates.add(targetPos.east(2));
            candidates.add(targetPos.west(2));
        }

        // Фильтр: только доступные позиции
        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos p : candidates) {
            if (GrimUtils.closestEyeDistanceSqTo(mc.player, p.toCenterPos()) > reachSq) continue;
            if (!mc.world.getBlockState(p).isReplaceable()) continue;

            Box box = new Box(p);
            if (mc.player.getBoundingBox().intersects(box)) continue;
            if (target.getBoundingBox().intersects(box)) continue;

            // Серверный canPlace: полноблочный якорь не встаёт в клетку с
            // любой сущностью внутри (не только с целью) — иначе клик отлетит,
            // а цикл потеряет тики на фантомную установку.
            if (entityBlocksCell(box)) continue;

            // Взрыв по этой позиции не должен навредить друзьям.
            if (isFriendDamaged(p)) continue;

            if (this.selfDamageSafety.getValue() && isUnsafeToExplode(p)) continue;

            if (findBestSupportHit(p, reach) == null) continue;

            valid.add(p);
        }

        if (valid.isEmpty()) return null;

        // SmartPlacement: выбираем позицию с максимальным
        // уроном по цели при минимальном уроне себе
        if (this.smartPlacement.getValue() && target instanceof LivingEntity livingTarget) {
            List<BlockPos> pool = shortlist(valid, livingTarget);

            // Возвышенная цель: радиус взрыва шире reach, поэтому самоурон
            // неизбежен и почти одинаков для всех позиций. Ранжируем только
            // по урону по цели, а защиту оставляем проверке тотема/здоровья.
            if (elevated) {
                BlockPos bestPos = null;
                float bestTarget = Float.NEGATIVE_INFINITY;
                for (BlockPos p : pool) {
                    float targetDmg = ExplosionDamageUtil.getDamageRaw(mc.world, p, livingTarget);
                    if (targetDmg > bestTarget) {
                        bestTarget = targetDmg;
                        bestPos = p;
                    }
                }
                // Критерий закрепа здесь тот же, что и ранжирование: урон по цели.
                return keepSticky(bestPos, bestTarget, pool, livingTarget, bestTarget, true);
            }

            float maxSelf = this.maxSelfDamage.getValue().floatValue();
            float minDmg  = effectiveMinTargetDamage(target);
            double ratio  = effectiveDamageRatio(target);

            BlockPos bestPos = null;
            float bestScore = Float.NEGATIVE_INFINITY;

            for (BlockPos p : pool) {
                float targetDmg = ExplosionDamageUtil.getDamageRaw(mc.world, p, livingTarget);
                float selfDmg   = ExplosionDamageUtil.getDamageRaw(mc.world, p, mc.player);

                // Отбрасываем позиции где нам плохо
                if (targetDmg < minDmg) continue;
                if (selfDmg > maxSelf) continue;
                if (selfDmg > 0.5f && targetDmg / selfDmg < ratio) continue;

                // Оценка: разница "урон цели - урон себе"
                // Умножаем урон по цели на 2, чтобы приоритизировать
                // позиции с высоким уроном по противнику
                float score = targetDmg * 2.0f - selfDmg;
                if (score > bestScore) {
                    bestScore = score;
                    bestPos = p;
                }
            }

            // Нашли хорошую позицию — берём её, но если закреплённая позиция
            // ещё в пуле и почти не хуже, оставляем её: смена точки наведения
            // каждый тик обнуляет плавную ротацию и установка встаёт.
            if (bestPos != null) return keepSticky(bestPos, bestScore, pool, livingTarget, bestScore, false);

            // Нет позиции с хорошим соотношением → всё равно берём
            // лучшую по урону цели, но пропуская заведомо смертельные
            BlockPos fallback = null;
            float bestTarget  = Float.NEGATIVE_INFINITY;
            for (BlockPos p : pool) {
                float selfDmg = ExplosionDamageUtil.getDamageRaw(mc.world, p, mc.player);
                if (!selfDamageSurvivable(selfDmg)) continue;

                float targetDmg = ExplosionDamageUtil.getDamageRaw(mc.world, p, livingTarget);
                if (targetDmg > bestTarget) {
                    bestTarget = targetDmg;
                    fallback = p;
                }
            }
            return fallback;
        }

        // SmartPlacement выключен → старый режим: ближайшая к цели
        valid.sort(Comparator.comparingDouble(p -> target.squaredDistanceTo(p.toCenterPos())));
        BlockPos nearest = valid.get(0);
        return valid.contains(this.stickyPos) ? this.stickyPos : nearest;
    }

    /**
     * Возвращает закреплённую позицию, если она ещё доступна и её результат
     * не хуже {@code bestScore * STICKY_KEEP_RATIO}. Иначе перезакрепляет.
     */
    private BlockPos keepSticky(BlockPos best, float bestScore, List<BlockPos> pool,
                                LivingEntity target, float threshold, boolean elevated) {
        BlockPos pinned = this.stickyPos;

        if (pinned != null && !pool.contains(pinned)) {
            this.stickyPos = null;
            pinned = null;
        }

        if (pinned != null) {
            // Сравниваем по тому же критерию, по которому шло ранжирование:
            // иначе закреп держится на заведомо худшей позиции.
            float score = elevated
                    ? ExplosionDamageUtil.getDamageRaw(mc.world, pinned, target)
                    : scorePosition(mc.world, pinned, target);

            if (score >= threshold * STICKY_KEEP_RATIO) return pinned;
        }

        this.stickyPos = best;
        return best;
    }

    /** Та же оценка, что и в основном подборе: 2×урон цели минус самоурон. */
    private float scorePosition(net.minecraft.world.World world, BlockPos pos, LivingEntity target) {
        float targetDmg = ExplosionDamageUtil.getDamageRaw(world, pos, target);
        float selfDmg   = ExplosionDamageUtil.getDamageRaw(world, pos, mc.player);
        return targetDmg * 2.0f - selfDmg;
    }

    /**
     * Коротлист позиций для точного расчёта.
     *
     * <p>Точная оценка — это {@code ExplosionDamageUtil.getDamageRaw}, а внутри
     * неё 45 мировых raycast'ов на сущность. На 17 кандидатах это ~1500
     * raycast'ов за тик только на подбор позиции. Сначала сортируем по
     * дешёвой верхней границе (считаем все лучи видимыми, raycast'ов ноль),
     * и точный расчёт делаем только по первым {@link #smartCandidates}.
     */
    private List<BlockPos> shortlist(List<BlockPos> valid, LivingEntity livingTarget) {
        int limit = this.smartCandidates.getValue().intValue();
        if (valid.size() <= limit) return valid;

        List<BlockPos> pool = new ArrayList<>(valid);
        pool.sort(Comparator.comparingDouble(p -> -(
                ExplosionDamageUtil.getDamageUpperBound(p.toCenterPos(), ExplosionDamageUtil.ANCHOR_POWER, livingTarget)
              + 0.001 * ExplosionDamageUtil.getDamageUpperBound(p.toCenterPos(), ExplosionDamageUtil.ANCHOR_POWER, mc.player)
        )));

        return pool.subList(0, Math.max(1, limit));
    }



    private Entity findTarget() {
        double rangeSq  = this.targetRange.getValue() * this.targetRange.getValue();
        Entity best     = null;
        double minDist  = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (FriendManager.INSTANCE.isFriend(player)) continue;
            if (player.isSpectator() || player.isDead() || !player.isAlive()) continue;
            if (!this.targetCreative.getValue() && player.isCreative()) continue;

            double d = mc.player.squaredDistanceTo(player);
            if (d <= rangeSq && d < minDist) {
                minDist = d;
                best    = player;
            }
        }

        if (best == null && this.targetMobs.getValue()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof LivingEntity living)
                        || living instanceof PlayerEntity
                        || !living.isAlive()) continue;
                double d = mc.player.squaredDistanceTo(entity);
                if (d <= rangeSq && d < minDist) {
                    minDist = d;
                    best    = entity;
                }
            }
        }

        return best;
    }

    // ─────────────────────────────────────────────────────────────
    // Поиск предметов в инвентаре
    // ─────────────────────────────────────────────────────────────

    private FindItemResult findAnchor() {
        FindItemResult hotbar = InventoryUtil.findInHotbar(Items.RESPAWN_ANCHOR);
        if (hotbar.found()) return hotbar;

        if (this.replenish.getValue() && GrimUtils.inventoryClickClean()) {
            FindItemResult inv = InventoryUtil.find(Items.RESPAWN_ANCHOR);
            if (inv.found() && inv.slot() >= 9) {
                InventoryUtil.quickMove(inv.slot());
                return InventoryUtil.findInHotbar(Items.RESPAWN_ANCHOR);
            }
        }

        return hotbar;
    }

    private FindItemResult findGlowstone() {
        FindItemResult hotbar = InventoryUtil.findInHotbar(Items.GLOWSTONE);
        if (hotbar.found()) return hotbar;

        if (this.replenish.getValue() && GrimUtils.inventoryClickClean()) {
            FindItemResult inv = InventoryUtil.find(Items.GLOWSTONE);
            if (inv.found() && inv.slot() >= 9) {
                InventoryUtil.quickMove(inv.slot());
                return InventoryUtil.findInHotbar(Items.GLOWSTONE);
            }
        }

        return hotbar;
    }

    /**
     * Ищет предмет для взрыва якоря — всё что угодно кроме световых камней.
     * Тотем предпочтительнее для безопасного взрыва.
     */
    private FindItemResult findBreakItem() {
        if (mc.player == null) return new FindItemResult(-1, 0);

        int current       = mc.player.getInventory().getSelectedSlot();
        ItemStack curStack = mc.player.getInventory().getStack(current);
        if (!curStack.isOf(Items.GLOWSTONE)) {
            return new FindItemResult(current, curStack.getCount());
        }

        int preferredSlot = -1;
        int fallbackSlot  = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.GLOWSTONE)) {
                if (stack.isEmpty()
                        || stack.isIn(ItemTags.SWORDS)
                        || stack.isIn(ItemTags.AXES)
                        || stack.isOf(Items.TOTEM_OF_UNDYING)) {
                    preferredSlot = i;
                    break;
                }
                if (fallbackSlot == -1) fallbackSlot = i;
            }
        }

        int targetSlot = preferredSlot != -1 ? preferredSlot : fallbackSlot;
        if (targetSlot != -1) {
            return new FindItemResult(targetSlot, mc.player.getInventory().getStack(targetSlot).getCount());
        }

        return new FindItemResult(-1, 0);
    }

    // ─────────────────────────────────────────────────────────────
    // Визуал
    // ─────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isToggled() || !this.render.getValue()) return;

        BlockPos pos = this.currentAnchorPos;
        if (pos == null) return;

        boolean placing = this.pendingAction == ActionType.PLACE;
        Color fill  = placing ? PLACE_FILL : ANCHOR_FILL;
        Color line  = placing ? PLACE_LINE : ANCHOR_LINE;

        Renderer3D.box(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                fill, line, ShapeMode.Both);
    }
}
