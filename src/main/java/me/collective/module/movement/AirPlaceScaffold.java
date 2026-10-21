package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.AirPlaceExecutor;
import me.collective.util.GrimUtils;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.math.Direction;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.PlacementUtils;
import me.collective.util.RotationUtils;
import me.collective.util.SwapManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * AirPlaceScaffold: скаффолд с нуля, ориентированный на воздушную постановку
 * блоков (airplace) без опоры под прицелом.
 *
 * <p>Ключевые решения против GrimAC:
 * <ul>
 *   <li>каждая постановка идёт через ротацию к реальной точке клика, а валы
 *       синхронизируются через RotationUtils (углы в пакетах и у прицела совпадают);</li>
 *   <li>воздушные клики выполняются только внутри досягаемости от глаз, чтобы
 *       не ловить reach-проверки;</li>
 *   <li>rate-limit: бюджет в тик + скользящее окно, чтобы не всплеск из
 *       десятков interact-пакетов подряд не выделялся на фоне lamпово игры.</li>
 * </ul>
 */
public class AirPlaceScaffold extends Module {
    public static AirPlaceScaffold INSTANCE;

    public final EnumSetting<RotationMode> rotationMode
        = add(new EnumSetting<>("RotationMode", RotationMode.None));
    public final NumberSetting rotationSpeed = add(new NumberSetting(
        "RotationSpeed", 120.0, 10.0, 360.0, 10.0,
        (v) -> rotationMode.getValue() != RotationMode.None));
    public final NumberSetting radius = add(new NumberSetting("Radius", 0.0, 0.0, 6.0, 0.5));
    public final BooleanSetting velocityPredict = add(new BooleanSetting("VelocityPredict", true));
    public final NumberSetting velocityMultiplier
        = add(new NumberSetting("VelocityMultiplier", 1.0, 0.5, 5.0, 0.1, (v) -> velocityPredict.getValue()));
    public final NumberSetting extendDistance
        = add(new NumberSetting("ExtendDistance", 0.5, 0.0, 2.0, 0.05));
    public final BooleanSetting tower = add(new BooleanSetting("Tower", false));
    public final NumberSetting maxBlocksPerTick
        = add(new NumberSetting("BlocksPerTick", 1.0, 0.1, 9.0, 0.1));
    public final BooleanSetting safeWalk = add(new BooleanSetting("SafeWalk", true));
    public final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));

    private static final double REACH = 4.5;
    private static final double REACH_MARGIN = 0.25;
    private static final double ALIGN_EPS = 3.0;
    private static final int ALIGN_TIMEOUT = 6;
    private static final int PLACE_COOLDOWN = 6;
    private static final int WINDOW_TICKS = 6;
    private static final int WINDOW_MAX = 9;

    private final Map<BlockPos, Long> cooldown = new HashMap<>();
    private final ArrayDeque<Long> window = new ArrayDeque<>();

    private long tick;
    private BlockPos aimPos;
    private BlockHitResult aimHit;
    private int alignTicks;
    /** Накопитель дробного лимита BlocksPerTick (например, 0.5 → блок раз в 2 тика). */
    private double placeAccumulator;
    /** Бюджет постановок на текущий тик (целая часть накопителя). */
    private int tickBudget;

    public AirPlaceScaffold() {
        super("AirPlaceScaffold",
            "Скаффолд с воздушной постановкой блоков и радиусным заполнением области.",
            Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.cooldown.clear();
        this.window.clear();
        this.tick = 0;
        this.placeAccumulator = 0;
        dropAim();
    }

    @Override
    public void onDisable() {
        this.cooldown.clear();
        this.window.clear();
        this.placeAccumulator = 0;
        dropAim();
        RotationUtils.getInstance().release(this);
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        this.tick++;
        this.cooldown.values().removeIf(expiry -> expiry <= this.tick);

        // Дробный лимит BlocksPerTick: накопитель вносит rate каждый тик,
        // целая часть тика становится бюджетом. 0.5 → блок через тик,
        // 0.1 → блок раз в 10 тиков; остаток переносится дальше.
        this.placeAccumulator = Math.min(
            this.placeAccumulator + this.maxBlocksPerTick.getValue(), 4.0);
        this.tickBudget = Math.min((int) this.placeAccumulator, windowBudget());
        this.placeAccumulator -= this.tickBudget;

        if (checkBlocksRunOut()) return;

        boolean towering = this.tower.getValue() && shouldTower();
        Vec3d basePos = mc.player.getEntityPos();
        Vec3d aimCenter = towering ? basePos : predict(basePos);

        List<BlockPos> candidates = buildCandidates(basePos, aimCenter, towering);
        if (candidates.isEmpty()) {
            dropAim();
            return;
        }

        if (this.rotationMode.getValue() == RotationMode.None) {
            batchPlace(candidates);
        } else {
            rotateAndAim(candidates.getFirst());
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (this.aimPos == null || this.aimHit == null) return;

        RotationUtils rot = RotationUtils.getInstance();
        boolean aligned = rot.isRotating() && rot.isAlignedFor(this, ALIGN_EPS);

        if (!aligned) {
            if (++this.alignTicks <= ALIGN_TIMEOUT) return;
            dropAim();
            return;
        }

        if (!isAimValid(this.aimHit)) {
            if (++this.alignTicks <= ALIGN_TIMEOUT) return;
            dropAim();
            return;
        }

        if (!isReachable(this.aimHit.getPos())) {
            dropAim();
            return;
        }

        FindItemResult item = findBlock();
        if (!item.found()) {
            dropAim();
            return;
        }

        if (placeAt(this.aimPos, this.aimHit, item)) dropAim();
    }

    /**
     * Готовит список позиций: основной блок под центром, расширенный вниз при
     * падении, затем кольцо радиуса вокруг центра. Сортировка — по расстоянию
     * от центра, ближние в приоритете: сначала перекрываем путь прямо под
     * игроком, оттуда расходимся наружу.
     */
    private List<BlockPos> buildCandidates(Vec3d basePos, Vec3d center, boolean towering) {
        List<BlockPos> positions = new ArrayList<>();
        int feetLevel = (int) Math.floor(basePos.y) - 1;

        BlockPos.Mutable cursor = new BlockPos.Mutable();

        cursor.set((int) Math.floor(center.x), feetLevel, (int) Math.floor(center.z));
        if (canPlace(cursor.toImmutable())) positions.add(cursor.toImmutable());

        if (!towering && Math.abs(mc.player.getVelocity().y) > 0.4) {
            BlockPos deep = cursor.toImmutable().down();
            if (canPlace(deep)) positions.add(deep);
        }

        double r = this.radius.getValue();
        if (r > 0.0) fillRadius(positions, cursor, feetLevel, center, r);

        return positions;
    }

    private void fillRadius(List<BlockPos> positions, BlockPos.Mutable cursor,
                            int feetLevel, Vec3d center, double radius) {
        int bound = (int) Math.ceil(radius - 1e-9);
        double radiusSq = radius * radius;
        int centerX = (int) Math.floor(center.x);
        int centerZ = (int) Math.floor(center.z);
        double reachSq = Math.pow(REACH - REACH_MARGIN, 2);

        List<BlockPos> ring = new ArrayList<>();

        for (int dx = -bound; dx <= bound; dx++) {
            for (int dz = -bound; dz <= bound; dz++) {
                if (dx * dx + dz * dz > radiusSq) continue;

                cursor.set(centerX + dx, feetLevel, centerZ + dz);
                BlockPos pos = cursor.toImmutable();
                if (positions.contains(pos) || !canPlace(pos)) continue;

                if (eyeCenterDistanceSq(pos) > reachSq) continue;

                ring.add(pos);
            }
        }

        ring.sort((a, b) -> Double.compare(
            horizontalDistanceSq(a, center.x, center.z),
            horizontalDistanceSq(b, center.x, center.z)));

        positions.addAll(ring);
    }

    private void batchPlace(List<BlockPos> candidates) {
        int budget = this.tickBudget;
        int placed = 0;

        FindItemResult item = findBlock();

        for (BlockPos pos : candidates) {
            if (placed >= budget) break;
            if (!item.found()) break;

            BlockHitResult hit = resolveHit(pos);
            if (hit == null) {
                this.cooldown.put(pos, this.tick + PLACE_COOLDOWN);
                continue;
            }

            if (!placeAt(pos, hit, item)) break;
            placed++;
        }
    }

    private void rotateAndAim(BlockPos pos) {
        if (windowBudget() <= 0) {
            dropAim();
            return;
        }

        BlockHitResult hit = resolveHit(pos);
        if (hit == null) {
            this.cooldown.put(pos, this.tick + PLACE_COOLDOWN);
            dropAim();
            return;
        }

        if (!isReachable(hit.getPos())) {
            dropAim();
            return;
        }

        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), hit.getPos());

        boolean claimed;
        switch (this.rotationMode.getValue()) {
            case Instant -> claimed = RotationUtils.getInstance().setRotationSilentInstant(
                this, SwapManager.Priority.PLACE, rot[0], rot[1]);
            case Direct -> claimed = RotationUtils.getInstance().setRotationSilentDirect(
                this, SwapManager.Priority.PLACE, rot[0], rot[1],
                this.rotationSpeed.getValue());
            default -> claimed = RotationUtils.getInstance().setRotationSilent(
                this, SwapManager.Priority.PLACE, rot[0], rot[1],
                this.rotationSpeed.getValue());
        }

        if (!claimed) {
            dropAim();
            return;
        }

        if (!pos.equals(this.aimPos)) this.alignTicks = 0;
        this.aimPos = pos.toImmutable();
        this.aimHit = hit;
    }

    private boolean placeAt(BlockPos pos, BlockHitResult hit, FindItemResult item) {
        if (this.tickBudget <= 0) return false;

        Hand hand = item.getHand();

        if (!item.isOffhand()
            && !SwapManager.getInstance().hold(this, item.slot(), SwapManager.Priority.PLACE, true, 2)) {
            return false;
        }

        BlockState predicted = predictedState(item);

        if (hit.isInsideBlock()) {
            grimAirPlace(hit, pos, predicted, hand);
        } else {
            BlockHitResult wire = new BlockHitResult(
                hit.getPos(), hit.getSide(), hit.getBlockPos(), false);
            AirPlaceExecutor.silentPlace(wire, pos, predicted, hand, true);
        }

        this.tickBudget--;
        this.cooldown.put(pos.toImmutable(), this.tick + PLACE_COOLDOWN);
        this.window.addLast(this.tick);
        return true;
    }

    /**
     * Воздушная постановка по грим-хендшейку, проверенному на 2b2t:
     * фиктивный START_DESTROY_BLOCK сбрасывает блок-трекинг, затем interact
     * с sequence, swing и второй START_DESTROY.
     */
    private void grimAirPlace(BlockHitResult hit, BlockPos pos, BlockState predicted, Hand hand) {
        var connection = mc.getNetworkHandler();
        if (connection == null || mc.world == null) return;

        BlockHitResult wire = new BlockHitResult(hit.getPos(), hit.getSide(), pos, false);

        connection.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, BlockPos.ORIGIN, Direction.DOWN));
        connection.sendPacket(new PlayerInteractBlockC2SPacket(hand, wire, GrimUtils.nextSequence()));
        connection.sendPacket(new HandSwingC2SPacket(hand));
        connection.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, BlockPos.ORIGIN, Direction.DOWN));

        if (predicted != null) {
            mc.world.setBlockState(pos, predicted, 11);
        }
    }

    private BlockState predictedState(FindItemResult item) {
        ItemStack stack = item.isOffhand()
            ? mc.player.getOffHandStack()
            : mc.player.getInventory().getStack(item.slot());

        return stack.getItem() instanceof BlockItem blockItem
            ? blockItem.getBlock().getDefaultState()
            : null;
    }

    private BlockHitResult resolveHit(BlockPos pos) {
        BlockHitResult support = PlacementUtils.getSupportHit(pos, REACH - REACH_MARGIN);
        return support != null ? support : PlacementUtils.getAirPlaceHit(pos, REACH - REACH_MARGIN);
    }

    private boolean canPlace(BlockPos pos) {
        if (this.cooldown.containsKey(pos)) return false;
        if (mc.world.isOutOfHeightLimit(pos)) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return false;

        return mc.world.getEntitiesByClass(Entity.class, new Box(pos), e ->
            e instanceof ItemEntity
                || e instanceof ExperienceOrbEntity
                || e instanceof ProjectileEntity
                || e instanceof EndCrystalEntity
                || e == mc.player).isEmpty();
    }

    private boolean isReachable(Vec3d hitPos) {
        return mc.player.getEyePos().squaredDistanceTo(hitPos)
            <= Math.pow(REACH - REACH_MARGIN, 2);
    }

    /**
     * Проверка как у Grim: реконструирует луч из присланных углов и
     * пересекает его с плоскостью грани, по которой идёт клик. Пока луч
     * попадает в ту же грань (~0.25 от точки клика) — интеракция валидна.
     * Такой допуск устойчив к дрейфу при движении (角度 lag в тик —
     * считанные смещения), в отличие от проверки «угла до точки».
     */
    private boolean isAimValid(BlockHitResult hit) {
        RotationUtils rot = RotationUtils.getInstance();
        Vec3d ray = Vec3d.fromPolar(rot.getSentPitch(), rot.getSentYaw());
        Vec3d eye = mc.player.getEyePos();

        Vec3d normal = Vec3d.of(hit.getSide().getVector());
        Vec3d planePoint = hit.getPos();

        double denom = normal.dotProduct(ray);
        if (Math.abs(denom) < 1e-6) return false;

        double t = planePoint.subtract(eye).dotProduct(normal) / denom;
        if (t < 0.0 || t > REACH) return false;

        Vec3d intersection = eye.add(ray.multiply(t));
        return intersection.squaredDistanceTo(planePoint) <= 0.25 * 0.25;
    }

    private double eyeCenterDistanceSq(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(
            new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    private double horizontalDistanceSq(BlockPos pos, double cx, double cz) {
        double dx = pos.getX() + 0.5 - cx;
        double dz = pos.getZ() + 0.5 - cz;
        return dx * dx + dz * dz;
    }

    private Vec3d predict(Vec3d basePos) {
        Vec3d velocity = mc.player.getVelocity();
        Vec3d center = basePos;

        if (this.velocityPredict.getValue() && Math.abs(velocity.y) > 0.1) {
            double mult = this.velocityMultiplier.getValue();
            center = center.add(velocity.x * mult, velocity.y * mult, velocity.z * mult);
        }

        Vec3d move = movementVector();
        if (move != Vec3d.ZERO && this.extendDistance.getValue() > 0.0) {
            center = center.add(move.multiply(this.extendDistance.getValue()));
        }

        return center;
    }

    private Vec3d movementVector() {
        float yaw = mc.player.getYaw();
        Vec3d v = Vec3d.ZERO;

        if (mc.options.forwardKey.isPressed()) v = v.add(Vec3d.fromPolar(0.0f, yaw));
        if (mc.options.backKey.isPressed()) v = v.add(Vec3d.fromPolar(0.0f, yaw + 180.0f));
        if (mc.options.leftKey.isPressed()) v = v.add(Vec3d.fromPolar(0.0f, yaw - 90.0f));
        if (mc.options.rightKey.isPressed()) v = v.add(Vec3d.fromPolar(0.0f, yaw + 90.0f));

        return v.lengthSquared() > 0.0 ? v.normalize() : v;
    }

    private boolean shouldTower() {
        return mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed();
    }

    private FindItemResult findBlock() {
        BlockPos target = this.aimPos != null ? this.aimPos
            : new BlockPos(
                (int) Math.floor(mc.player.getEntityPos().x),
                (int) Math.floor(mc.player.getEntityPos().y) - 1,
                (int) Math.floor(mc.player.getEntityPos().z));

        Predicate<ItemStack> isScaffoldBlock = stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

            Block block = blockItem.getBlock();
            BlockState state = block.getDefaultState();

            if (state.getCollisionShape(mc.world, target).isEmpty()) return false;

            return !(block instanceof FallingBlock)
                || !FallingBlock.canFallThrough(mc.world.getBlockState(
                    mc.player.getBlockPos().down()));
        };

        return InventoryUtil.findInHotbarOrOffhand(isScaffoldBlock);
    }

    private boolean checkBlocksRunOut() {
        if (!this.autoDisable.getValue()) return false;
        if (!this.window.isEmpty()) return false;
        if (findBlock().found()) return false;

        toggle();
        return true;
    }

    private int windowBudget() {
        while (!this.window.isEmpty()
            && this.window.peekFirst() <= this.tick - WINDOW_TICKS) {
            this.window.pollFirst();
        }

        return WINDOW_MAX - this.window.size();
    }

    private void dropAim() {
        this.aimPos = null;
        this.aimHit = null;
        this.alignTicks = 0;
    }

    /**
     * Читается миксином пользовательского ввода прямо в тике движения:
     * если под каким-то углом хитбокса нет опоры — подставляем sneak,
     * чтобы ванилла сама затормозила на кромке.
     */
    public boolean isAtEdge() {
        if (mc.player == null || mc.world == null || !mc.player.isOnGround()) return false;

        Box box = mc.player.getBoundingBox().expand(0.1);
        double feetY = box.minY - 0.01;
        int y = (int) Math.floor(feetY);
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(box.maxX);
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(box.maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isSafeWalking() {
        return this.isToggled() && this.safeWalk.getValue();
    }

    /**
     * Занят ли модуль активной постановкой прямо сейчас: включён и имеет
     * текущую цель под игроком. Используется OffhandManager, чтобы не трогать
     * офхенд, пока скаффолд работает.
     */
    public boolean isBusy() {
        return this.isToggled() && this.aimPos != null;
    }

    public enum RotationMode {
        None,
        Instant,
        Direct,
        Smooth
    }
}
