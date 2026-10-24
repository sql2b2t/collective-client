package me.collective.util;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;

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
            var process = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess();
            process.setGoal(new GoalBlock(pos));
            process.path();
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

    public static boolean isPathing() {
        try {
            return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
        } catch (Throwable t) {
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