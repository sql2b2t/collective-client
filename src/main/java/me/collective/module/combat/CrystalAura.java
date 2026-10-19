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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Кристальная аура под GrimAC.
 *
 * <p>Строгий порядок взаимодействия, по паттерну проверенного GrimAnchorAura:
 * <ul>
 *   <li>одна ротация на тик, все интеракции тика валидируются по ней;</li>
 *   <li>установка кристалла: луч по отправленному углу должен попасть
 *       в грань опорного блока (обсидиан/коренная порода), курсор на грани
 *       рандомизируется ±0.30 — Grim ловит фиксированный центр;</li>
 *   <li>разбиение кристалла: перед пакетом атаки досылается ротация
 *       ({@code flushServerRotation}), луч по отправленному углу обязан
 *       пересечь хитбокс кристалла, расширенный на 0.1, дистанция до бокса
 *       не больше лимита сервера (3.0);</li>
 *   <li>постановка и разбиение чередуются по разным тикам: цели почти всегда
 *       в разных направлениях, а одна ротация на два разных действия в тике
 *       вылетает из проверки Grim.</li>
 * </ul>
 *
 * <p>Симуляция постановки повторяет серверный {@code EndCrystalItem.useOnBlock}
 * (проверено по байткоду 1.21.11): клетка спавна — строго над кликнутым
 * блоком, клетка обязана быть воздухом, бокс 1×2×1 не должен пересекать ни
 * одну сущность (включая дроп) — иначе сервер молча отклоняет пакет.
 * Центр взрыва кристалла — позиция сущности, мощность 6.0.
 */
public class CrystalAura extends Module {
    private static final int ROTATION_PRIORITY = 40;

    /** Насколько Grim расширяет хитбокс сущности при проверке интеракции. */
    private static final double ENTITY_BOX_EXPAND = 0.1;

    /** Амплитуда рандомизации курсора на грани. */
    private static final double CURSOR_JITTER = 0.30;

    /** Через сколько миллисекунд кристалл считается снова атакуемым. */
    private static final long ATTACKED_TTL_MS = 600L;

    /** Сколько тиков держим цель, пока она примерно так же выгодна. */
    private static final int TARGET_HOLD_TICKS = 8;

    /** Доля от лучшего результата, ниже которой менять зацеп не имеет смысла. */
    private static final double STICKY_KEEP_RATIO = 0.75;

    private static final long STICKY_TTL_MS = 900L;

    /** Верхняя граница дистанции блок-интеракции на сервере (1.20.5+). */
    private static final double SERVER_BLOCK_REACH = 4.5;

    /** Тики ожидания подтверждения сервера после установки обсидиана. */
    private static final int SUPPORT_CONFIRM_TICKS = 2;

    private static final Random RANDOM = new Random();

    private static final Color ATTACK_FILL = new Color(110, 255, 150, 24);
    private static final Color ATTACK_LINE = new Color(110, 255, 150, 160);
    private static final Color PLACE_FILL  = new Color(124, 92, 255, 24);
    private static final Color PLACE_LINE  = new Color(124, 92, 255, 160);

    public enum RotationMode {
        SILENT, CLIENT, BOTH
    }

    public enum TargetMode {
        DAMAGE, DISTANCE, HEALTH
    }

    // ── Ротация ──────────────────────────────────────────────────────
    public final EnumSetting<RotationMode> rotationMode = add(new EnumSetting<>("Rotation", RotationMode.SILENT));
    public final BooleanSetting syncMovement            = add(new BooleanSetting("SyncMovement", true));
    public final NumberSetting turnSpeed                = add(new NumberSetting("TurnSpeed", 180.0, 15.0, 180.0, 5.0));
    public final NumberSetting smoothness               = add(new NumberSetting("Smoothness", 0.0, 0.0, 0.95, 0.05));

    // ── Цели ─────────────────────────────────────────────────────────
    public final EnumSetting<TargetMode> targetMode     = add(new EnumSetting<>("Targeting", TargetMode.DAMAGE));
    public final NumberSetting targetRange              = add(new NumberSetting("TargetRange", 10.0, 1.0, 15.0, 0.5));
    public final BooleanSetting targetMobs              = add(new BooleanSetting("TargetMobs", false));
    public final BooleanSetting targetCreative          = add(new BooleanSetting("TargetCreative", false));
    public final BooleanSetting antiFriend              = add(new BooleanSetting("AntiFriend", true));
    public final NumberSetting friendDamageLimit        = add(new NumberSetting("FriendDmg", 6.0, 0.0, 20.0, 0.5));

