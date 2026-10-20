package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.*;
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

import java.util.*;
import java.util.function.Predicate;

/**
 * GrimScaffold: ставит блоки под себя пакетами и поворотами, которые принимает Grim.
 * 1:1 порт из b2xy.
 */
public class GrimScaffold extends Module {
    public static GrimScaffold INSTANCE;

    public final BooleanSetting tower = add(new BooleanSetting("Tower", false));
    public final BooleanSetting onlyOnClick = add(new BooleanSetting("OnlyOnClick", false));
    public final EnumSetting<RotationMode> rotationMode = add(new EnumSetting<>("RotationMode", RotationMode.None));
    public final NumberSetting extendDistance = add(new NumberSetting("ExtendDistance", 0.1, 0.0, 2.0, 0.05));
    public final BooleanSetting safeWalk = add(new BooleanSetting("SafeWalk", false));
    public final BooleanSetting velocityPredict = add(new BooleanSetting("VelocityPredict", true));
    public final NumberSetting velocityMultiplier = add(new NumberSetting("VelocityMultiplier", 1.0, 0.5, 5.0, 0.1, (v) -> velocityPredict.getValue()));
    public final BooleanSetting adaptiveSpeed = add(new BooleanSetting("AdaptiveSpeed", true));
    public final NumberSetting maxBlocksPerTick = add(new NumberSetting("MaxBlocksPerTick", 1, 1, 5, 1, (v) -> adaptiveSpeed.getValue()));
    public final NumberSetting radius = add(new NumberSetting("Radius", 0.0, 0.0, 3.0, 0.5));
    public final BooleanSetting radiusPredictCenter = add(new BooleanSetting("RadiusPredictCenter", true, (v) -> radius.getValue() > 0.0));

    private static final double SCAFFOLD_TURN_SPEED = 120.0;
    private static final double SCAFFOLD_ALIGN_EPS = 3.0;
    private static final double REACH = 4.5;
    private static final int ALIGN_TIMEOUT = 6;
    private static final int PLACE_COOLDOWN = 6;
    private static final int WINDOW_TICKS = 6;
    private static final int WINDOW_MAX = 9;

    private final BlockPos.Mutable targetPos = new BlockPos.Mutable();
    private final Map<BlockPos, Integer> cooldown = new HashMap<>();
    private final ArrayDeque<Integer> window = new ArrayDeque<>();

    private int tick;
    private int tickDelay;
    private BlockPos aimPos;
    private BlockHitResult aimHit;
    private int alignTicks;

