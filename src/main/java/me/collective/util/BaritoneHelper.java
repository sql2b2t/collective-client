package me.collective.util;

import net.minecraft.core.BlockPos;

/**
 * Безопасный доступ к Baritone, если он установлен.
 */
public final class BaritoneHelper {

    private static Boolean available;
    private static Boolean elytraProcess;

    private BaritoneHelper() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                available = true;
            } catch (Throwable e) {
                available = false;
            }
        }

        return available;
    }

    public static void runCommand(String command) {
        if (!isAvailable()) return;
        BaritoneBridge.executeCommand(command);
    }

    public static void clearGoal() {
        if (!isAvailable()) return;
        BaritoneBridge.clearGoal();
    }

    public static void setGoal(BlockPos pos) {
        if (!isAvailable()) return;
        BaritoneBridge.setGoal(pos);
    }

    public static boolean hasGoal() {
        if (!isAvailable()) return false;
        return BaritoneBridge.hasGoal();
    }

    public static boolean hasElytraDestination() {
        if (!isAvailable() || !hasElytraProcess()) return false;
        return BaritoneBridge.hasElytraDestination();
    }

    public static boolean isElytraProcessActive() {
        if (!isAvailable() || !hasElytraProcess()) return false;
        return BaritoneBridge.isElytraProcessActive();
    }

    public static boolean hasElytraProcess() {
        if (elytraProcess == null) {
            if (!isAvailable()) {
                elytraProcess = false;
            } else {
                try {
                    Class.forName("baritone.api.IBaritone").getMethod("getElytraProcess", new Class[0]);
                    elytraProcess = true;
                } catch (Throwable e) {
                    elytraProcess = false;
                }
            }
        }

        return elytraProcess;
    }
}