    // ── Атака кристаллов ─────────────────────────────────────────────
    public final NumberSetting breakDelay               = add(new NumberSetting("BreakDelay", 0.0, 0.0, 5.0, 1.0));
    public final NumberSetting attackRange              = add(new NumberSetting("AttackRange", 2.9, 2.0, 3.0, 0.1));

    // ── Постановка кристаллов ────────────────────────────────────────
    public final NumberSetting placeDelay               = add(new NumberSetting("PlaceDelay", 0.0, 0.0, 5.0, 1.0));
    public final NumberSetting placeReach               = add(new NumberSetting("Reach", 4.2, 1.0, 4.5, 0.1));
    public final BooleanSetting smartPlacement          = add(new BooleanSetting("SmartPlacement", true));
    public final NumberSetting smartCandidates          = add(new NumberSetting("SmartCandidates", 6.0, 1.0, 16.0, 1.0));

    // ── Урон ─────────────────────────────────────────────────────────
    public final NumberSetting minDamage                = add(new NumberSetting("MinDmg", 3.0, 0.0, 20.0, 0.5));
    public final NumberSetting facePlaceHp              = add(new NumberSetting("FacePlaceHp", 6.0, 0.0, 20.0, 0.5));
    public final NumberSetting maxSelfDamage            = add(new NumberSetting("MaxSelfDmg", 8.0, 0.0, 20.0, 0.5));
    public final NumberSetting damageRatio              = add(new NumberSetting("DamageRatio", 2.0, 1.0, 5.0, 0.1));
    public final BooleanSetting safety                  = add(new BooleanSetting("Safety", true));

    // ── Прочее ───────────────────────────────────────────────────────
    public final BooleanSetting autoSupport             = add(new BooleanSetting("AutoSupport", false));
    public final BooleanSetting predict                 = add(new BooleanSetting("Predict", false));
    public final BooleanSetting render                  = add(new BooleanSetting("Render", true));

    private Entity currentTarget;
    private int targetHoldTicks;
    private boolean aiming;

    /** Общий кулдаун действия: placeDelay — после постановки, breakDelay — после атаки. */
    private int actionTimer;

    /** Точка наведения последнего запланированного действия (для удержания ротации). */
    private Vec3d pendingAim;

    /** Зацепленная клетка постановки: постоянная смена точки сбивает плавную ротацию. */
    private BlockPos stickyCell;
    private int stickyTargetId = -1;
    private long stickyMs;

    /** Id кристаллов, атакованных только что: повторный удар до TTL запрещён. */
    private final Map<Integer, Long> attackedCrystals = new HashMap<>();

    /** Клетка, в которую AutoSupport ставит обсидиан. */
    private BlockPos pendingSupportCell;

    /** Кулдаун подтверждения установленной опоры. */
    private int supportWaitTicks;

    /** Боксы для визуализации текущего решения. */
    private Box attackBox;
    private Box placeBox;

    public CrystalAura() {
        super("CrystalAura", "Grim-safe End Crystal aura: automatic crystal placement and burst breaking.", Category.COMBAT);
    }

    @Override
    public void onEnable() {
        this.currentTarget = null;
        this.targetHoldTicks = 0;
        this.actionTimer = 0;
        this.pendingAim = null;
        this.stickyCell = null;
        this.stickyTargetId = -1;
        this.pendingSupportCell = null;
        this.supportWaitTicks = 0;
        this.attackBox = null;
        this.placeBox = null;
        this.attackedCrystals.clear();
    }

    @Override
    public void onDisable() {
        RotationUtils.getInstance().release(this);
        this.currentTarget = null;
        this.targetHoldTicks = 0;
        this.actionTimer = 0;
        this.pendingAim = null;
        this.stickyCell = null;
        this.stickyTargetId = -1;
        this.pendingSupportCell = null;
        this.supportWaitTicks = 0;
        this.attackBox = null;
        this.placeBox = null;
        this.attackedCrystals.clear();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        this.aiming = false;
        pruneAttacked();
        tickAura();

        if (!this.aiming) RotationUtils.getInstance().release(this);
    }

