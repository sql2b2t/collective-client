package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.*;
import me.collective.util.AuraCoordination;
import me.collective.util.RotationUtils;
import me.collective.util.SwapManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Melee-аура со стратегией «ноль флагов»: одна плавная ротация через общий
 * RotationUtils-провод, удар уходит только когда серверный угол уже доведён
 * до цели и луч гарантированно попадает в реальный хитбокс. Участник единой
 * очереди боевых аур ({@link AuraCoordination}) — не спорит с CrystalAura
 * и GrimAnchorAura за серверный угол.
 */
public class KillAura extends Module {
    public static KillAura INSTANCE;

    /** Текущая цель. Имя поле публично: используется TargetESP. */
    public static LivingEntity textureRun;

    // ── Атака ──
    public static NumberSetting attackRange = new NumberSetting("Радиус атаки", 3.0F, 3.0F, 6.0F, 0.1F);
    public static NumberSetting acquireRange = new NumberSetting("Радиус обнаружения", 1.0F, 0.0F, 5.0F, 0.1F);
    public static NumberSetting turnSpeed = new NumberSetting("Скорость ротации", 65.0F, 20.0F, 180.0F, 5.0F);
    public static BooleanSetting smartCrits = new BooleanSetting("Умные криты", false);
    public static BooleanSetting nearestTarget = new BooleanSetting("Приоритет ближайшей цели", false);
    public static BooleanSetting followTarget = new BooleanSetting("Следовать за целью", false);
    public static BooleanSetting sprintReset = new BooleanSetting("Сброс спринта", true);

    // ── Цели ──
    public static ChoiceSetting providerClose = new ChoiceSetting(
        "Цели",
        new BooleanSetting("Игроки", true),
        new BooleanSetting("Голые", true),
        new BooleanSetting("Невидимки", true),
        new BooleanSetting("Голые невидимки", false),
        new BooleanSetting("Друзья", false),
        new BooleanSetting("NPC", true),
        new BooleanSetting("Мобы", false),
        new BooleanSetting("Животные", false),
        new BooleanSetting("Жители", false)
    );

    // ── Проверки перед ударом ──
    public static ChoiceSetting colorMeasure = new ChoiceSetting(
        "Проверки",
        new BooleanSetting("Бить через блоки", false),
        new BooleanSetting("Бить только оружием", false),
        new BooleanSetting("Не бить если кушаешь", true),
        new BooleanSetting("Не бить в контейнерах", false),
        new BooleanSetting("Ломать щит", false),
        new BooleanSetting("Отжим щита", false),
        new BooleanSetting("Проверка на луч", true)
    );

    // ── Булава ──
    public static BooleanSetting profileInvoke = new BooleanSetting("Булава", false);
    public static NumberSetting scaleSave = new NumberSetting("Высота булавы", 2.0F, 0.5F, 6.0F, 0.1F);
    public static BooleanSetting autoWeapon = new BooleanSetting("Авто-оружие", true);
    public static NumberSetting cooldownGate = new NumberSetting("Кулдаун удара", 0.92F, 0.80F, 1.0F, 0.02F);

    public static BooleanSetting debugOutput = new BooleanSetting("Отладка", false);

    /**
     * Допустимое расхождение серверного угла с углом на цель перед ударом.
     * Удар уходит с углом, который сервер уже получал, — скачков не видно.
     */
    private static final float AIM_EPSILON = 2.5f;

    /** Приоритет захвата ротации — равный остальным боевым аурам. */
    private static final int ROTATION_PRIORITY = 40;

    private static final long DEBUG_COOLDOWN_MS = 2000L;
    private long lastDebugAt;

    private float targetYaw;
    private float targetPitch;
    private boolean aiming;

    public KillAura() {
        super("AttackAura", "Автоматически бьет энтити - таргетов", Category.COMBAT);
        INSTANCE = this;

        add(attackRange);
        add(acquireRange);
        add(turnSpeed);
        add(smartCrits);
        add(providerClose);
        add(colorMeasure);
        add(sprintReset);
        add(followTarget);
        add(nearestTarget);
        add(autoWeapon);
        add(cooldownGate);
        add(profileInvoke);
        add(scaleSave);
        add(debugOutput);
    }

    @Override
    public void onEnable() {
        textureRun = null;
    }

    @Override
    public void onDisable() {
        textureRun = null;
        RotationUtils.getInstance().release(this);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        aiming = false;
        tickOnce();

        // Тик без наведения — серверный угол больше не нужен.
        if (!aiming) RotationUtils.getInstance().release(this);
    }

