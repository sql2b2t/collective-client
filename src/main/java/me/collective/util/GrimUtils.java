package me.collective.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Вспомогательные проверки для GrimAC.
 * Расчет расстояний ведется по всем возможным высотам глаз игрока.
 */
public final class GrimUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    private GrimUtils() {
    }

    public static boolean inputClickClean() {
        if (mc.player == null) return false;

        Input in = mc.player.getLastSentInput();
        return !in.forward() && !in.backward() && !in.left() && !in.right()
            && !in.jump() && !in.shift();
    }

    public static boolean inventoryClickClean() {
        return inputClickClean() && mc.player != null && !mc.player.isSprinting();
    }

    public static double[] getPossibleEyeHeights(Player player) {
        float scale = (float) player.getAttributeValue(Attributes.SCALE);

        double standing = 1.62 * scale;
        double sneaking = 1.27 * scale;
        double swimming = 0.4 * scale;

        Pose pose = player.getPose();
        if (pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK || pose == Pose.SWIMMING) {
            return new double[]{swimming, standing, sneaking};
        }

        if (pose == Pose.CROUCHING) {
            return new double[]{sneaking, standing, swimming};
        }

        return new double[]{standing, sneaking, swimming};
    }

    public static double closestEyeDistanceSqTo(Player player, BlockPos pos) {
        return closestEyeDistanceSqTo(player, Vec3.atCenterOf(pos));
    }

    public static double closestEyeDistanceSqTo(Player player, Vec3 target) {
        Vec3 base = player.position();
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            double d = base.add(0.0, h, 0.0).distanceToSqr(target);
            if (d >= best) continue;

            best = d;
        }

        return best;
    }

    public static double closestEyeDistanceSqTo(Player player, AABB box) {
        Vec3 base = player.position();
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            Vec3 eye = base.add(0.0, h, 0.0);

            double d = eye.distanceToSqr(
                clamp(eye.x, box.minX, box.maxX),
                clamp(eye.y, box.minY, box.maxY),
                clamp(eye.z, box.minZ, box.maxZ));

            if (d >= best) continue;

            best = d;
        }

        return best;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int nextSequence() {
        if (mc.level == null) return 0;
        try (var handler = ((me.collective.mixin.ClientLevelAccessor) mc.level).collective$getBlockStatePredictionHandler().startPredicting()) {
            return handler.currentSequence();
        } catch (Exception e) {
            return 0;
        }
    }
}