    public GrimScaffold() {
        super("GrimScaffold", "Ставит блоки под себя пакетами и поворотами, совместимыми с Grim.", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        this.tick = 0;
        this.tickDelay = 0;
        this.cooldown.clear();
        this.window.clear();
        dropAim();
    }

    @Override
    public void onDisable() {
        this.cooldown.clear();
        this.window.clear();
        dropAim();
        RotationUtils.getInstance().clearRotations(this);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        this.tick++;
        this.cooldown.values().removeIf(exp -> exp <= this.tick);

        boolean towering = this.tower.getValue() && shouldTower();

        if (this.onlyOnClick.getValue() && !mc.options.useKey.isPressed() && !towering) {
            dropAim();
            return;
        }

        this.tickDelay++;
        if (this.tickDelay < 1) return;

        List<BlockPos> candidates = getPlacementPositions();
        if (candidates.isEmpty()) {
            dropAim();
            return;
        }

        FindItemResult blockItem = findBlock(candidates.getFirst());
        if (!blockItem.found()) {
            dropAim();
            return;
        }

        if (this.rotationMode.getValue() == RotationMode.None) {
            dropAim();

            int budget = Math.min(getBlocksPerTick(), windowBudget());
            int placed = 0;

            for (BlockPos pos : candidates) {
                if (placed >= budget) break;

                BlockHitResult hit = resolveHit(pos);
                if (hit == null) {
                    this.cooldown.put(pos, this.tick + PLACE_COOLDOWN);
                    continue;
                }

                if (!placeAt(pos, hit, blockItem)) break;
                placed++;
            }
            return;
        }

        BlockPos pos = candidates.getFirst();
        if (!pos.equals(this.aimPos)) this.alignTicks = 0;

        BlockHitResult hit = resolveHit(pos);
        if (hit == null) {
            this.cooldown.put(pos, this.tick + PLACE_COOLDOWN);
            dropAim();
            return;
        }

        if (windowBudget() <= 0) return;

        if (rotationUnsafe()) {
            placeAt(pos, hit, blockItem);
            dropAim();
            return;
        }

        float[] rotations = RotationUtils.getRotationsTo(mc.player.getEyePos(), hit.getPos());

        boolean claimed = this.rotationMode.getValue() == RotationMode.Simple
            ? RotationUtils.getInstance().setRotationSilentDirect(
                this, 35, rotations[0], rotations[1], SCAFFOLD_TURN_SPEED)
            : RotationUtils.getInstance().setRotationSilent(
                this, 35, rotations[0], rotations[1], SCAFFOLD_TURN_SPEED);

        if (!claimed) {
            placeAt(pos, hit, blockItem);
            dropAim();
            return;
        }

        this.aimPos = pos.toImmutable();
        this.aimHit = hit;
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (this.aimHit == null || this.aimPos == null) return;

        if (!canPlace(this.aimPos)) {
            dropAim();
            return;
        }

        RotationUtils rot = RotationUtils.getInstance();
        boolean aligned = rot.isRotating() && rot.isAlignedFor(this, SCAFFOLD_ALIGN_EPS);

        if (!aligned && ++this.alignTicks <= ALIGN_TIMEOUT) return;

        FindItemResult blockItem = findBlock(this.aimPos);
        if (!blockItem.found()) {
            dropAim();
            return;
        }

        if (placeAt(this.aimPos, this.aimHit, blockItem)) dropAim();
    }

    private void dropAim() {
        this.aimPos = null;
        this.aimHit = null;
        this.alignTicks = 0;
    }

    private BlockHitResult resolveHit(BlockPos pos) {
        BlockHitResult hit = PlacementUtils.getSupportHit(pos, REACH);
        return hit != null ? hit : PlacementUtils.getAirPlaceHit(pos, REACH);
    }

    private boolean placeAt(BlockPos pos, BlockHitResult hit, FindItemResult blockItem) {
        Hand hand;

        if (blockItem.isOffhand()) {
            hand = Hand.OFF_HAND;
        } else {
            if (!SwapManager.getInstance().hold(this, blockItem.slot(), 35, true, 2)) {
                return false;
            }
            hand = Hand.MAIN_HAND;
        }

        BlockState predicted = predictedState(blockItem);

        if (hit.isInsideBlock() && hand == Hand.OFF_HAND) {
            BlockHitResult wireHit = new BlockHitResult(hit.getPos(), hit.getSide(), hit.getBlockPos(), false);
            AirPlaceExecutor.silentPlace(wireHit, pos, predicted, hand, true);
        } else {
            AirPlaceExecutor.place(hit, pos, hand, predicted, AirPlaceExecutor.Method.Default);
        }

        this.cooldown.put(pos.toImmutable(), this.tick + PLACE_COOLDOWN);
        this.window.addLast(this.tick);

        this.tickDelay = 0;
        return true;
    }

    private BlockState predictedState(FindItemResult blockItem) {
        ItemStack stack = blockItem.isOffhand()
            ? mc.player.getOffHandStack()
            : mc.player.getInventory().getStack(blockItem.slot());

        return stack.getItem() instanceof BlockItem blockItem2
            ? blockItem2.getBlock().getDefaultState()
            : null;
    }

    private boolean rotationUnsafe() {
        if (mc.player.isGliding() || mc.player.isSwimming()
            || mc.player.isUsingRiptide() || mc.player.hasVehicle()) {
            return true;
        }

        return mc.player.isSprinting()
            && mc.player.isOnGround()
            && mc.options.jumpKey.isPressed();
    }

    private int windowBudget() {
        while (!this.window.isEmpty() && this.window.peekFirst() <= this.tick - WINDOW_TICKS) {
            this.window.pollFirst();
        }

        return WINDOW_MAX - this.window.size();
    }

    private int getBlocksPerTick() {
        if (!this.adaptiveSpeed.getValue()) return 1;

        double velocity = Math.abs(mc.player.getVelocity().y);

        if (velocity > 0.5) return this.maxBlocksPerTick.getIntValue();
        if (velocity > 0.3) return Math.min(2, this.maxBlocksPerTick.getIntValue());

        return 1;
    }

    private List<BlockPos> getPlacementPositions() {
        List<BlockPos> positions = new ArrayList<>();

        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d velocity = mc.player.getVelocity();
        Vec3d predictedPos = playerPos;

        if (this.velocityPredict.getValue() && Math.abs(velocity.y) > 0.1) {
            double multiplier = this.velocityMultiplier.getValue();
            predictedPos = playerPos.add(velocity.x * multiplier, velocity.y * multiplier, velocity.z * multiplier);
        }

        if (isMovingHorizontally() && this.extendDistance.getValue() > 0.0) {
            predictedPos = predictedPos.add(getMovementVector().multiply(this.extendDistance.getValue()));
        }

        this.targetPos.set(predictedPos.x, playerPos.y - 1.0, predictedPos.z);
        if (canPlace(this.targetPos)) positions.add(this.targetPos.toImmutable());

        if (Math.abs(velocity.y) > 0.4) {
            BlockPos belowTarget = this.targetPos.down().toImmutable();
            if (canPlace(belowTarget)) positions.add(belowTarget);
        }

        double radius = this.radius.getValue();
        if (radius > 0.0) {
            Vec3d circleCenter = this.radiusPredictCenter.getValue() ? predictedPos : playerPos;
            addRadiusPositions(positions, circleCenter, radius);
        }

        return positions;
    }

    /**
     * Заполняет область в круге заданного радиуса вокруг центра на уровне ног-1.
     * Ближе к центру — приоритетнее: сначала перекрываем путь вплотную к игроку.
     * Позиции дальше досягаемости воздушного клика отбрасываются, чтобы не ловить
     * reach-флаги Grim.
     */
    private void addRadiusPositions(List<BlockPos> positions, Vec3d center, double radius) {
        int bound = (int) Math.ceil(radius - 1e-9);
        double radiusSq = radius * radius;
        int centerX = (int) Math.floor(center.x);
        int centerZ = (int) Math.floor(center.z);
        int levelY = (int) Math.floor(mc.player.getEntityPos().y - 1.0);
        Vec3d eye = mc.player.getEyePos();
        double maxReachSq = (REACH - 0.3) * (REACH - 0.3);

        List<BlockPos> extras = new ArrayList<>();

        for (int dx = -bound; dx <= bound; dx++) {
            for (int dz = -bound; dz <= bound; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > radiusSq) continue;

                this.targetPos.set(centerX + dx, levelY, centerZ + dz);

                BlockPos pos = this.targetPos.toImmutable();
                if (positions.contains(pos)) continue;

                if (!canPlace(pos)) continue;

                if (eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > maxReachSq) continue;

                extras.add(pos);
            }
        }

        extras.sort(Comparator.comparingDouble(p -> {
            double ex = p.getX() + 0.5 - center.x;
            double ez = p.getZ() + 0.5 - center.z;
            return ex * ex + ez * ez;
        }));

        positions.addAll(extras);
    }