    private void tickAura() {
        if (this.actionTimer > 0) {
            this.actionTimer--;
            // Во время кулдауна продолжаем наводиться. Слот тика нужен
            // только когда есть куда наводиться: простаивающий кристалл
            // не съедает тики якоря.
            if (this.pendingAim != null) {
                // Заявку слота считаем «провод нужен»: отказ координации —
                // не повод сбрасывать собственное наведение.
                this.aiming = true;
                if (AuraCoordination.claim(this, mc.world.getTime())) {
                    applyAim(this.pendingAim);
                }
            }
            return;
        }

        refreshTarget();

        if (this.currentTarget == null) {
            cancelAction();
            return;
        }

        // Синхронизация с другими боевыми аурами (GrimAnchorAura): в тике
        // действует одна, иначе две одновременно перезаписывают серверный
        // угол и сбивают друг другу проверки прицела. Отказ слота наведение
        // не отменяет — доворот доживёт до своего следующего тика.
        this.aiming = true;
        if (!AuraCoordination.claim(this, mc.world.getTime())) return;

        // 1. Атака существующего кристалла — самый быстрый путь урона.
        //    Не проваливаемся к постановке в тот же тик: прицелы разные,
        //    каждая смена точки обнуляет ход ротации.
        EndCrystalEntity crystal = bestAttackableCrystal();
        this.placeBox = crystal != null ? null : this.placeBox;
        if (crystal != null) {
            this.attackBox = crystal.getBoundingBox();
            handleAttack(crystal);
            return;
        }

        // 2. Постановка нового кристалла (в т.ч. опоры под него).
        this.attackBox = null;
        handlePlacement();
    }

    private void cancelAction() {
        this.pendingAim = null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Атака: выбор и исполнение
    // ─────────────────────────────────────────────────────────────────

    private EndCrystalEntity bestAttackableCrystal() {
        double reachSq = this.attackRange.getValue() * this.attackRange.getValue();
        LivingEntity target = livingTarget();

        EndCrystalEntity best = null;
        float bestValue = Float.NEGATIVE_INFINITY;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive() || crystal.isRemoved()) continue;
            if (isRecentlyAttacked(crystal.getId())) continue;

            Box box = crystal.getBoundingBox();
            double dist = GrimUtils.closestEyeDistanceSqTo(mc.player, box);
            if (dist > reachSq) continue;

            Vec3d center = crystalCenter(crystal);
            float targetDmg = target != null
                    ? ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, target)
                    : 0.0f;

            if (target != null && !damageAcceptable(center, target, targetDmg)) continue;

