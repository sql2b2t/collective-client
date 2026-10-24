package me.collective.command.commands;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import me.collective.baritone.OverworldElytraProcess;
import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.util.BaritoneHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

public class ElytraFlyCommand extends Command {

    public ElytraFlyCommand() {
        super("elytrafly", "Commands Baritone to fly with Elytra in Overworld using Pitch-40 mode.", ".elytrafly <x> [y] <z> | cancel | goal", "efly", "belytra");
    }

    @Override
    public void execute(String[] args) {
        // Класс OverworldElytraProcess недогружаем без Baritone (его интерфейсы
        // — из baritone.api) — сначала проверка доступности.
        if (!BaritoneHelper.isAvailable()) {
            ChatHelper.sendMessage(Component.literal("Baritone не установлен - команда недоступна").withStyle(s -> s.withColor(ChatFormatting.RED)));
            return;
        }
        OverworldElytraProcess.init();
        OverworldElytraProcess process = OverworldElytraProcess.getInstance();

        if (process == null) {
            ChatHelper.sendMessage(Component.literal("Baritone не инициализирован!").withStyle(s -> s.withColor(ChatFormatting.RED)));
            return;
        }

        if (args.length == 0) {
            ChatHelper.sendMessage(Component.literal("Использование: " + getSyntax()).withStyle(s -> s.withColor(ChatFormatting.YELLOW)));
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("cancel") || sub.equals("stop")) {
            process.cancel();
            ChatHelper.sendMessage(Component.literal("Полет на элитрах остановлен.").withStyle(s -> s.withColor(ChatFormatting.RED)));
            return;
        }

        if (sub.equals("goal")) {
            try {
                Goal currentGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();
                if (currentGoal == null) {
                    ChatHelper.sendMessage(Component.literal("В Baritone не установлена цель (#goal)!").withStyle(s -> s.withColor(ChatFormatting.RED)));
                    return;
                }
                process.pathTo(currentGoal);
            } catch (Throwable t) {
                ChatHelper.sendMessage(Component.literal("Ошибка взятия цели: " + t.getMessage()).withStyle(s -> s.withColor(ChatFormatting.RED)));
            }
            return;
        }

        try {
            int x, y, z;
            if (args.length == 2) {
                x = Integer.parseInt(args[0]);
                y = 64;
                z = Integer.parseInt(args[1]);
            } else if (args.length >= 3) {
                x = Integer.parseInt(args[0]);
                y = Integer.parseInt(args[1]);
                z = Integer.parseInt(args[2]);
            } else {
                x = Integer.parseInt(args[0]);
                y = 64;
                z = 0;
            }
            process.pathTo(new BlockPos(x, y, z));
        } catch (NumberFormatException e) {
            ChatHelper.sendMessage(Component.literal("Неверные координаты! Использование: " + getSyntax()).withStyle(s -> s.withColor(ChatFormatting.RED)));
        }
    }
}