    private boolean canPlace(BlockPos pos) {
        if (this.cooldown.containsKey(pos)) return false;
        if (mc.world.isOutOfHeightLimit(pos)) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return false;

        return !mc.world.getEntitiesByClass(Entity.class, new Box(pos), e ->
            !(e instanceof ItemEntity)
                && !(e instanceof ExperienceOrbEntity)
                && !(e instanceof ProjectileEntity)
                && !(e instanceof EndCrystalEntity)
                && e != mc.player).isEmpty();
    }

    private Vec3d getMovementVector() {
        Vec3d velocity = Vec3d.ZERO;
        float yaw = mc.player.getYaw();

        if (mc.options.forwardKey.isPressed()) velocity = velocity.add(Vec3d.fromPolar(0.0f, yaw));
        if (mc.options.backKey.isPressed()) velocity = velocity.add(Vec3d.fromPolar(0.0f, yaw + 180.0f));
        if (mc.options.leftKey.isPressed()) velocity = velocity.add(Vec3d.fromPolar(0.0f, yaw - 90.0f));
        if (mc.options.rightKey.isPressed()) velocity = velocity.add(Vec3d.fromPolar(0.0f, yaw + 90.0f));

        if (velocity.lengthSquared() > 0.0) return velocity.normalize();

        return velocity;
    }

    private boolean isMovingHorizontally() {
        return mc.options.forwardKey.isPressed()
            || mc.options.backKey.isPressed()
            || mc.options.leftKey.isPressed()
            || mc.options.rightKey.isPressed();
    }

    private boolean shouldTower() {
        return mc.options.jumpKey.isPressed()
            && !mc.options.sneakKey.isPressed()
            && !isMovingHorizontally();
    }

    private FindItemResult findBlock(BlockPos target) {
        Predicate<ItemStack> isScaffoldBlock = stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;

            Block block = blockItem.getBlock();

            if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, target))) {
                return false;
            }

            return !(block instanceof FallingBlock)
                || !FallingBlock.canFallThrough(mc.world.getBlockState(target.down()));
        };

        return InventoryUtil.findInHotbarOrOffhand(isScaffoldBlock);
    }

    public boolean isSafeWalking() {
        return this.isToggled() && this.safeWalk.getValue();
    }

    public enum RotationMode {
        None,
        Simple,
        Precise
    }
}