            if (targetDmg > bestValue || (targetDmg == bestValue && dist < bestDist)) {
                bestValue = targetDmg;
                bestDist = dist;
                best = crystal;
            }
        }

        return best;
    }

    private void handleAttack(EndCrystalEntity crystal) {
        // Наведение заказывается ДО проверок луча: без этого отправленный
        // угол никогда не дойдёт до кристалла и атаа не выстрелит.
        // Держим прицел на центре хитбокса кристалла даже пока луч не дошёл.
        this.pendingAim = crystal.getBoundingBox().getCenter();
        applyAim(this.pendingAim);

        // Удар уходит только когда луч по отправленному углу уже пересекает
        // расширенный хитбокс кристалла; дубль наведения выше гонит доворот.
        Box hitBox = crystal.getBoundingBox().expand(ENTITY_BOX_EXPAND);
        if (!aimAtEntityBox(hitBox)) {
            return;
        }

        if (SwapManager.getInstance().isActionConflictTick()) return;

        RotationUtils rot = RotationUtils.getInstance();
        rot.flushServerRotation();
        rot.keepWireFresh();

        mc.getNetworkHandler().sendPacket(
                PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        this.attackedCrystals.put(crystal.getId(), System.currentTimeMillis());
        this.actionTimer = this.breakDelay.getValue().intValue();
        cancelAction();
    }

    private boolean isRecentlyAttacked(int id) {
        Long ts = this.attackedCrystals.get(id);
        return ts != null && System.currentTimeMillis() - ts < ATTACKED_TTL_MS;
    }

    private void pruneAttacked() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Long>> it = this.attackedCrystals.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= ATTACKED_TTL_MS) it.remove();
        }
    }

    /** Центр взрыва кристалла — позиция сущности (проверено по байткоду). */
    private static Vec3d crystalCenter(EndCrystalEntity crystal) {
        return new Vec3d(crystal.getX(), crystal.getY(), crystal.getZ());
    }

    // ─────────────────────────────────────────────────────────────────
    // Постановка: выбор клетки и исполнение
    // ─────────────────────────────────────────────────────────────────

    private void handlePlacement() {
        if (this.pendingSupportCell != null) {
            handleSupportBuild();
            return;
        }

        BlockPos cell = bestPlacementCell();
        if (cell == null) {
            cancelAction();
            return;
        }

        BlockState supportState = mc.world.getBlockState(cell.down());
        if (!isCrystalSupport(supportState)) {
            if (this.autoSupport.getValue()) {
                this.pendingSupportCell = cell.down();
                this.supportWaitTicks = 0;
                handleSupportBuild();
            } else {
                cancelAction();
            }
            return;
        }

        aimPlacement(cell);
    }

    private void handleSupportBuild() {
        BlockPos supportCell = this.pendingSupportCell;

        // Ожидание подтверждения: обсидиан встал — дальше кристалл.
        BlockState state = mc.world.getBlockState(supportCell);
        if (state.isOf(Blocks.OBSIDIAN)) {
            this.pendingSupportCell = null;
            this.supportWaitTicks = 0;
            return;
        }
        if (!state.isReplaceable()) {
            this.pendingSupportCell = null;
            cancelAction();
            return;
        }

        if (this.supportWaitTicks > 0) {
            this.supportWaitTicks--;
            return;
        }

        FindItemResult obsidian = InventoryUtil.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) {
            this.pendingSupportCell = null;
            cancelAction();
            return;
        }

        // Обсидиан ставим кликом по верхней грани блока под опорной клеткой.
        BlockPos under = supportCell.down();
        BlockState underState = mc.world.getBlockState(under);
        if (underState.isReplaceable() || !underState.isSolidBlock(mc.world, under)) {
            this.pendingSupportCell = null;
            cancelAction();
            return;
        }

        BlockHitResult hit = findFaceHit(under, Direction.UP, this.placeReach.getValue());
        if (hit == null) {
            // Вверх не видно — пробуем боковые грани опорного блока-соседа.
            hit = findSupportSideHit(supportCell);
            if (hit == null) {
                cancelAction();
                return;
            }
        }

        this.pendingAim = hit.getPos();
        applyAim(hit.getPos());

        if (!isAimedViaSentRot(hit, this.placeReach.getValue())) return;
        if (SwapManager.getInstance().isActionConflictTick()) return;

        if (!SwapManager.getInstance().hold(this, obsidian.slot(), SwapManager.Priority.PLACE, 2)) return;

        BlockHitResult finalHit = randomizeCursor(hit);
        int seq = GrimUtils.nextSequence();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, finalHit, seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (this.predict.getValue() && isFaceFacingEye(mc.player.getEyePos(), under, Direction.UP)) {
            mc.world.setBlockState(supportCell, Blocks.OBSIDIAN.getDefaultState());
        }

        this.supportWaitTicks = SUPPORT_CONFIRM_TICKS;
        cancelAction();
    }

    /**
     * Ставка обсидиана боковой гранью: сосед опорной клетки, чья грань
     * граничит с опорной клеткой и видна с глаз.
     */
    private BlockHitResult findSupportSideHit(BlockPos supportCell) {
        double reach = Math.min(this.placeReach.getValue(), SERVER_BLOCK_REACH);
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = supportCell.offset(dir);
            BlockState neighborState = mc.world.getBlockState(neighbor);
            if (neighborState.isReplaceable() || !neighborState.isSolidBlock(mc.world, neighbor)) continue;

            BlockHitResult hit = findFaceHit(neighbor, dir.getOpposite(), reach);
            if (hit != null) return hit;
        }
        return null;
    }

    private BlockPos bestPlacementCell() {
        LivingEntity target = livingTarget();
        if (target == null) return null;

        double reach = Math.min(this.placeReach.getValue(), SERVER_BLOCK_REACH);
        double reachSq = reach * reach;

        BlockPos origin = mc.player.getBlockPos();
        int r = (int) Math.ceil(reach);

        List<BlockPos> valid = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos cell = origin.add(dx, dy, dz);
                    if (!mc.world.getBlockState(cell).isAir()) continue;

                    BlockState supportState = mc.world.getBlockState(cell.down());
                    boolean supportReady = isCrystalSupport(supportState);
                    if (!supportReady) {
                        if (!this.autoSupport.getValue()) continue;
                        // AutoSupport: обсидиан поставим позже, клетка опоры — заменяемая.
                        if (!supportState.isReplaceable()) continue;
                    }

                    // Симуляция серверного EndCrystalItem: бокс 1×2×1 пуст от сущностей.
                    Box space = new Box(
                            cell.getX(), cell.getY(), cell.getZ(),
                            cell.getX() + 1.0, cell.getY() + 2.0, cell.getZ() + 1.0);
                    if (!mc.world.getOtherEntities(null, space).isEmpty()) continue;

                    Vec3d center = bottomCenter(cell);
                    if (GrimUtils.closestEyeDistanceSqTo(mc.player, center) > reachSq) continue;

                    valid.add(cell);
                }
            }
        }

        if (valid.isEmpty()) return null;

        float maxSelf = this.maxSelfDamage.getValue().floatValue();
        float minDmg  = effectiveMinDamage(target);
        double ratio  = this.damageRatio.getValue();

        // Дешёвая верхняя граница урона для сортировки перед точным расчётом.
        List<BlockPos> pool = valid;
        if (this.smartPlacement.getValue() && pool.size() > this.smartCandidates.getValue().intValue()) {
            pool = new ArrayList<>(valid);
            pool.sort(Comparator.comparingDouble((BlockPos p) -> -(
                    ExplosionDamageUtil.getDamageUpperBound(bottomCenter(p), ExplosionDamageUtil.CRYSTAL_POWER, target)
                    - 0.001 * ExplosionDamageUtil.getDamageUpperBound(bottomCenter(p), ExplosionDamageUtil.CRYSTAL_POWER, mc.player)
            )));
            pool = pool.subList(0, Math.max(1, this.smartCandidates.getValue().intValue()));
        }

        BlockPos best = null;
        float bestScore = Float.NEGATIVE_INFINITY;

        for (BlockPos p : pool) {
            Vec3d center = bottomCenter(p);
            float targetDmg = ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, target);
            if (targetDmg < minDmg) continue;
            if (this.antiFriend.getValue() && isFriendDamaged(center)) continue;

            float selfDmg = ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, mc.player);
            if (selfDmg > maxSelf) continue;
            if (selfDmg > 0.5f && targetDmg / selfDmg < ratio) continue;

            if (this.smartPlacement.getValue()) {
                // Жёсткий сейф: позиция, на которой мы переживаем взрыв.
                if (this.safety.getValue() && !selfDamageSurvivable(selfDmg)) continue;
                float score = targetDmg * 2.0f - selfDmg;
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            } else {
                if (targetDmg > bestScore) {
                    bestScore = targetDmg;
                    best = p;
                }
            }
        }

        return best == null ? null : keepSticky(best, bestScore, pool, target);
    }

    private BlockPos keepSticky(BlockPos best, float bestScore, List<BlockPos> pool, LivingEntity target) {
        long now = System.currentTimeMillis();
        BlockPos pinned = this.stickyCell;

        if (this.stickyTargetId != this.currentTarget.getId() || now - this.stickyMs > STICKY_TTL_MS) {
            this.stickyCell = null;
            pinned = null;
        }
        this.stickyTargetId = this.currentTarget.getId();
        this.stickyMs = now;

        if (pinned != null && (!pool.contains(pinned) || !mc.world.getBlockState(pinned).isAir())) {
            this.stickyCell = null;
            pinned = null;
        }

        if (pinned != null) {
            Vec3d center = bottomCenter(pinned);
            float score = ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, target) * 2.0f
                    - ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, mc.player);
            if (score >= bestScore * STICKY_KEEP_RATIO) return pinned;
        }

        this.stickyCell = best;
        return best;
    }

    private void aimPlacement(BlockPos cell) {
        BlockPos support = cell.down();
        BlockState supportState = mc.world.getBlockState(support);

        BlockHitResult hit = null;
        if (isCrystalSupport(supportState)) {
            for (Direction face : facePreferenceOrder(support)) {
                hit = findFaceHit(support, face, this.placeReach.getValue());
                if (hit != null) break;
            }
        } else {
            hit = findSupportSideHit(support);
        }
        if (hit == null) {
            cancelAction();
            return;
        }

        this.pendingAim = hit.getPos();
        applyAim(hit.getPos());
        this.placeBox = new Box(
                cell.getX(), cell.getY(), cell.getZ(),
                cell.getX() + 1.0, cell.getY() + 2.0, cell.getZ() + 1.0);

        if (!isAimedViaSentRot(hit, this.placeReach.getValue())) return;
        if (SwapManager.getInstance().isActionConflictTick()) return;

        FindItemResult crystals = InventoryUtil.findInHotbar(Items.END_CRYSTAL);
        if (!crystals.found()) {
            cancelAction();
            return;
        }
        if (!SwapManager.getInstance().hold(this, crystals.slot(), SwapManager.Priority.CRYSTAL, 2)) return;

        BlockHitResult finalHit = randomizeCursor(hit);
        int seq = GrimUtils.nextSequence();
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, finalHit, seq));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        this.actionTimer = this.placeDelay.getValue().intValue();
        cancelAction();
    }

    private boolean isCrystalSupport(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    /**
     * Поиск кликабельного хита по грани блока. COLLIDER — как в
     * GrimAnchorAura для физической поддержки placement-лучей.
     */
    private BlockHitResult findFaceHit(BlockPos pos, Direction face, double reach) {
        Vec3d eye = mc.player.getEyePos();

        if (!isFaceFacingEye(eye, pos, face)) return null;

        Vec3d sample = faceCenter(pos, face);
        Vec3d dir = sample.subtract(eye);
        if (dir.lengthSquared() < 1.0E-6) return null;

        Vec3d end = eye.add(dir.normalize().multiply(reach + 0.1));

        BlockHitResult clip = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

        if (clip.getType() != HitResult.Type.BLOCK) return null;
        if (!clip.getBlockPos().equals(pos) || clip.getSide() != face) return null;
        return eye.squaredDistanceTo(clip.getPos()) > reach * reach ? null : clip;
    }

    // ─────────────────────────────────────────────────────────────────
    // Ротация и проверки прицела
    // ─────────────────────────────────────────────────────────────────

    private void applyAim(Vec3d targetPos) {
        this.aiming = true;

        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), targetPos);
        double maxSpeed = this.turnSpeed.getValue();
        double smooth = this.smoothness.getValue();
        RotationMode mode = this.rotationMode.getValue();

        if (mode == RotationMode.CLIENT || mode == RotationMode.BOTH) {
            float curYaw = mc.player.getYaw();
            float curPitch = mc.player.getPitch();

            float dy = MathHelper.wrapDegrees(rot[0] - curYaw);
            float dp = rot[1] - curPitch;
            double totalDist = Math.hypot(dy, dp);

            if (maxSpeed >= 180.0 && smooth <= 0.0) {
                mc.player.setYaw(curYaw + dy);
                mc.player.setPitch(MathHelper.clamp(rot[1], -90.0f, 90.0f));
            } else if (totalDist > 0.0001) {
                double decelRange = Math.max(16.0, maxSpeed * 0.40);
                double easePow = 0.50 + smooth * 0.30;
                double speedFactor = totalDist < decelRange ? Math.pow(totalDist / decelRange, easePow) : 1.0;
                double desiredSpeed = Math.max(maxSpeed * speedFactor, 3.0);
                double step = Math.min(totalDist, Math.min(desiredSpeed, maxSpeed));

                double stepY = dy / totalDist * step;
                double stepP = dp / totalDist * step;

                // GCD-квантование под мышачий ввод.
                double sens = mc.options.getMouseSensitivity().getValue();
                double f = sens * 0.6 + 0.2;
                double gcd = f * f * f * 8.0 * 0.15;
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
                rotUtils.setRotationFullInstant(this, ROTATION_PRIORITY, mc.player.getYaw(), mc.player.getPitch());
            } else {
                rotUtils.setRotationSmooth(this, ROTATION_PRIORITY, rot[0], rot[1], maxSpeed, this.syncMovement.getValue());
            }
            rotUtils.keepWireFresh();
        } else {
            RotationUtils.getInstance().release(this);
        }
    }

    /** Проверка прицела по отправленному углу: луч обязан попасть в блок и грань. */
    private boolean isAimedViaSentRot(BlockHitResult expected, double reach) {
        if (mc.player == null || mc.world == null || expected == null) return false;

        RotationUtils rotUtils = RotationUtils.getInstance();
        Vec3d eye = mc.player.getEyePos();

        float yaw, pitch;
        if (this.rotationMode.getValue() == RotationMode.CLIENT) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        } else {
            yaw = rotUtils.getSentYaw();
            pitch = rotUtils.getSentPitch();
        }

        Vec3d look = RotationUtils.getRotationVector(pitch, yaw);
        Vec3d end = eye.add(look.multiply(reach + 0.1));

        // GrimAC валидирует интеракции через OUTLINE-форму блока.
        BlockHitResult clip = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));

        if (clip == null || clip.getType() != HitResult.Type.BLOCK) return false;
        if (!clip.getBlockPos().equals(expected.getBlockPos())) return false;
        if (clip.getSide() != expected.getSide()) return false;
        return eye.squaredDistanceTo(clip.getPos()) <= reach * reach;
    }

    /** Проверка, что по отправленному углу луч пересекает хитбокс кристалла. */
    private boolean aimAtEntityBox(Box hitBox) {
        double reach = this.attackRange.getValue();
        Vec3d eye = mc.player.getEyePos();

        if (GrimUtils.closestEyeDistanceSqTo(mc.player, hitBox) > reach * reach) return false;

        float yaw, pitch;
        if (this.rotationMode.getValue() == RotationMode.CLIENT) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        } else {
            RotationUtils rotUtils = RotationUtils.getInstance();
            yaw = rotUtils.getSentYaw();
            pitch = rotUtils.getSentPitch();
        }

        Vec3d look = RotationUtils.getRotationVector(pitch, yaw);
        Vec3d end = eye.add(look.multiply(reach + 0.2));
        return hitBox.raycast(eye, end).isPresent();
    }

    // ─────────────────────────────────────────────────────────────────
    // Урон и безопасность
    // ─────────────────────────────────────────────────────────────────

    /** Гейт урона: минДмг / соотношение / фейсплейс / самоподрыв / друзья. */
    private boolean damageAcceptable(Vec3d center, LivingEntity target, float targetDmg) {
        float selfDmg = ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, mc.player);

        boolean facePlace = isFacePlace(target);
        if (!facePlace) {
            if (targetDmg < this.minDamage.getValue().floatValue()) return false;
            if (selfDmg > 0.5f && targetDmg / selfDmg < this.damageRatio.getValue()) return false;
        }

        if (selfDmg > this.maxSelfDamage.getValue().floatValue()) return false;
        if (this.antiFriend.getValue() && isFriendDamaged(center)) return false;
        if (this.safety.getValue() && !selfDamageSurvivable(selfDmg)) return false;

        return true;
    }

    private boolean isFacePlace(LivingEntity target) {
        return this.facePlaceHp.getValue() > 0.0
                && (target.getHealth() + target.getAbsorptionAmount()) <= this.facePlaceHp.getValue().floatValue();
    }

    private float effectiveMinDamage(LivingEntity target) {
        return isFacePlace(target) ? 0.0f : this.minDamage.getValue().floatValue();
    }

    private boolean isFriendDamaged(Vec3d center) {
        float limit = this.friendDamageLimit.getValue().floatValue();
        if (limit <= 0.0f) return false;

        for (String name : FriendManager.INSTANCE.getFriends()) {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (!player.getName().getString().equals(name)) continue;
                if (player == mc.player || !player.isAlive() || player.isDead()) continue;

                float upper = ExplosionDamageUtil.getDamageUpperBound(center, ExplosionDamageUtil.CRYSTAL_POWER, player);
                if (upper <= limit) continue;

                float dmg = ExplosionDamageUtil.getDamage(mc.world, center, ExplosionDamageUtil.CRYSTAL_POWER, player);
                if (dmg > limit) return true;
            }
        }
        return false;
    }

    /** Тотем в руках или здоровья хватит пережить самоурон. */
    private boolean selfDamageSurvivable(float selfDmg) {
        if (selfDmg <= 6.0f) return true;

        boolean hasTotem = mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)
                || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        if (hasTotem) return true;

        return (mc.player.getHealth() + mc.player.getAbsorptionAmount()) > selfDmg;
    }

    // ─────────────────────────────────────────────────────────────────
    // Цели
    // ─────────────────────────────────────────────────────────────────

    private void refreshTarget() {
        double rangeSq = this.targetRange.getValue() * this.targetRange.getValue();

        Entity best = null;
        double bestValue = Double.MAX_VALUE;      // для DISTANCE/HEALTH — меньше, тем лучше
        float bestDamage = Float.NEGATIVE_INFINITY;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.isSpectator() || player.isDead() || !player.isAlive()) continue;
            if (!this.targetCreative.getValue() && player.isCreative()) continue;
            if (this.antiFriend.getValue() && FriendManager.INSTANCE.isFriend(player)) continue;

            double dist = mc.player.squaredDistanceTo(player);
            if (dist > rangeSq) continue;

            switch (this.targetMode.getValue()) {
                case DISTANCE -> {
                    if (dist < bestValue) {
                        bestValue = dist;
                        best = player;
                    }
                }
                case HEALTH -> {
                    double hp = player.getHealth() + player.getAbsorptionAmount();
                    if (hp < bestValue) {
                        bestValue = hp;
                        best = player;
                    }
                }
                case DAMAGE -> {
                    float upper = ExplosionDamageUtil.getDamageUpperBound(
                            mc.player.getEntityPos().add(0.0, 1.0, 0.0), ExplosionDamageUtil.CRYSTAL_POWER, player);
                    if (upper > bestDamage) {
                        bestDamage = upper;
                        best = player;
                    }
                }
            }
        }

        if (best == null && this.targetMobs.getValue()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof LivingEntity living)
                        || living instanceof PlayerEntity
                        || !living.isAlive()
                        || living.isDead()) continue;

                double dist = mc.player.squaredDistanceTo(living);
                if (dist > rangeSq) continue;

                if (this.targetMode.getValue() == TargetMode.HEALTH) {
                    double hp = living.getHealth() + living.getAbsorptionAmount();
                    if (hp < bestValue) {
                        bestValue = hp;
                        best = living;
                    }
                } else {
                    if (dist < bestValue) {
                        bestValue = dist;
                        best = living;
                    }
                }
            }
        }

        // Зацеп цели: мигание выбором между равными кандидатами сбивает ротацию.
        if (best != null && this.currentTarget != null
                && best.getId() != this.currentTarget.getId()
                && this.targetHoldTicks > 0
                && this.currentTarget.isAlive()
                && mc.player.squaredDistanceTo(this.currentTarget) <= rangeSq) {
            this.targetHoldTicks--;
            best = this.currentTarget;
        }

        if (this.currentTarget == null || best == null || best.getId() != this.currentTarget.getId()) {
            this.targetHoldTicks = TARGET_HOLD_TICKS;
            this.stickyCell = null;
            this.pendingSupportCell = null;
        }
        this.currentTarget = best;
    }

    private LivingEntity livingTarget() {
        return this.currentTarget instanceof LivingEntity living && living.isAlive() && !living.isDead()
                ? living : null;
    }

    // ─────────────────────────────────────────────────────────────────
    // Геометрия и пакеты
    // ─────────────────────────────────────────────────────────────────

    private boolean isFaceFacingEye(Vec3d eye, BlockPos pos, Direction face) {
        return switch (face) {
            case DOWN -> eye.y < pos.getY();
            case UP -> eye.y > pos.getY() + 1.0;
            case NORTH -> eye.z < pos.getZ();
            case SOUTH -> eye.z > pos.getZ() + 1.0;
            case WEST -> eye.x < pos.getX();
            case EAST -> eye.x > pos.getX() + 1.0;
        };
    }

    /** Центр грани блока — фиксированная точка прицеливания. */
    private Vec3d faceCenter(BlockPos pos, Direction face) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        return switch (face) {
            case DOWN -> new Vec3d(x + 0.5, y, z + 0.5);
            case UP -> new Vec3d(x + 0.5, y + 1.0, z + 0.5);
            case NORTH -> new Vec3d(x + 0.5, y + 0.5, z);
            case SOUTH -> new Vec3d(x + 0.5, y + 0.5, z + 1.0);
            case WEST -> new Vec3d(x, y + 0.5, z + 0.5);
            case EAST -> new Vec3d(x + 1.0, y + 0.5, z + 0.5);
        };
    }

    /**
     * Порядок предпочтения граней опоры: верхняя, потому что её центр —
     * это нижний центр кристалла: честный луч сразу и для установки,
     * и для атаки. Сбоку — когда верхняя не видна. Снизу — из-под опоры.
     */
    private Direction[] facePreferenceOrder(BlockPos support) {
        Vec3d eye = mc.player.getEyePos();
        Direction[] topFirst = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.DOWN};
        Direction[] bottomFirst = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};

        return eye.y > support.getY() + 1.0
                ? topFirst
                : eye.y < support.getY() ? bottomFirst : topFirst;
    }

    /** Точка, из которой сервер считает урон взрыва кристалла (низ клетки). */
    private static Vec3d bottomCenter(BlockPos cell) {
        return new Vec3d(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
    }

    /**
     * GrimAC фиксирует курсор взаимодействия в центре грани (0.5, 0.5).
     * Рандомизируем в пределах ±0.30 — как живой игрок.
     */
    private BlockHitResult randomizeCursor(BlockHitResult original) {
        BlockPos blockPos = original.getBlockPos();
        Direction face = original.getSide();

        double bx = blockPos.getX();
        double by = blockPos.getY();
        double bz = blockPos.getZ();

        double rx, ry, rz;

        switch (face) {
            case UP -> {
                rx = bx + 0.5 + jitter();
                ry = by + 1.0;
                rz = bz + 0.5 + jitter();
            }
            case DOWN -> {
                rx = bx + 0.5 + jitter();
                ry = by;
                rz = bz + 0.5 + jitter();
            }
            case NORTH -> {
                rx = bx + 0.5 + jitter();
                ry = by + 0.5 + jitter();
                rz = bz;
            }
            case SOUTH -> {
                rx = bx + 0.5 + jitter();
                ry = by + 0.5 + jitter();
                rz = bz + 1.0;
            }
            case WEST -> {
                rx = bx;
                ry = by + 0.5 + jitter();
                rz = bz + 0.5 + jitter();
            }
            case EAST -> {
                rx = bx + 1.0;
                ry = by + 0.5 + jitter();
                rz = bz + 0.5 + jitter();
            }
            default -> {
                return original;
            }
        }

        return new BlockHitResult(new Vec3d(rx, ry, rz), face, blockPos, original.isInsideBlock());
    }

    private static double jitter() {
        return (RANDOM.nextDouble() - 0.5) * 2.0 * CURSOR_JITTER;
    }

    // ─────────────────────────────────────────────────────────────────
    // Визуал
    // ─────────────────────────────────────────────────────────────────

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!isToggled() || !this.render.getValue()) return;

        if (this.attackBox != null) {
            Renderer3D.box(this.attackBox.minX, this.attackBox.minY, this.attackBox.minZ,
                    this.attackBox.maxX, this.attackBox.maxY, this.attackBox.maxZ,
                    ATTACK_FILL, ATTACK_LINE, ShapeMode.Both);
        }
        if (this.placeBox != null) {
            Renderer3D.box(this.placeBox.minX, this.placeBox.minY, this.placeBox.minZ,
                    this.placeBox.maxX, this.placeBox.maxY, this.placeBox.maxZ,
                    PLACE_FILL, PLACE_LINE, ShapeMode.Both);
        }
    }
}
