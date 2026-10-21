package me.collective.util.sort;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Доводит игрока до сундука так, чтобы к нему было видно откуда-то рядом.
 *
 * <p>Главная сложность не в пути, а в ракурсе: Baritone доводит до блока,
 * но может встать так, что сундук не виден и не открывается. Поэтому цель
 * формулируется не «к блоку», а «в любую из точек, откуда сундук видно».
 */
public final class ChestNavigator {
    private static final int SPOT_RADIUS = 4;
    private static final int SPOT_UP = 3;
    private static final int SPOT_DOWN = 3;
    private static final double SPOT_REACH = 3.8;
    private static final int MAX_SPOTS = 16;
    private static final int MAX_TRACES = 64;
    private static final int MAX_ATTEMPTS = 3;
    private static final int STALL_TICKS = 400;
    private static final double EYE_HEIGHT = 1.62;

    private BlockPos target;
    private Goal issued;
    private boolean issuedPrecise;
    private int sinceIssue;
    private int attempts;
    private double bestDist = Double.MAX_VALUE;
    private int stallTicks;
    private String failure = "";
    private Boolean savedAllowBreak;
    private Boolean savedAllowPlace;

    /** Человекочитаемая причина, почему до сундука не дойти. */
    public String failure() {
        return failure;
    }

    public Status travelTo(BlockPos canonical) {
        // Без Baritone путь построить нечем: честно завершаемся с причиной.
        if (!me.collective.util.BaritoneHelper.isAvailable()) {
            failure = "Baritone не установлен";
            return Status.UNREACHABLE;
        }
        if (net.minecraft.client.MinecraftClient.getInstance().player == null || net.minecraft.client.MinecraftClient.getInstance().world == null || canonical == null) {
            failure = "не в мире";
            return Status.UNREACHABLE;
        }

        if (!canonical.equals(target)) {
            stop();
            target = canonical;
            attempts = 0;
            stallTicks = 0;
            bestDist = Double.MAX_VALUE;
            failure = "";
        }

        // Уже стоим так, что сундук кликается — идти больше некуда
        if (openHit(canonical) != null) {
            stop();
            return Status.ARRIVED;
        }

        IBaritone b = baritone();
        if (b == null) {
            failure = "Baritone недоступен";
            return Status.UNREACHABLE;
        }

        sinceIssue++;
        boolean precise = regionLoaded(canonical);
        boolean phaseChanged = issued != null && precise != issuedPrecise;
        boolean idle = issued != null
            && !phaseChanged
            && sinceIssue > 20
            && !b.getPathingBehavior().isPathing()
            && !b.getCustomGoalProcess().isActive();

        if (issued == null || phaseChanged || idle) {
            if (phaseChanged) attempts = 0;

            if (idle) {
                BlockPos feet = net.minecraft.client.MinecraftClient.getInstance().player.getBlockPos();
                // Baritone считает цель достигнутой, но кликнуть нечем —
                // это не «не дошли», это «встали не туда»
                if (issuedPrecise && issued.isInGoal(feet.getX(), feet.getY(), feet.getZ())) {
                    failure = "дошли, но ни одна точка рядом не видит сундук";
                    return Status.UNREACHABLE;
                }
                if (++attempts > MAX_ATTEMPTS) {
                    failure = "Baritone не нашёл путь";
                    return Status.UNREACHABLE;
                }
            }

            // Чанк не загружен — точек стояния не знаем, идём «примерно»
            Goal goal = precise ? preciseGoal(canonical) : new GoalNear(canonical, SPOT_RADIUS - 1);
            if (goal == null) {
                failure = "нет места, откуда сундук достать";
                return Status.UNREACHABLE;
            }

            issued = goal;
            issuedPrecise = precise;
            sinceIssue = 0;
            // setGoal, а не setGoalAndPath: расчёт пути идёт в пуле Baritone,
            // render-тред не мёрзнет. Простой/провал ловят idle/attempts/таймауты.
            b.getCustomGoalProcess().setGoal(goal);
        }

        double dist = net.minecraft.client.MinecraftClient.getInstance().player.getEntityPos().distanceTo(Vec3d.ofCenter(canonical));
        if (dist < bestDist - 0.5) {
            bestDist = dist;
            stallTicks = 0;
        } else if (++stallTicks > STALL_TICKS) {
            failure = "перестал приближаться";
            return Status.UNREACHABLE;
        }

        return Status.TRAVELLING;
    }

