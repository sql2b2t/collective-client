package me.collective.util.printer;

import me.collective.util.RotationUtils;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/**
 * Решатель вариантов установки блока (порт из BepHax NEW-SRC). Подбирает точку
 * клика/грань/поворот так, чтобы блок встал с нужным состоянием и не был
 * заблокирован соседями, с учётом reach-лимита и трешхолдов Grim.
 *
 * Адаптация под 1.21.11 (Yarn): ClipContext -> RaycastContext (ShapeType/FluidHandling),
 * Level.clip -> Level.raycast, Vec3 -> Vec3, InteractionHand -> InteractionHand,
 * getStateForPlacement -> Block.getPlacementState, canBeReplaced -> isReplaceable.
 */
public final class PlacementSolver {
    private static final Set<String> IGNORED = Set.of(
        "waterlogged",
        "powered",
        "power",
        "lit",
        "distance",
        "persistent",
        "note",
        "instrument",
        "age",
        "stage",
        "snowy",
        "bottom",
        "occupied",
        "signal_fire",
        "extended",
        "triggered",
        "locked",
        "delay",
        "in_wall",
        "shape"
    );
    private static final Set<String> CONNECTION = Set.of("north", "south", "east", "west", "up", "down");
    private static final double[] SAMPLES = new double[]{0.5, 0.25, 0.75, 0.1, 0.9};
    private static final float JITTER_MARGIN = 0.25F;
    private static final int[] K_ORDER = new int[]{0, 1, -1, 2, -2, 3, -3, 4};
    private static final int[] K_ORDER_SPRINT = new int[]{0, 1, -1};
    private static final float[] LATTICE_PITCHES = new float[]{30.0F, -30.0F, 75.0F, -75.0F};
    private static final double[] LATTICE_SAMPLES = new double[]{0.5, 0.25, 0.75};
    private static final float LATTICE_MARGIN = 1.0F;
    private static final int LATTICE_SIM_BUDGET = 800;
    private static int latticeBudget;
    private static final float[][] ANY_ROTATION_GRID = new float[][]{
        {0.0F, 30.0F},
        {90.0F, 30.0F},
        {180.0F, 30.0F},
        {-90.0F, 30.0F},
        {0.0F, -30.0F},
        {90.0F, -30.0F},
        {180.0F, -30.0F},
        {-90.0F, -30.0F},
        {0.0F, 75.0F},
        {90.0F, 75.0F},
        {180.0F, 75.0F},
        {-90.0F, 75.0F},
        {0.0F, -75.0F},
        {90.0F, -75.0F},
        {180.0F, -75.0F},
        {-90.0F, -75.0F}
    };

    private PlacementSolver() {
    }

    public static Solution solve(BlockPos target, BlockState desired, ItemStack stack, double reach, boolean allowFace, boolean allowAir) {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().world == null) {
            return null;
        }

        if (!(stack.getItem() instanceof BlockItem)) {
            return null;
        }

        Vec3 eye = Minecraft.getInstance().player.getEyePos();
        double reachSq = reach * reach;
        if (allowFace) {
            BlockState ws = Minecraft.getInstance().world.getBlockState(target);
            if (!ws.isAir() && !clickTriggersAction(ws.getBlock())) {
                Solution self = solveFaces(target, desired, stack, eye, reachSq, target, false);
                if (self != null) {
                    return self;
                }
            }

            Solution real = solveAgainstFace(target, desired, stack, eye, reachSq);
            if (real != null) {
                return real;
            }
        }

