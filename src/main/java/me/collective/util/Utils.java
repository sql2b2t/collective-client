package me.collective.util;

import net.minecraft.world.phys.Vec3;

public final class Utils {

    private Utils() {
    }

    /** Курс, округлённый до кратного 45: так «видит» движение сервер. */
    public static double angleOnAxis(double yaw) {
        if (yaw < 0.0) yaw += 360.0;
        return Math.round(yaw / 45.0) * 45.0;
    }

    public static float smoothRotation(double current, double target, double rotationScaling) {
        double difference = angleDifference(target, current);
        return (float) (current + difference * rotationScaling);
    }

    public static double angleDifference(double target, double current) {
        double diff = (target - current + 180.0) % 360.0 - 180.0;
        return diff < -180.0 ? diff + 360.0 : diff;
    }

    public static Vec3 yawToDirection(double yaw) {
        yaw = yaw * Math.PI / 180.0;

        double x = -Math.sin(yaw);
        double z = Math.cos(yaw);

        return new Vec3(x, 0.0, z);
    }

    public static Vec3 positionInDirection(Vec3 pos, double yaw, double distance) {
        return pos.add(yawToDirection(yaw).multiply(distance));
    }

    /**
     * Расстояние от точки до луча по горизонтали.
     * Высота игнорируется: вертикальная составляющая не влияет на проверку направления.
     */
    public static double distancePointToDirection(Vec3 point, Vec3 direction, Vec3 start) {
        if (start == null) start = Vec3.ZERO;

        point = point.multiply(new Vec3(1.0, 0.0, 1.0));
        start = start.multiply(new Vec3(1.0, 0.0, 1.0));
        direction = direction.multiply(new Vec3(1.0, 0.0, 1.0));

        Vec3 directionVec = point.subtract(start);

        double projectionLength = directionVec.dotProduct(direction) / direction.lengthSquared();
        Vec3 projection = direction.multiply(projectionLength);
        Vec3 perp = directionVec.subtract(projection);

        return perp.length();
    }

    public static int firework(net.minecraft.client.Minecraft mc, boolean elytraRequired) {
        if (mc.player == null || mc.interactionManager == null) return -1;

        int elytraSwapSlot = -1;

        if (elytraRequired && !mc.player.getInventory().getStack(38).isOf(net.minecraft.item.Items.ELYTRA)) {
            FindItemResult elytra = InventoryUtil.findInHotbar(net.minecraft.item.Items.ELYTRA);
            if (!elytra.found()) return -1;

            elytraSwapSlot = elytra.slot();

            InventoryUtil.swap(elytra.slot(), true);
            mc.interactionManager.interactItem(mc.player, net.minecraft.util.InteractionHand.MAIN_HAND);
            InventoryUtil.swapBack();

            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket(
                mc.player, net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        FindItemResult rocket = InventoryUtil.findInHotbarOrOffhand(net.minecraft.item.Items.FIREWORK_ROCKET);

        if (rocket.found()) {
            if (rocket.isOffhand()) {
                mc.interactionManager.interactItem(mc.player, net.minecraft.util.InteractionHand.OFF_HAND);
                mc.player.swingHand(net.minecraft.util.InteractionHand.OFF_HAND);
            } else {
                InventoryUtil.swap(rocket.slot(), true);
                mc.interactionManager.interactItem(mc.player, net.minecraft.util.InteractionHand.MAIN_HAND);
                mc.player.swingHand(net.minecraft.util.InteractionHand.MAIN_HAND);
                InventoryUtil.swapBack();
            }

            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }

        int movedSlot = -1;

        for (int n = 9; n < mc.player.getInventory().main.size(); n++) {
            net.minecraft.item.Item item = mc.player.getInventory().getStack(n).getItem();
            if (item != net.minecraft.item.Items.FIREWORK_ROCKET) continue;

            if (!GrimUtils.inventoryClickClean()) return -2;

            InventoryUtil.move().from(n).to(mc.player.getInventory().selectedSlot);
            movedSlot = n;
            break;
        }

        if (movedSlot != -1) {
            mc.interactionManager.interactItem(mc.player, net.minecraft.util.InteractionHand.MAIN_HAND);
            mc.player.swingHand(net.minecraft.util.InteractionHand.MAIN_HAND);

            InventoryUtil.move().from(mc.player.getInventory().selectedSlot).to(movedSlot);

            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }

        return -1;
    }

    public static void setPressed(net.minecraft.client.option.KeyMapping key, boolean pressed) {
        key.setPressed(pressed);
    }

    public static boolean isPressed(int glfwKey) {
        return org.lwjgl.glfw.GLFW.glfwGetKey(
            net.minecraft.client.Minecraft.getInstance().getWindow().getHandle(), glfwKey) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}