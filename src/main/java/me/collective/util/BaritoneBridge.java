package me.collective.util;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.util.math.BlockPos;

/**
 * Изолированный мост к Baritone API.
 */
public final class BaritoneBridge {
    private BaritoneBridge() {
    }

    public static void executeCommand(String command) {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(command);
        } catch (Throwable ignored) {
        }
    }

    public static void clearGoal() {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
        } catch (Throwable ignored) {
        }
    }

    public static void setGoal(BlockPos pos) {
        try {
            // Асинхронно: setGoalAndPath считает A* на вызывающем треде и морозит игру
            setGoalAsync(pos);
        } catch (Throwable ignored) {
        }
    }

    /** Неблокирующая выдача цели (расчёт пути в пуле Baritone). */
    public static void setGoalAsync(BlockPos pos) {
        try {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(new GoalBlock(pos));
        } catch (Throwable ignored) {
        }
    }

    public static boolean hasGoal() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasElytraDestination() {
        try {
            if (me.collective.baritone.OverworldElytraProcess.getInstance() != null && me.collective.baritone.OverworldElytraProcess.getInstance().isActive()) {
                return true;
            }
            return BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().currentDestination() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isElytraProcessActive() {
        try {
            if (me.collective.baritone.OverworldElytraProcess.getInstance() != null && me.collective.baritone.OverworldElytraProcess.getInstance().isActive()) {
                return true;
            }
            return BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