    private void tickOnce() {
        if (textureRun == null || !isValidTarget(textureRun)) {
            findTarget();
        }

        if (textureRun == null) return;

        if (followTarget.getValue()) {
            Vec3d diff = textureRun.getEntityPos().subtract(mc.player.getEntityPos());
            if (diff.lengthSquared() > 1.0) {
                float targetAngle = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
                mc.player.setYaw(MathHelper.lerpAngleDegrees(0.2f, mc.player.getYaw(), targetAngle));
            }
        }

        // Единая очередь боевых аур: в тике наводится только одна аура.
        // Заявка считается «провод нужен»: отказ координации — не повод
        // сбрасывать собственное наведение (release погубил бы доворот).
        aiming = true;
        if (!AuraCoordination.claim(this, mc.world.getTime())) return;

        // Наведение. Без захвата провода не бьём: удар ушёл бы с чужим
        // серверным углом — мгновенный флаг проверки «взгляд — удар».
        if (!aimAtTarget()) return;

        // Удар только когда сервер уже видит доведённый угол.
        if (!isAimReady()) return;

        if (shouldSkipAttack()) {
            report("пропуск по предварительным проверкам");
            return;
        }

        // Reach: угол уходит с реального расстояния между хитбоксами.
        // Сервер считает удары по настоящим боксам, поэтому и мы по ним.
        if (boxDistance(realBox(mc.player), realBox(textureRun)) > getAttackDistance(textureRun)) {
            report("цель дальше радиуса атаки");
            return;
        }

        if (isRayBlocked()) {
            report("луч не попал в цель");
            return;
        }

        if (!isAttackReady()) return;
        if (this.smartCrits.getValue() && !canCrit()) return;

        performAttack();
    }