    public void stop() {
        target = null;
        issued = null;
        sinceIssue = 0;
        attempts = 0;
        stallTicks = 0;
        bestDist = Double.MAX_VALUE;

        try {
            IBaritone b = baritone();
            if (b != null && (b.getPathingBehavior().isPathing() || b.getCustomGoalProcess().isActive())) {
                b.getPathingBehavior().cancelEverything();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Лучший видимый сундук из текущей точки, либо null. */
    @Nullable
    public BlockHitResult openHit(BlockPos canonical) {
        if (net.minecraft.client.MinecraftClient.getInstance().player == null || net.minecraft.client.MinecraftClient.getInstance().world == null || canonical == null) return null;

        Vec3d eye = net.minecraft.client.MinecraftClient.getInstance().player.getEyePos();
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;

        for (BlockPos half : halves(canonical)) {
            double d = cubeDistance(eye, half);
            if (d > 4.2 || d >= bestD) continue;
            BlockHitResult hit = trace(eye, half);
            if (hit != null) {
                best = hit;
                bestD = d;
            }
        }

        return best;
    }

    /** На сортировке ломать и ставить блоки нельзя — это заметно на античите. */
    public void setSafePathing(boolean on) {
        if (!on) {
            restorePathing();
            return;
        }

        try {
            Settings s = BaritoneAPI.getSettings();
            if (savedAllowBreak == null) {
                savedAllowBreak = (Boolean) s.allowBreak.value;
                savedAllowPlace = (Boolean) s.allowPlace.value;
            }
            s.allowBreak.value = false;
            s.allowPlace.value = false;
        } catch (Throwable ignored) {
        }
    }

    public void restorePathing() {
        if (savedAllowBreak == null) return;

        try {
            Settings s = BaritoneAPI.getSettings();
            s.allowBreak.value = savedAllowBreak;
            s.allowPlace.value = savedAllowPlace;
        } catch (Throwable ignored) {
        }

        savedAllowBreak = null;
        savedAllowPlace = null;
    }

    @Nullable
    private Goal preciseGoal(BlockPos canonical) {
        List<BlockPos> spots = standSpots(canonical);
        if (spots.isEmpty()) return null;
        if (spots.size() == 1) return new GoalBlock(spots.get(0));

        Goal[] goals = new Goal[spots.size()];
        for (int i = 0; i < goals.length; i++) goals[i] = new GoalBlock(spots.get(i));
        return new GoalComposite(goals);
    }

    /** Все места, с которых сундук виден, ближайшие к игроку — не дальше MAX_SPOTS. */
    private List<BlockPos> standSpots(BlockPos canonical) {
        List<BlockPos> halves = halves(canonical);
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos half : halves) {
            for (int dy = -SPOT_DOWN; dy <= SPOT_UP; dy++) {
                for (int dx = -SPOT_RADIUS; dx <= SPOT_RADIUS; dx++) {
                    for (int dz = -SPOT_RADIUS; dz <= SPOT_RADIUS; dz++) {
                        BlockPos p = half.add(dx, dy, dz);
                        if (seen.add(p) && standable(p) && inReach(eyeAt(p), halves)) {
                            candidates.add(p.toImmutable());
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return List.of();

        Vec3d me = net.minecraft.client.MinecraftClient.getInstance().player.getEntityPos();
        candidates.sort(Comparator.comparingDouble(px -> me.squaredDistanceTo(Vec3d.ofCenter(px))));

        List<BlockPos> visible = new ArrayList<>();
        int traced = 0;
        for (BlockPos p : candidates) {
            // Проверка видимости — это луч: дорого, поэтому ограничиваем
            if (visible.size() >= MAX_SPOTS || traced >= MAX_TRACES) break;
            traced++;

            Vec3d eye = eyeAt(p);
            for (BlockPos half : halves) {
                if (cubeDistance(eye, half) <= SPOT_REACH && trace(eye, half) != null) {
                    visible.add(p);
                    break;
                }
            }
        }

        if (!visible.isEmpty()) return visible;
        return candidates.size() <= MAX_SPOTS ? candidates : candidates.subList(0, MAX_SPOTS);
    }

    private static List<BlockPos> halves(BlockPos canonical) {
        BlockPos other = ContainerGeometry.otherHalf(net.minecraft.client.MinecraftClient.getInstance().world, canonical);
        return other == null ? List.of(canonical) : List.of(canonical, other);
    }

    private static Vec3d eyeAt(BlockPos feet) {
        return new Vec3d(feet.getX() + 0.5, feet.getY() + EYE_HEIGHT, feet.getZ() + 0.5);
    }

    private static boolean inReach(Vec3d eye, List<BlockPos> halves) {
        for (BlockPos half : halves) if (cubeDistance(eye, half) <= SPOT_REACH) return true;
        return false;
    }

    /** Расстояние от глаза до кубика блока, а не до его центра. */
    private static double cubeDistance(Vec3d eye, BlockPos pos) {
        double dx = Math.max(Math.max(pos.getX() - eye.x, 0.0), eye.x - (pos.getX() + 1));
        double dy = Math.max(Math.max(pos.getY() - eye.y, 0.0), eye.y - (pos.getY() + 1));
        double dz = Math.max(Math.max(pos.getZ() - eye.z, 0.0), eye.z - (pos.getZ() + 1));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Виден ли блок из точки глаза: луч в ближайшую точку его контура. */
    @Nullable
    private static BlockHitResult trace(Vec3d eye, BlockPos pos) {
        VoxelShape shape = net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(pos).getOutlineShape(net.minecraft.client.MinecraftClient.getInstance().world, pos);
        Box box = shape.isEmpty() ? new Box(pos) : shape.getBoundingBox();
        Vec3d aim = new Vec3d(clamp(eye.x, box.minX, box.maxX), clamp(eye.y, box.minY, box.maxY), clamp(eye.z, box.minZ, box.maxZ));
        Vec3d dir = aim.subtract(eye);
        double len = dir.length();
        if (len < 1.0E-4) return null;

        Vec3d end = aim.add(dir.multiply(0.1 / len));
        BlockHitResult hit = net.minecraft.client.MinecraftClient.getInstance().world.raycast(
            new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, net.minecraft.client.MinecraftClient.getInstance().player));
        return hit.getType() == Type.BLOCK && hit.getBlockPos().equals(pos) ? hit : null;
    }

    /** Можно ли стоять: ноги и голова свободны, под ногами опора, рядом безопасно. */
    private static boolean standable(BlockPos pos) {
        if (!net.minecraft.client.MinecraftClient.getInstance().world.isPosLoaded(pos)) return false;

        BlockPos above = pos.up();
        BlockPos below = pos.down();
        BlockState feet = net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(pos);
        BlockState head = net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(above);
        BlockState floor = net.minecraft.client.MinecraftClient.getInstance().world.getBlockState(below);

        if (!feet.getCollisionShape(net.minecraft.client.MinecraftClient.getInstance().world, pos).isEmpty()) return false;
        if (!head.getCollisionShape(net.minecraft.client.MinecraftClient.getInstance().world, above).isEmpty()) return false;
        if (floor.getCollisionShape(net.minecraft.client.MinecraftClient.getInstance().world, below).isEmpty()) return false;

        return !hazard(feet) && !hazard(head) && !hazard(floor);
    }

    private static boolean hazard(BlockState state) {
        return state.getFluidState().isIn(FluidTags.LAVA)
            || state.isIn(BlockTags.FIRE)
            || state.isOf(Blocks.MAGMA_BLOCK)
            || state.isOf(Blocks.CACTUS)
            || state.isOf(Blocks.POWDER_SNOW)
            || state.isOf(Blocks.SWEET_BERRY_BUSH)
            || state.isOf(Blocks.WITHER_ROSE);
    }

    /** Загружен ли весь квадрат, в котором ищем точки стояния. */
    private static boolean regionLoaded(BlockPos pos) {
        return net.minecraft.client.MinecraftClient.getInstance().world.isPosLoaded(pos)
            && net.minecraft.client.MinecraftClient.getInstance().world.isPosLoaded(pos.add(SPOT_RADIUS, 0, SPOT_RADIUS))
            && net.minecraft.client.MinecraftClient.getInstance().world.isPosLoaded(pos.add(SPOT_RADIUS, 0, -SPOT_RADIUS))
            && net.minecraft.client.MinecraftClient.getInstance().world.isPosLoaded(pos.add(-SPOT_RADIUS, 0, SPOT_RADIUS))
            && net.minecraft.client.MinecraftClient.getInstance().world.isPosLoaded(pos.add(-SPOT_RADIUS, 0, -SPOT_RADIUS));
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    @Nullable
    private static IBaritone baritone() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public enum Status {
        TRAVELLING,
        ARRIVED,
        UNREACHABLE
    }
}
