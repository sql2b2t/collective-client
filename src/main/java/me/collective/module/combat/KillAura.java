package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.setting.*;
import me.collective.util.RotationUtils;
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
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {
    public static KillAura INSTANCE;

    public static NumberSetting source = new NumberSetting("Радиус атаки", 3.0F, 3.0F, 6.0F, 0.1F);
    public static NumberSetting target = new NumberSetting("Радиус обнаружения", 1.0F, 0.0F, 5.0F, 0.1F);
    public static ModeSetting pending = new ModeSetting("Режим ротации", "Smooth", "Matrix", "Random Smooth ", "Snap", "FOV", "Smooth ", "FunTime", "FT-New", "SpookyTime", "ST-Test", "Legit", "Custom");
    public static ActionSetting previous = new ActionSetting("Конструктор ротации", 0);
    public static NumberSetting latest = new NumberSetting("AI Jitter", 1.0F, 0.0F, 2.0F, 0.05F);
    public static ActionSetting vectorMatch = new ActionSetting("AI Lab", 0);
    public static ModeSetting itemProject = new ModeSetting("Режим снапа", "Fast", "Fast", "Smooth", "Random");
    public static NumberSetting responseCompute = new NumberSetting("FOV", 90.0F, 5.0F, 180.0F, 1.0F);
    public static BooleanSetting providerFetch = new BooleanSetting("Отображать FOV", true);
    public static NumberSetting profileDraw = new NumberSetting("Скорость", 0.08F, 0.02F, 0.4F, 0.01F);

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

    public static ModeSetting presetSave = new ModeSetting("Тайминг удара", "Быстрый", "Быстрый", "Динамичный");
    public static BooleanSetting windowConvert = new BooleanSetting("Адаптивный тайминг", false);
    public static ModeSetting presetWrite = new ModeSetting("Режим спринта", "Обычный", "Обычный", "Обновленный", "Тестовый", "Легит");

    public static ChoiceSetting colorMeasure = new ChoiceSetting(
        "Проверки до удара",
        new BooleanSetting("Бить через блоки", false),
        new BooleanSetting("Бить только оружием", false),
        new BooleanSetting("Не бить если кушаешь", true),
        new BooleanSetting("Не бить в контейнерах ", false),
        new BooleanSetting("Ломать щит", false),
        new BooleanSetting("Отжим щита", false),
        new BooleanSetting("Проверка на луч", true)
    );

    public static BooleanSetting rayTraceCheck = new BooleanSetting("Проверка на луч", true);

    /** Пишет в чат, почему модуль не сработал. */
    public static BooleanSetting debugOutput = new BooleanSetting("Отладка", false);

    public static ChoiceSetting animationSchedule = new ChoiceSetting(
        "Дополнительные настройки",
        new BooleanSetting("Расширенная настройки для атаки", true),
        new BooleanSetting("Умные криты", false),
        new BooleanSetting("Увеличенная дистанция удара", false),
        new BooleanSetting("Приоритет ближайшей цели", false)
    );

    // Радиус атаки общий для всех сущностей (игроки и мобы)
    public static NumberSetting unifiedRadius = new NumberSetting("Радиус атаки", 3.0F, 3.0F, 6.0F, 0.1F);
    public static ModeSetting outputCollapse = new ModeSetting("Режим движения", "Default", "Default", "Free", "Target", "Преследование");

    public static BooleanSetting profileInvoke = new BooleanSetting("Булава", false);
    public static ModeSetting sourceSchedule = new ModeSetting("Режим булавы", "Авто", "Авто", "Бинд");
    public static KeybindSetting timerRender = new KeybindSetting("Кнопка булавы", -1);
    public static NumberSetting scaleSave = new NumberSetting("Высота булавы", 2.0F, 0.5F, 6.0F, 0.1F);
    public static BooleanSetting colorCompute = new BooleanSetting("Усиление урона", false);
    public static BooleanSetting scaleAdapt = new BooleanSetting("Отладка булавы", false);

    public static LivingEntity textureRun;
    private int originalSlot = -1;
    private boolean swappedMace = false;
    private boolean swappedAxe = false;
    private long lastAttackTime = 0L;
    private float currentYaw = 0f;
    private float currentPitch = 0f;

    /**
     * Приоритет захвата ротации.
     *
     * <p>Раньше поворот брался через {@code RotationUtils.setServerRotation()},
     * а он внутри зовёт {@code setRotationSilent()} → {@code claim(null, 0)}.
     * С владельцем {@code null} и приоритетом 0 любой другой модуль с
     * приоритетом ≥ 0 (GrimAnchorAura — 40, CrystalAura — 40) перехватывал
     * ротацию, и KillAura молча получал отказ: не ротай, не бей.
     */
    private static final int ROTATION_PRIORITY = 45;

    private static final long DEBUG_COOLDOWN_MS = 2000L;
    private long lastDebugAt;

    public KillAura() {
        super("AttackAura", "Автоматически бьет энтити - таргетов", Category.COMBAT);
        INSTANCE = this;

        add(source);
        add(target);
        add(pending);
        add(previous);
        add(latest);
        add(vectorMatch);
        add(itemProject);
        add(responseCompute);
        add(providerFetch);
        add(profileDraw);
        add(providerClose);
        add(presetSave);
        add(windowConvert);
        add(presetWrite);
        add(colorMeasure);
        add(rayTraceCheck);
        add(debugOutput);
        add(animationSchedule);
        add(unifiedRadius);
        add(outputCollapse);
        add(profileInvoke);
        add(sourceSchedule);
        add(timerRender);
        add(scaleSave);
        add(colorCompute);
        add(scaleAdapt);
    }

    @Override
    public void onEnable() {
        textureRun = null;
        swappedMace = false;
        swappedAxe = false;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        restoreSlot();
        textureRun = null;
        RotationUtils.getInstance().release(this);
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Verify or search target
        if (textureRun == null || !isValidTarget(textureRun)) {
            findTarget();
        }

        if (textureRun == null) {
            // Подробная причина уже отдана в findTarget()
            restoreSlot();
            return;
        }

        // Handle Movement Modes
        handleMovement();

        // Handle Rotations
        handleRotations();

        // Pre-attack checks
        if (shouldSkipAttack()) {
            report("пропуск по предварительным проверкам");
            return;
        }

        // Raycast / Ray check ("Проверка на луч")
        if (isRayBlocked()) {
            report("луч не попал в цель");
            return;
        }

        // Cooldown and timing check
        if (!isAttackReady()) {
            return;
        }

        // Smart Crits check
        if (animationSchedule.process("Умные криты") && !canCrit()) {
            return;
        }

        // Execute Attack
        performAttack();
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (pending.process("FT-New")) {
            if (event.packet instanceof HandSwingC2SPacket || event.packet instanceof UpdateSelectedSlotC2SPacket) {
                // FT-New packet synchronization
            }
        }
    }

    private void findTarget() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
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
            if (animationSchedule.process("Приоритет ближайшей цели")) {
                score = boxDistance(mc.player, living);
            } else {
                Vec3d targetCenter = living.getBoundingBox().getCenter();
                Vec3d toTarget = targetCenter.subtract(eyePos).normalize();
                score = Math.acos(MathHelper.clamp(lookVec.dotProduct(toTarget), -1.0, 1.0));
            }

            if (score < bestDist) {
                bestDist = score;
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
     * <p>Раньше здесь стоял {@code mc.player.distanceTo(entity)}, а это
     * расстояние между позициями — то есть нога-в-ноги. У игрока рост 1.8,
     * поэтому удар с 3 блоков по хитбоксу превращался в 3.5–3.8 по ногам,
     * и окно обнаружения фактически съезжало внутрь. Сервер же считает
     * дальность удара между хитбоксами — как здесь.
     */
    private static double boxDistance(Entity a, Entity b) {
        net.minecraft.util.math.Box ba = a.getBoundingBox();
        net.minecraft.util.math.Box bb = b.getBoundingBox();

        double dx = Math.max(0.0, Math.max(ba.minX - bb.maxX, bb.minX - ba.maxX));
        double dy = Math.max(0.0, Math.max(ba.minY - bb.maxY, bb.minY - ba.maxY));
        double dz = Math.max(0.0, Math.max(ba.minZ - bb.maxZ, bb.minZ - ba.maxZ));

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Почему сущность не годится в цель. {@code null} — годится. */
    private String rejectReason(LivingEntity entity) {
        if (entity == null || entity == mc.player || entity instanceof ClientPlayerEntity) return "это мы сами";
        if (!entity.isAlive()) return "мёртв";
        if (entity.isInvulnerable()) return "неуязвим";
        if (entity instanceof ArmorStandEntity) return "арморстенд";

        float maxDist = getAttackDistance(entity) + target.compute() + hitboxBonus();
        double dist = boxDistance(mc.player, entity);
        if (dist > maxDist) {
            return String.format("%s в %.1f блоках, окно %.1f (атака %.1f + обнаружение %.1f)",
                    entity.getName().getString(), dist, maxDist,
                    getAttackDistance(entity), target.compute());
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

    /** Бонус дистанции от HitBox.AffectToAura. */
    private static float hitboxBonus() {
        HitBox hb = ModuleManager.INSTANCE.getModule(HitBox.class);
        if (hb != null && hb.isToggled() && hb.affectToAura.getValue()) {
            return hb.xzExpand.compute();
        }
        return 0f;
    }

    public static float getAttackDistance(LivingEntity entity) {
        float dist = unifiedRadius.compute();

        if (animationSchedule.process("Увеличенная дистанция удара")) {
            float totalHp = entity.getHealth() + entity.getAbsorptionAmount();
            if (totalHp >= 10.0F && totalHp <= 12.0F) {
                dist += 0.15F;
            }
        }

        return dist;
    }

    private void handleRotations() {
        if (textureRun == null) return;

        Vec3d targetPos = textureRun.getBoundingBox().getCenter();
        float[] rots = RotationUtils.getLegitRotations(targetPos);
        float targetYaw = rots[0];
        float targetPitch = rots[1];

        String mode = pending.compute().trim();
        switch (mode) {
            case "Snap":
                currentYaw = targetYaw;
                currentPitch = targetPitch;
                break;
            case "Matrix":
                float jitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.4f;
                currentYaw = targetYaw + jitter;
                currentPitch = MathHelper.clamp(targetPitch + jitter, -90f, 90f);
                break;
            case "FunTime":
                float ftJitter = (ThreadLocalRandom.current().nextFloat() - 0.5f) * latest.compute();
                currentYaw = targetYaw + ftJitter;
                currentPitch = MathHelper.clamp(targetPitch + (ftJitter * 0.5f), -90f, 90f);
                break;
            case "Legit":
                float speed = profileDraw.compute();
                currentYaw = MathHelper.lerpAngleDegrees(speed, currentYaw == 0 ? mc.player.getYaw() : currentYaw, targetYaw);
                currentPitch = MathHelper.lerp(speed, currentPitch == 0 ? mc.player.getPitch() : currentPitch, targetPitch);
                break;
            case "FOV":
                float angleDiff = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - targetYaw));
                if (angleDiff <= responseCompute.compute() / 2f) {
                    currentYaw = targetYaw;
                    currentPitch = targetPitch;
                }
                break;
            case "Smooth":
            default:
                currentYaw = MathHelper.lerpAngleDegrees(0.35f, currentYaw == 0 ? mc.player.getYaw() : currentYaw, targetYaw);
                currentPitch = MathHelper.lerp(0.35f, currentPitch == 0 ? mc.player.getPitch() : currentPitch, targetPitch);
                break;
        }

        // Захватываем поворот от своего имени и с высоким приоритетом,
        // иначе его перехватит любой боевой модуль.
        RotationUtils rotUtils = RotationUtils.getInstance();
        if (!rotUtils.setRotationSmooth(this, ROTATION_PRIORITY, currentYaw, currentPitch, 180.0, false)) {
            report("ротацию перехватил другой модуль");
            return;
        }
        rotUtils.keepWireFresh();
    }

    private void handleMovement() {
        if (textureRun == null || mc.player == null) return;

        String mode = outputCollapse.compute().trim();
        if (mode.equalsIgnoreCase("Преследование")) {
            Vec3d diff = textureRun.getEntityPos().subtract(mc.player.getEntityPos());
            if (diff.lengthSquared() > 1.0) {
                float targetAngle = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
                mc.player.setYaw(MathHelper.lerpAngleDegrees(0.2f, mc.player.getYaw(), targetAngle));
            }
        }
    }

    private boolean shouldSkipAttack() {
        if (mc.player == null) return true;

        if (colorMeasure.process("Не бить если кушаешь") && mc.player.isUsingItem() && !(mc.player.getActiveItem().getItem() instanceof ShieldItem)) {
            return true;
        }

        if (colorMeasure.process("Не бить в контейнерах ") && mc.currentScreen != null) {
            return true;
        }

        if (colorMeasure.process("Бить только оружием")) {
            ItemStack main = mc.player.getMainHandStack();
            boolean isWeapon = main.isIn(ItemTags.SWORDS) || main.isIn(ItemTags.AXES) || main.getItem() == Items.MACE;
            if (!isWeapon) return true;
        }

        return false;
    }

    private boolean isRayBlocked() {
        if (!colorMeasure.process("Проверка на луч") && !rayTraceCheck.compute()) {
            return false;
        }
        if (textureRun == null || mc.player == null || mc.world == null) {
            return true;
        }

        Vec3d eye = mc.player.getEyePos();

        // Проверяем луч по СВОЕМУ углу и по углу, который реально ушёл
        // серверу — оба. Раньше здесь читался безусловно getSentYaw(), а это
        // угол того, кто последним захватил ротацию: если это был другой
        // модуль, KillAura проверял чужой прицел, луч в цель не попадал,
        // isRayBlocked() возвращал true и удар не проходил.
        float sentYaw   = RotationUtils.getInstance().getSentYaw();
        float sentPitch = RotationUtils.getInstance().getSentPitch();
        boolean sentIsOurs = MathHelper.abs(MathHelper.wrapDegrees(sentYaw - currentYaw)) < 5.0f;

        // По чужому углу смысла проверять нет — сервер всё равно применит
        // KillAura-угол только если ротация не перехвачена. Проверяем
        // отправленный, если он наш, иначе свой.
        float yaw   = sentIsOurs ? sentYaw   : currentYaw;
        float pitch = sentIsOurs ? sentPitch : currentPitch;

        if (yaw == 0f && pitch == 0f) {
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
        }

        Vec3d look = RotationUtils.getRotationVector(pitch, yaw);
        double reach = getAttackDistance(textureRun);
        Vec3d end = eye.add(look.multiply(reach));

        // 1. Raycast to target bounding box
        net.minecraft.util.math.Box targetBox = textureRun.getBoundingBox().expand(0.1);
        java.util.Optional<Vec3d> hit = targetBox.raycast(eye, end);

        if (hit.isEmpty()) {
            net.minecraft.util.math.Box searchBox = mc.player.getBoundingBox().stretch(look.multiply(reach)).expand(1.0, 1.0, 1.0);
            net.minecraft.util.hit.EntityHitResult entityHit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                mc.player, eye, end, searchBox, e -> e == textureRun, reach * reach
            );
            if (entityHit == null || entityHit.getEntity() != textureRun) {
                report(String.format("луч мимо: дистанция %.2f, лимит %.2f",
                        mc.player.distanceTo(textureRun), reach));
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

    private boolean isAttackReady() {
        float progress = mc.player.getAttackCooldownProgress(0.5f);
        if (presetSave.process("Быстрый")) {
            return progress >= 0.92f;
        } else {
            // Динамичный тайминг
            long now = System.currentTimeMillis();
            if (now - lastAttackTime < 500L) {
                return progress >= 0.98f;
            }
            return progress >= 0.95f;
        }
    }

    private boolean canCrit() {
        if (mc.player == null) return true;
        boolean falling = mc.player.fallDistance > 0.0f || mc.player.getVelocity().y < -0.0784;
        boolean onClimbable = mc.player.isClimbing();
        boolean inWater = mc.player.isTouchingWater();
        boolean inWeb = isPlayerInWeb();
        return falling && !onClimbable && !inWater && !inWeb;
    }

    private boolean isPlayerInWeb() {
        BlockPos pos = mc.player.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(net.minecraft.block.Blocks.COBWEB);
    }

    private void performAttack() {
        if (textureRun == null || mc.player == null || mc.interactionManager == null) return;

        // Check Mace auto swap
        if (profileInvoke.compute() && sourceSchedule.process("Авто")) {
            double heightDiff = mc.player.getY() - textureRun.getY();
            if (heightDiff >= scaleSave.compute() && mc.player.fallDistance > 0) {
                int maceSlot = findItemInHotbar(Items.MACE);
                if (maceSlot != -1 && mc.player.getInventory().getSelectedSlot() != maceSlot) {
                    originalSlot = mc.player.getInventory().getSelectedSlot();
                    mc.player.getInventory().setSelectedSlot(maceSlot);
                    swappedMace = true;
                }
            }
        }

        // Check Shield break auto swap
        if (colorMeasure.process("Ломать щит") && textureRun.isBlocking() && !swappedMace) {
            int axeSlot = findAxeInHotbar();
            if (axeSlot != -1 && mc.player.getInventory().getSelectedSlot() != axeSlot) {
                originalSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(axeSlot);
                swappedAxe = true;
            }
        }

        // Unpress shield before attacking
        boolean wasUsingShield = false;
        if (colorMeasure.process("Отжим щита") && mc.player.isUsingItem() && mc.player.getActiveItem().getItem() == Items.SHIELD) {
            mc.interactionManager.stopUsingItem(mc.player);
            wasUsingShield = true;
        }

        // Sprint reset for Grim/Vanilla knockback
        boolean wasSprinting = mc.player.isSprinting();
        if (presetWrite.process("Обычный") || presetWrite.process("Легит")) {
            mc.player.setSprinting(false);
        }

        // Attack
        mc.interactionManager.attackEntity(mc.player, textureRun);
        mc.player.swingHand(Hand.MAIN_HAND);
        lastAttackTime = System.currentTimeMillis();

        // Restore sprinting
        if (wasSprinting && !presetWrite.process("Легит")) {
            mc.player.setSprinting(true);
        }

        // Restore shield if needed
        if (wasUsingShield) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
        }

        // Restore previous weapon slot if swapped
        restoreSlot();
    }

    private void report(String reason) {
        if (!debugOutput.getValue()) return;

        long now = System.currentTimeMillis();
        if (now - this.lastDebugAt < DEBUG_COOLDOWN_MS) return;

        this.lastDebugAt = now;
        info("AttackAura: %s", reason);
    }

    private void restoreSlot() {
        if ((swappedMace || swappedAxe) && originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
            swappedMace = false;
            swappedAxe = false;
        }
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

    public Entity getTarget() {
        return textureRun;
    }
}