    private void findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F).normalize();

        // Ближайший отклонённый кандидат — чтобы отладка объясняла,
        // почему цели нет, а не просто констатировала факт.
        String nearestReject = null;
        double nearestRejectDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity living)) continue;

            String reject = rejectReason(living);
            if (reject != null) {
                double d = boxDistance(mc.player, living);
                if (d < nearestRejectDist) {
                    nearestRejectDist = d;
                    nearestReject = reject;
                }
                continue;
            }

            double score;
            if (nearestTarget.getValue()) {
                score = boxDistance(mc.player, living);
            } else {
                Vec3d targetCenter = living.getBoundingBox().getCenter();
                Vec3d toTarget = targetCenter.subtract(eyePos).normalize();
                score = Math.acos(MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0));
            }

            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        textureRun = best;

        if (best == null && nearestReject != null) {
            report("цели нет: ближайшая отклонена — " + nearestReject);
        }
    }

    /**
     * Минимальное расстояние между хитбоксами.
     *
     * <p>Расстояние между позициями (нога-в-ноги) при росте игрока 1.8
     * превращало удар «с 3 блоков» в 3.5–3.8 по хитбоксу: окно обнаружения
     * съезжало внутрь, а дальность удара завышалась. Сервер считает удары
     * между хитбоксами — как здесь.
     */
    private static double boxDistance(Entity a, Entity b) {
        return boxDistance(a.getBoundingBox(), b.getBoundingBox());
    }

    private static double boxDistance(net.minecraft.util.math.Box ba, net.minecraft.util.math.Box bb) {
        double dx = Math.max(0.0, Math.max(ba.minX - bb.maxX, bb.minX - ba.maxX));
        double dy = Math.max(0.0, Math.max(ba.minY - bb.maxY, bb.minY - ba.maxY));
        double dz = Math.max(0.0, Math.max(ba.minZ - bb.maxZ, bb.minZ - ba.maxZ));

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Реальный хитбокс сущности.
     *
     * <p>Модуль HitBox через {@code MixinEntityHitbox} расширяет
     * {@code Entity.getBoundingBox()}, но сервер считает удары по настоящему
     * боксу — поэтому все боевые проверки (луч, дистанция) берут его отсюда.
     */
    private static net.minecraft.util.math.Box realBox(Entity entity) {
        return entity.getDimensions(entity.getPose()).getBoxAt(entity.getEntityPos());
    }

    /** Почему сущность не годится в цель. {@code null} — годится. */
    private String rejectReason(LivingEntity entity) {
        if (entity == null || entity == mc.player || entity instanceof ClientPlayerEntity) return "это мы сами";
        if (!entity.isAlive()) return "мёртв";
        if (entity.isInvulnerable()) return "неуязвим";
        if (entity instanceof ArmorStandEntity) return "арморстенд";

        float maxDist = getAttackDistance(entity) + acquireRange.compute() + hitboxBonus();
        double dist = boxDistance(mc.player, entity);
        if (dist > maxDist) {
            return String.format("%s в %.1f блоках, окно %.1f (атака %.1f + обнаружение %.1f)",
                    entity.getName().getString(), dist, maxDist,
                    getAttackDistance(entity), acquireRange.compute());
        }

        if (!colorMeasure.process("Бить через блоки") && !mc.player.canSee(entity)) {
            return entity.getName().getString() + " не виден (нет видимости)";
        }
        if (!providerClose.process("NPC") && isNpc(entity)) {
            return entity.getName().getString() + " NPC";
        }

        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return entity.getName().getString() + " креатив";
            if (player.isSpectator()) return entity.getName().getString() + " спектатор";

            boolean isFriend = FriendManager.getInstance().isFriend(player);
            if (isFriend && !providerClose.process("Друзья")) return entity.getName().getString() + " друг";
            if (!isFriend && !providerClose.process("Игроки")) return entity.getName().getString() + " не игрок";

            boolean naked = isNaked(player);
            boolean invis = player.isInvisible();

            if (invis) {
                return naked
                        ? (providerClose.process("Голые невидимки") ? null : "голая невидимка отключена")
                        : (providerClose.process("Невидимки") ? null : "невидимка отключена");
            }
            return (!naked || providerClose.process("Голые")) ? null : "фильтр «Голые»";
        }

        boolean isMob     = entity instanceof Monster || entity instanceof SlimeEntity;
        boolean isVillager = entity instanceof VillagerEntity || entity instanceof MerchantEntity;
        boolean isAnimal   = entity instanceof AnimalEntity || entity instanceof WaterCreatureEntity || entity instanceof AmbientEntity;

        if (isMob)     return providerClose.process("Мобы")     ? null : "мобы отключены";
        if (isVillager) return providerClose.process("Жители") ? null : "жители отключены";
        if (isAnimal)  return providerClose.process("Животные") ? null : "животные отключены";
        return "нецелевой тип сущности";
    }

    private boolean isValidTarget(LivingEntity entity) {
        return rejectReason(entity) == null;
    }

    private boolean isNaked(PlayerEntity player) {
        return player.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
            && player.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
            && player.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
            && player.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    private boolean isNpc(LivingEntity entity) {
        String name = cleanName(entity.getName().getString());
        String display = cleanName(entity.getDisplayName().getString());
        String custom = entity.getCustomName() == null ? "" : cleanName(entity.getCustomName().getString());

        if (containsNpcTag(name) || containsNpcTag(display) || containsNpcTag(custom)) {
            return true;
        }

        if (entity instanceof PlayerEntity player) {
            boolean noTabEntry = mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(player.getUuid()) == null;
            boolean numericOrCit = name.matches("\\d{1,8}") || name.startsWith("cit-");
            return noTabEntry || (numericOrCit && !display.equals(name));
        }

        return false;
    }

    private boolean containsNpcTag(String s) {
        return s.contains("npc") || s.contains("znpc") || s.contains("нпс") || s.contains("наставник");
    }

    private String cleanName(String s) {
        return s == null ? "" : s.replaceAll("(?i)§.", "").replaceAll("(?i)&.", "").replaceAll("\\p{Cntrl}", "").trim().toLowerCase(Locale.ROOT);
    }

    /** Бонус окна обнаружения от HitBox.AffectToAura (дистанцию удара не меняет). */
    private static float hitboxBonus() {
        HitBox hb = ModuleManager.INSTANCE.getModule(HitBox.class);
        if (hb != null && hb.isToggled() && hb.affectToAura.getValue()) {
            return hb.xzExpand.compute();
        }
        return 0f;
    }

    public static float getAttackDistance(LivingEntity entity) {
        return attackRange.compute();
    }

    /** @return {@code true}, если поворот захвачен; иначе бить нельзя. */
    private boolean aimAtTarget() {
        if (textureRun == null) return false;

        Vec3d targetPos = textureRun.getBoundingBox().getCenter();
        float[] rots = RotationUtils.getLegitRotations(targetPos);
        this.targetYaw = rots[0];
        this.targetPitch = rots[1];

        RotationUtils rotUtils = RotationUtils.getInstance();
        if (!rotUtils.setRotationSmooth(this, ROTATION_PRIORITY, targetYaw, targetPitch, turnSpeed.compute(), false)) {
            report("ротацию занял другой модуль");
            return false;
        }
        rotUtils.keepWireFresh();
        aiming = true;
        return true;
    }

    /**
     * Сервер уже видит угол, доведённый до цели? Удар его и несёт: разрыв
     * между прошлым и новым отправленным углом стремится к нулю — скачков
     * ротации для анчита не существует.
     */
    private boolean isAimReady() {
        RotationUtils rotUtils = RotationUtils.getInstance();
        float dyaw = Math.abs(MathHelper.wrapDegrees(targetYaw - rotUtils.getSentYaw()));
        float dpitch = Math.abs(targetPitch - rotUtils.getSentPitch());
        if (dyaw <= AIM_EPSILON && dpitch <= AIM_EPSILON) return true;
        // Если луч по отправленным углам уже попадает в хитбокс цели — наведение готово
        return !isRayBlocked();
    }

    private boolean shouldSkipAttack() {
        if (mc.player == null) return true;

        // Если включено авто-оружие, пустая рука или не-оружие не блокируют удар —
        // модуль сам сделает быстрый pulse-свап на меч перед атакой.
        if (mc.player.getMainHandStack().isEmpty()) {
            if (!autoWeapon.getValue() || findBestWeaponInHotbar() == -1) {
                report("в руке пусто — не бью");
                return true;
            }
        }

        if (colorMeasure.process("Не бить если кушаешь") && mc.player.isUsingItem() && !(mc.player.getActiveItem().getItem() instanceof ShieldItem)) {
            return true;
        }

        if (colorMeasure.process("Не бить в контейнерах") && mc.currentScreen != null) {
            return true;
        }

        if (colorMeasure.process("Бить только оружием")) {
            ItemStack main = mc.player.getMainHandStack();
            boolean isWeapon = main.isIn(ItemTags.SWORDS) || main.isIn(ItemTags.AXES) || main.getItem() == Items.MACE;
            if (!isWeapon) {
                if (!autoWeapon.getValue() || findBestWeaponInHotbar() == -1) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isRayBlocked() {
        if (!colorMeasure.process("Проверка на луч")) return false;
        if (textureRun == null || mc.player == null || mc.world == null) return true;

        Vec3d eye = mc.player.getEyePos();

        // Луч строится строго по углу, который сервер уже видел: удар и луч
        // идут с одним углом — проверку «куда смотрели — туда ударили» не
        // обмануть.
        RotationUtils rotUtils = RotationUtils.getInstance();
        float yaw   = rotUtils.getSentYaw();
        float pitch = rotUtils.getSentPitch();

        Vec3d look = RotationUtils.getRotationVector(pitch, yaw);
        double reach = getAttackDistance(textureRun);
        Vec3d end = eye.add(look.multiply(reach));

        // 1. Raycast to real target bounding box (сервер считает по настоящим)
        net.minecraft.util.math.Box targetBox = realBox(textureRun).expand(0.1);
        java.util.Optional<Vec3d> hit = targetBox.raycast(eye, end);

        if (hit.isEmpty()) {
            net.minecraft.util.math.Box searchBox = mc.player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0, 1.0, 1.0);
            net.minecraft.util.hit.EntityHitResult entityHit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                mc.player, eye, end, searchBox, e -> e == textureRun, reach * reach
            );
            if (entityHit == null || entityHit.getEntity() != textureRun) {
                report(String.format("луч мимо (ротация %.0f/%.0f): дистанция %.2f, лимит %.2f",
                        yaw, pitch, mc.player.distanceTo(textureRun), reach));
                return true;
            }
        }

        // 2. Block obstruction check if not hitting through blocks
        if (!colorMeasure.process("Бить через блоки")) {
            net.minecraft.util.hit.BlockHitResult blockHit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eye, end, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player
            ));
            if (blockHit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                double blockDistSq = eye.squaredDistanceTo(blockHit.getPos());
                double targetDistSq = hit.map(eye::squaredDistanceTo).orElse(eye.squaredDistanceTo(textureRun.getEntityPos()));
                if (blockDistSq < targetDistSq - 0.05) {
                    report("луч перекрыт блоком");
                    return true;
                }
            }
        }

        return false;
    }

    /** Порог кулдауна с лёгким человеческим дрожанием момента. */
    private boolean isAttackReady() {
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        float gate = cooldownGate.compute() - ThreadLocalRandom.current().nextFloat() * 0.02f;
        return progress >= gate;
    }

    /**
     * Условия честного крита (без споофа позиций — он у Criticals и флагается).
     * Умные криты ждут естественного падения, ничего не имитируя.
     */
    private boolean canCrit() {
        if (mc.player == null) return false;
        if (mc.player.isOnGround()) return false;
        boolean falling = mc.player.fallDistance > 0.0f || mc.player.getVelocity().y < -0.0784;
        return falling
                && !mc.player.isClimbing()
                && !mc.player.isTouchingWater()
                && !isPlayerInWeb()
                && !mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
    }

    private boolean isPlayerInWeb() {
        BlockPos pos = mc.player.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(net.minecraft.block.Blocks.COBWEB);
    }

    private void performAttack() {
        if (textureRun == null || mc.player == null || mc.interactionManager == null) return;

        // Свапы через SwapManager: слот уходит на сервер немедленно, в этом же
        // батче до удара — бонус булавы и разбивание щита засчитываются
        // по-настоящему. Автовозврат тоже на нём.
        boolean swapped = false;
        if (profileInvoke.compute() && mc.player.fallDistance > 0
                && (mc.player.getY() - textureRun.getY()) >= scaleSave.compute()) {
            int maceSlot = findItemInHotbar(Items.MACE);
            if (maceSlot != -1) {
                SwapManager.getInstance().pulse(this, maceSlot, SwapManager.Priority.WEAPON, 2);
                swapped = true;
            }
        }

        if (!swapped && colorMeasure.process("Ломать щит") && textureRun.isBlocking()) {
            int axeSlot = findAxeInHotbar();
            if (axeSlot != -1) {
                SwapManager.getInstance().pulse(this, axeSlot, SwapManager.Priority.WEAPON, 2);
                swapped = true;
            }
        }

        if (!swapped && autoWeapon.getValue()) {
            ItemStack main = mc.player.getMainHandStack();
            boolean isWeapon = main.isIn(ItemTags.SWORDS) || main.isIn(ItemTags.AXES) || main.isOf(Items.MACE);
            if (!isWeapon) {
                int bestWeapon = findBestWeaponInHotbar();
                if (bestWeapon != -1) {
                    SwapManager.getInstance().pulse(this, bestWeapon, SwapManager.Priority.WEAPON, 2);
                }
            }
        }

        // Отжим щита перед ударом.
        boolean wasUsingShield = false;
        if (colorMeasure.process("Отжим щита") && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.SHIELD) {
            mc.interactionManager.stopUsingItem(mc.player);
            wasUsingShield = true;
        }

        // Сброс спринта на тик удара: чистая механика W-tap.
        if (sprintReset.getValue() && mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        mc.interactionManager.attackEntity(mc.player, textureRun);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Отжим восстанавливаем тем же слотом предмета.
        if (wasUsingShield) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        }
    }

    private void report(String reason) {
        if (!debugOutput.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastDebugAt < DEBUG_COOLDOWN_MS) return;

        this.lastDebugAt = now;
        info("AttackAura: %s", reason);
    }

    private int findItemInHotbar(net.minecraft.item.Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) {
                return i;
            }
        }
        return -1;
    }

    private int findAxeInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) {
                return i;
            }
        }
        return -1;
    }

    private int findBestWeaponInHotbar() {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestDamage = -1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            boolean isSword = stack.isIn(ItemTags.SWORDS);
            boolean isAxe = stack.isIn(ItemTags.AXES);
            if (isSword || isAxe) {
                float dmg = 4.0f;
                if (stack.isOf(Items.NETHERITE_SWORD)) dmg = 8.0f;
                else if (stack.isOf(Items.DIAMOND_SWORD)) dmg = 7.0f;
                else if (stack.isOf(Items.NETHERITE_AXE)) dmg = 7.5f;
                else if (stack.isOf(Items.DIAMOND_AXE)) dmg = 6.5f;
                else if (stack.isOf(Items.IRON_SWORD)) dmg = 6.0f;
                else if (stack.isOf(Items.IRON_AXE)) dmg = 5.5f;
                if (dmg > bestDamage) {
                    bestDamage = dmg;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    public Entity getTarget() {
        return textureRun;
    }
}
