package me.collective.util;

import net.minecraft.client.Minecraft;
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
        return pos.add(yawToDirection(yaw).scale(distance));
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

        double projectionLength = directionVec.dot(direction) / direction.lengthSqr();
        Vec3 projection = direction.scale(projectionLength);
        Vec3 perp = directionVec.subtract(projection);

        return perp.length();
    }

    public static int firework(net.minecraft.client.Minecraft mc, boolean elytraRequired) {
        if (mc.player == null || mc.gameMode == null) return -1;

        int elytraSwapSlot = -1;

        if (elytraRequired && !mc.player.getInventory().getItem(38).is(net.minecraft.world.item.Items.ELYTRA)) {
            FindItemResult elytra = InventoryUtil.findInHotbar(net.minecraft.world.item.Items.ELYTRA);
            if (!elytra.found()) return -1;

            elytraSwapSlot = elytra.slot();

            InventoryUtil.swap(elytra.slot(), true);
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            InventoryUtil.swapBack();

            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                mc.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }

        FindItemResult rocket = InventoryUtil.findInHotbarOrOffhand(net.minecraft.world.item.Items.FIREWORK_ROCKET);

        if (rocket.found()) {
            if (rocket.isOffhand()) {
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.OFF_HAND);
                Utils.swingHand(net.minecraft.world.InteractionHand.OFF_HAND);
            } else {
                InventoryUtil.swap(rocket.slot(), true);
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                Utils.swingHand(net.minecraft.world.InteractionHand.MAIN_HAND);
                InventoryUtil.swapBack();
            }

            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }

        int movedSlot = -1;

        for (int n = 9; n < mc.player.getInventory().getNonEquipmentItems().size(); n++) {
            net.minecraft.world.item.Item item = mc.player.getInventory().getItem(n).getItem();
            if (item != net.minecraft.world.item.Items.FIREWORK_ROCKET) continue;

            if (!GrimUtils.inventoryClickClean()) return -2;

            InventoryUtil.move().from(n).to(mc.player.getInventory().getSelectedSlot());
            movedSlot = n;
            break;
        }

        if (movedSlot != -1) {
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            Utils.swingHand(net.minecraft.world.InteractionHand.MAIN_HAND);

            InventoryUtil.move().from(mc.player.getInventory().getSelectedSlot()).to(movedSlot);

            return elytraSwapSlot != -1 ? elytraSwapSlot : 200;
        }

        return -1;
    }

    public static void setPressed(net.minecraft.client.KeyMapping key, boolean pressed) {
        key.setDown(pressed);
    }

    public static void swingHand(net.minecraft.world.InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.swing(hand, mc.player.getItemInHand(hand).getAttackAnimation(), false);
            if (mc.getConnection() != null) {
                mc.getConnection().send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            }
        }
    }

    public static void swingHand(net.minecraft.client.player.LocalPlayer player, net.minecraft.world.InteractionHand hand) {
        if (player != null) {
            player.swing(hand, player.getItemInHand(hand).getAttackAnimation(), false);
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                mc.getConnection().send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
            }
        }
    }

    public static boolean isPressed(int key) {
        return KeyHelper.isKeyDown(key);
    }
}