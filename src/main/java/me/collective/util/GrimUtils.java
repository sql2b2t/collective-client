package me.collective.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Вспомогательные проверки для GrimAC.
 * Расчет расстояний ведется по всем возможным высотам глаз игрока.
 */
public final class GrimUtils {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private GrimUtils() {
    }

    public static boolean inputClickClean() {
        if (mc.player == null) return false;

        PlayerInput in = mc.player.getLastPlayerInput();
        return !in.forward() && !in.backward() && !in.left() && !in.right()
            && !in.jump() && !in.sneak();
    }

    public static boolean inventoryClickClean() {
        return inputClickClean() && mc.player != null && !mc.player.isSprinting();
    }

    public static double[] getPossibleEyeHeights(PlayerEntity player) {
        float scale = (float) player.getAttributeValue(EntityAttributes.SCALE);

        double standing = 1.62 * scale;
        double sneaking = 1.27 * scale;
        double swimming = 0.4 * scale;

        EntityPose pose = player.getPose();
        if (pose == EntityPose.GLIDING || pose == EntityPose.SPIN_ATTACK || pose == EntityPose.SWIMMING) {
            return new double[]{swimming, standing, sneaking};
        }

        if (pose == EntityPose.CROUCHING) {
            return new double[]{sneaking, standing, swimming};
        }

        return new double[]{standing, sneaking, swimming};
    }

    public static double closestEyeDistanceSqTo(PlayerEntity player, BlockPos pos) {
        return closestEyeDistanceSqTo(player, Vec3d.ofCenter(pos));
    }

    public static double closestEyeDistanceSqTo(PlayerEntity player, Vec3d target) {
        Vec3d base = player.getEntityPos();
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            double d = base.add(0.0, h, 0.0).squaredDistanceTo(target);
            if (d >= best) continue;

            best = d;
        }

        return best;
    }

    public static double closestEyeDistanceSqTo(PlayerEntity player, Box box) {
        Vec3d base = player.getEntityPos();
        double best = Double.MAX_VALUE;

        for (double h : getPossibleEyeHeights(player)) {
            Vec3d eye = base.add(0.0, h, 0.0);

            double d = eye.squaredDistanceTo(
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
        if (mc.world == null) return 0;

        PendingUpdateManager manager = mc.world.getPendingUpdateManager();
        int seq = manager.incrementSequence().getSequence();
        manager.close();
        return seq;
    }
}