        return allowAir ? solveFaces(target, desired, stack, eye, reachSq, target, true) : null;
    }

    public static Solution solveAnyRotation(BlockPos target, BlockState desired, ItemStack stack, double reach) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().world != null) {
            if (!(stack.getItem() instanceof BlockItem)) {
                return null;
            }

            Vec3 eye = Minecraft.getInstance().player.getEyePos();
            double reachSq = reach * reach;
            latticeBudget = 400;
            BlockState ws = Minecraft.getInstance().world.getBlockState(target);
            if (!ws.isAir() && clickTriggersAction(ws.getBlock())) {
                return null;
            }

            for (Direction face : Direction.values()) {
                Vec3 faceCenter = Vec3.ofCenter(target).add(normal(face).multiply(0.5));

                for (double u : LATTICE_SAMPLES) {
                    for (double v : LATTICE_SAMPLES) {
                        if (latticeBudget <= 0) {
                            return null;
                        }

                        Vec3 sample = faceOffset(faceCenter, face, u - 0.5, v - 0.5);
                        Solution s = tryAnyRotation(target, desired, stack, eye, reachSq, target, face, sample);
                        if (s != null) {
                            return s;
                        }
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static Solution tryAnyRotation(
        BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq, BlockPos hitBlock, Direction clickFace, Vec3 sample
    ) {
        if (!faceVisible(eye, hitBlock, clickFace)) {
            return null;
        }

        if (sample.subtract(eye).lengthSquared() < 1.0E-6) {
            return null;
        }

        if (eye.squaredDistanceTo(sample) > reachSq) {
            return null;
        }

        BlockHitResult hit = new BlockHitResult(sample, clickFace, hitBlock, true);
        BlockState result = null;

        for (float[] look : ANY_ROTATION_GRID) {
            latticeBudget--;
            result = simulate(hit, stack, look[0], look[1], target);
            if (result == null || !statesMatch(desired, result)) {
                return null;
            }
        }

        return new Solution(
            hit, InteractionHand.MAIN_HAND, Minecraft.getInstance().player.getYaw(), Minecraft.getInstance().player.getPitch(), result, null, true
        );
    }

    public static Solution solveMoving(
        BlockPos target, BlockState desired, ItemStack stack, double reach, boolean allowFace, boolean allowAir, boolean sprinting
    ) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().world != null) {
            if (!(stack.getItem() instanceof BlockItem)) {
                return null;
            }

            Vec3 eye = Minecraft.getInstance().player.getEyePos();
            double reachSq = reach * reach;
            float realYaw = Minecraft.getInstance().player.getYaw();
            latticeBudget = 800;

            for (int k : sprinting ? K_ORDER_SPRINT : K_ORDER) {
                for (float pitch : LATTICE_PITCHES) {
                    float yaw = realYaw + k * 45.0F;
                    if (allowFace) {
                        BlockState ws = Minecraft.getInstance().world.getBlockState(target);
                        if (!ws.isAir() && !clickTriggersAction(ws.getBlock())) {
                            Solution self = latticeFaces(target, desired, stack, eye, reachSq, target, false, yaw, pitch, k);
                            if (self != null) {
                                return self;
                            }
                        }

                        for (Direction face : Direction.values()) {
                            BlockPos neighbor = target.offset(face);
                            BlockState neighborState = Minecraft.getInstance().world.getBlockState(neighbor);
                            if (!neighborState.isReplaceable()
                                && !neighborState.getCollisionShape(Minecraft.getInstance().world, neighbor).isEmpty()
                                && !clickTriggersAction(neighborState.getBlock())) {
                                Solution s = latticeSamples(
                                    target, desired, stack, eye, reachSq, neighbor, face.getOpposite(), false, yaw, pitch, k
                                );
                                if (s != null) {
                                    return s;
                                }
                            }
                        }
                    }

                    if (allowAir) {
                        Solution s = latticeFaces(target, desired, stack, eye, reachSq, target, true, yaw, pitch, k);
                        if (s != null) {
                            return s;
                        }
                    }

                    if (latticeBudget <= 0) {
                        return null;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static Solution latticeFaces(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        boolean inside,
        float yaw,
        float pitch,
        int k
    ) {
        for (Direction face : Direction.values()) {
            Solution s = latticeSamples(target, desired, stack, eye, reachSq, hitBlock, face, inside, yaw, pitch, k);
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    private static Solution latticeSamples(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        Direction clickFace,
        boolean inside,
        float yaw,
        float pitch,
        int k
    ) {
        Vec3 faceCenter = Vec3.ofCenter(hitBlock).add(normal(clickFace).multiply(0.5));

        for (double u : LATTICE_SAMPLES) {
            for (double v : LATTICE_SAMPLES) {
                Vec3 sample = faceOffset(faceCenter, clickFace, u - 0.5, v - 0.5);
                Solution s = tryLattice(target, desired, stack, eye, reachSq, hitBlock, clickFace, sample, inside, yaw, pitch, k);
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    private static Solution tryLattice(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        Direction clickFace,
        Vec3 sample,
        boolean inside,
        float yaw,
        float pitch,
        int k
    ) {
        if (latticeBudget <= 0) {
            return null;
        }

        if (inside && !faceVisible(eye, hitBlock, clickFace)) {
            return null;
        }

        if (sample.subtract(eye).lengthSquared() < 1.0E-6) {
            return null;
        }

        if (eye.squaredDistanceTo(sample) > reachSq) {
            return null;
        }

        Vec3 hitVec = validateRay(eye, sample, hitBlock, clickFace, inside);
        if (hitVec != null && !(eye.squaredDistanceTo(hitVec) > reachSq)) {
            BlockHitResult hit = new BlockHitResult(hitVec, clickFace, hitBlock, inside);
            latticeBudget -= 3;
            BlockState result = simulate(hit, stack, yaw, pitch, target);
            if (result != null && statesMatch(desired, result)) {
                for (float dy : new float[]{-1.0F, 1.0F}) {
                    BlockState drifted = simulate(hit, stack, yaw + dy, pitch, target);
                    if (drifted == null || !statesMatch(desired, drifted)) {
                        return null;
                    }
                }

                return new Solution(hit, InteractionHand.MAIN_HAND, yaw, pitch, result, k, false);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public static boolean confirmSent(Solution s, BlockPos target, BlockState desired, ItemStack stack) {
        if (s.anyRotation()) {
            return true;
        } else if (Minecraft.getInstance().player != null && Minecraft.getInstance().world != null) {
            RotationUtils rot = RotationUtils.getInstance();
            BlockState result = simulate(s.hit(), stack, rot.getServerYaw(), rot.getServerPitch(), target);
            return result != null && statesMatch(desired, result);
        } else {
            return false;
        }
    }

    public static Solution revalidate(
        Solution s, BlockPos target, BlockState desired, ItemStack stack, double reach, boolean sprinting
    ) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().world != null) {
            Vec3 eye = Minecraft.getInstance().player.getEyePos();
            double reachSq = reach * reach;
            BlockHitResult h = s.hit();
            if (s.anyRotation()) {
                latticeBudget = 400;
                return tryAnyRotation(target, desired, stack, eye, reachSq, h.getBlockPos(), h.getSide(), h.getPos());
            }

            if (s.latticeK() == null) {
                return tryCandidate(target, desired, stack, eye, reachSq, h.getBlockPos(), h.getSide(), h.getPos(), h.isInsideBlock());
            }

            float realYaw = Minecraft.getInstance().player.getYaw();
            latticeBudget = 800;

            for (int k : sprinting ? K_ORDER_SPRINT : K_ORDER) {
                for (float pitch : LATTICE_PITCHES) {
                    Solution r = tryLattice(
                        target,
                        desired,
                        stack,
                        eye,
                        reachSq,
                        h.getBlockPos(),
                        h.getSide(),
                        h.getPos(),
                        h.isInsideBlock(),
                        realYaw + k * 45.0F,
                        pitch,
                        k
                    );
                    if (r != null) {
                        return r;
                    }
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private static Solution solveAgainstFace(BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq) {
        for (Direction face : Direction.values()) {
            BlockPos neighbor = target.offset(face);
            BlockState neighborState = Minecraft.getInstance().world.getBlockState(neighbor);
            if (!neighborState.isReplaceable()
                && !neighborState.getCollisionShape(Minecraft.getInstance().world, neighbor).isEmpty()
                && !clickTriggersAction(neighborState.getBlock())) {
                Solution s = trySamples(target, desired, stack, eye, reachSq, neighbor, face.getOpposite(), false);
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    private static Solution solveFaces(
        BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq, BlockPos hitBlock, boolean inside
    ) {
        for (Direction face : Direction.values()) {
            Solution s = trySamples(target, desired, stack, eye, reachSq, hitBlock, face, inside);
            if (s != null) {
                return s;
            }
        }

        return null;
    }

    private static Solution trySamples(
        BlockPos target, BlockState desired, ItemStack stack, Vec3 eye, double reachSq, BlockPos hitBlock, Direction clickFace, boolean inside
    ) {
        Vec3 faceCenter = Vec3.ofCenter(hitBlock).add(normal(clickFace).multiply(0.5));

        for (double u : SAMPLES) {
            for (double v : SAMPLES) {
                Vec3 sample = faceOffset(faceCenter, clickFace, u - 0.5, v - 0.5);
                Solution s = tryCandidate(target, desired, stack, eye, reachSq, hitBlock, clickFace, sample, inside);
                if (s != null) {
                    return s;
                }
            }
        }

        return null;
    }

    private static Solution tryCandidate(
        BlockPos target,
        BlockState desired,
        ItemStack stack,
        Vec3 eye,
        double reachSq,
        BlockPos hitBlock,
        Direction clickFace,
        Vec3 sample,
        boolean inside
    ) {
        if (inside && !faceVisible(eye, hitBlock, clickFace)) {
            return null;
        }

        if (sample.subtract(eye).lengthSquared() < 1.0E-6) {
            return null;
        }

        if (eye.squaredDistanceTo(sample) > reachSq) {
            return null;
        }

        Vec3 hitVec = validateRay(eye, sample, hitBlock, clickFace, inside);
        if (hitVec != null && !(eye.squaredDistanceTo(hitVec) > reachSq)) {
            float[] rot = RotationUtils.getRotationsTo(eye, hitVec);
            BlockHitResult hit = new BlockHitResult(hitVec, clickFace, hitBlock, inside);
            BlockState result = simulate(hit, stack, rot[0], rot[1], target);
            if (result != null && statesMatch(desired, result)) {
                float[] deltas = new float[]{-0.25F, 0.25F};

                for (float d : deltas) {
                    BlockState jitteredYaw = simulate(hit, stack, rot[0] + d, rot[1], target);
                    if (jitteredYaw != null && statesMatch(desired, jitteredYaw)) {
                        BlockState jitteredPitch = simulate(hit, stack, rot[0], Math.clamp(rot[1] + d, -90.0F, 90.0F), target);
                        if (jitteredPitch != null && statesMatch(desired, jitteredPitch)) {
                            continue;
                        }

                        return null;
                    }

                    return null;
                }

                return new Solution(hit, InteractionHand.MAIN_HAND, rot[0], rot[1], result, null, false);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static Vec3 validateRay(Vec3 eye, Vec3 sample, BlockPos hitBlock, Direction clickFace, boolean inside) {
        if (inside) {
            return sample;
        } else {
            Vec3 dir = sample.subtract(eye);
            Vec3 end = eye.add(dir.multiply(1.0 + 0.01 / dir.length()));
            BlockHitResult clip = Minecraft.getInstance()
                .world
                .raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, Minecraft.getInstance().player));
            if (clip.getType() != HitResult.Type.BLOCK) {
                return null;
            } else {
                return clip.getBlockPos().equals(hitBlock) && clip.getSide() == clickFace ? clip.getPos() : null;
            }
        }
    }

    private static boolean faceVisible(Vec3 eye, BlockPos pos, Direction face) {
        return switch (face) {
            case DOWN -> eye.y < pos.getY();
            case UP -> eye.y > pos.getY() + 1.0;
            case NORTH -> eye.z < pos.getZ();
            case SOUTH -> eye.z > pos.getZ() + 1.0;
            case WEST -> eye.x < pos.getX();
            case EAST -> eye.x > pos.getX() + 1.0;
        };
    }

    private static Vec3 faceOffset(Vec3 center, Direction face, double du, double dv) {
        return switch (face.getAxis()) {
            case X -> center.add(0.0, dv, du);
            case Y -> center.add(du, 0.0, dv);
            case Z -> center.add(du, dv, 0.0);
        };
    }

    private static BlockState simulate(BlockHitResult hit, ItemStack stack, float yaw, float pitch, BlockPos target) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            PlacementContextSim ctx = new PlacementContextSim(Minecraft.getInstance().player, InteractionHand.MAIN_HAND, stack, hit, yaw, pitch);
            if (!ctx.canPlace()) {
                return null;
            } else {
                return !ctx.getBlockPos().equals(target) ? null : blockItem.getBlock().getPlacementState(ctx);
            }
        } else {
            return null;
        }
    }

    private static boolean clickTriggersAction(Block block) {
        return block instanceof net.minecraft.block.BlockWithEntity
            || block instanceof net.minecraft.block.CraftingTableBlock
            || block instanceof net.minecraft.block.AnvilBlock
            || block instanceof net.minecraft.block.DoorBlock
            || block instanceof net.minecraft.block.TrapdoorBlock
            || block instanceof net.minecraft.block.FenceGateBlock
            || block instanceof net.minecraft.block.ButtonBlock
            || block instanceof net.minecraft.block.LeverBlock
            || block instanceof net.minecraft.block.BedBlock
            || block instanceof net.minecraft.block.NoteBlock
            || block instanceof net.minecraft.block.RepeaterBlock
            || block instanceof net.minecraft.block.ComparatorBlock
            || block instanceof RespawnAnchorBlock
            || block instanceof DragonEggBlock
            || block instanceof CakeBlock
            || block instanceof FlowerPotBlock
            || block instanceof ComposterBlock;
    }

    public static boolean statesMatch(BlockState desired, BlockState actual) {
        if (desired.getBlock() != actual.getBlock()) {
            return false;
        }

        boolean faces = faceDefinedByPlacement(desired);

        for (Property<?> prop : desired.getProperties()) {
            String name = prop.getName();
            if (!IGNORED.contains(name) && (faces || !CONNECTION.contains(name)) && !desired.get(prop).equals(actual.get(prop))) {
                return false;
            }
        }

        return true;
    }

    public static boolean faceDefinedByPlacement(BlockState state) {
        return state.getBlock() instanceof MultifaceBlock;
    }

    private static Vec3 normal(Direction d) {
        return new Vec3(d.getOffsetX(), d.getOffsetY(), d.getOffsetZ());
    }

    public record Solution(BlockHitResult hit, InteractionHand hand, float yaw, float pitch, BlockState predicted, Integer latticeK, boolean anyRotation) {
    }
}