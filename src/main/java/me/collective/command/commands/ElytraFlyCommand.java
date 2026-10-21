package me.collective.command.commands;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.Goal;
import me.collective.baritone.OverworldElytraProcess;
import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.util.BaritoneHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class ElytraFlyCommand extends Command {

    public ElytraFlyCommand() {
        super("elytrafly", "Commands Baritone to fly with Elytra in Overworld using Pitch-40 mode.", ".elytrafly <x> [y] <z> | cancel | goal", "efly", "belytra");
    }

    @Override
    public void execute(String[] args) {
        // Класс OverworldElytraProcess недогружаем без Baritone (его интерфейсы
        // — из baritone.api) — сначала проверка доступности.
        if (!BaritoneHelper.isAvailable()) {
            ChatHelper.sendMessage(Text.literal("Baritone не установлен - команда недоступна").styled(s -> s.withColor(Formatting.RED)));
            return;
        }
        OverworldElytraProcess.init();
        OverworldElytraProcess process = OverworldElytraProcess.getInstance();

        if (process == null) {
            ChatHelper.sendMessage(Text.literal("Baritone не инициализирован!").styled(s -> s.withColor(Formatting.RED)));
            return;
        }

        if (args.length == 0) {
            ChatHelper.sendMessage(Text.literal("Использование: " + getSyntax()).styled(s -> s.withColor(Formatting.YELLOW)));
            return;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("cancel") || sub.equals("stop")) {
            process.cancel();
            ChatHelper.sendMessage(Text.literal("Полет на элитрах остановлен.").styled(s -> s.withColor(Formatting.RED)));
            return;
        }

        if (sub.equals("goal")) {
            try {
                Goal currentGoal = BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal();
                if (currentGoal == null) {
                    ChatHelper.sendMessage(Text.literal("В Baritone не установлена цель (#goal)!").styled(s -> s.withColor(Formatting.RED)));
                    return;
                }
                process.pathTo(currentGoal);
            } catch (Throwable t) {
                ChatHelper.sendMessage(Text.literal("Ошибка взятия цели: " + t.getMessage()).styled(s -> s.withColor(Formatting.RED)));
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
            ChatHelper.sendMessage(Text.literal("Неверные координаты! Использование: " + getSyntax()).styled(s -> s.withColor(Formatting.RED)));
        }
    }
}
