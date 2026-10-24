package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.ModuleManager;
import me.collective.module.player.StashMover;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;

public class MoverCommand extends Command {
    public MoverCommand() {
        super("перенос", "Зоны для переноса вещей между стешами.", ".перенос [вход|выход] [1|2|очистить]");
    }

    private StashMover mover() {
        return ModuleManager.INSTANCE.getModule(StashMover.class);
    }

    @Override
    public void execute(String[] args) {
        StashMover m = mover();
        if (m == null) {
            ChatHelper.sendMessage(Component.literal("Модуль StashMover не найден.").styled(st -> st.withColor(ChatFormatting.RED)));
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("помощь")) {
            ChatHelper.sendMessage(Component.literal("§6=== Настройка StashMover ==="));
            ChatHelper.sendMessage(Component.literal("§e.перенос вход 1§7 / §e2§7 — углы зоны входа (смотри на блок)"));
            ChatHelper.sendMessage(Component.literal("§e.перенос выход 1§7 / §e2§7 — углы зоны выхода"));
            ChatHelper.sendMessage(Component.literal("§e.перенос вход очистить§7 — сбросить зоны"));
            ChatHelper.sendMessage(Component.literal("§e.перенос старт§7 — включить или выключить перенос"));
            return;
        }

        String zone = args[0].toLowerCase();
        if (zone.equals("старт")) {
            m.toggle();
            return;
        }

        if (!zone.equals("вход") && !zone.equals("выход")) {
            ChatHelper.sendMessage(Component.literal("Укажите «вход» или «выход». Например: .перенос вход 1").styled(st -> st.withColor(ChatFormatting.RED)));
            return;
        }

        if (args.length < 2) {
            ChatHelper.sendMessage(Component.literal("Укажите номер угла (1 или 2) или «очистить»").styled(st -> st.withColor(ChatFormatting.RED)));
            return;
        }

        if (args[1].equalsIgnoreCase("очистить")) {
            m.clearAreas();
            ChatHelper.sendMessage(Component.literal("§aЗоны сброшены."));
            return;
        }

        int n;
        try {
            n = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            ChatHelper.sendMessage(Component.literal("Номер угла должен быть 1 или 2").styled(st -> st.withColor(ChatFormatting.RED)));
            return;
        }

        BlockPos pos = m.lookedAtBlock();
        if (pos == null) {
            ChatHelper.sendMessage(Component.literal("Смотри на угол и повтори.").styled(st -> st.withColor(ChatFormatting.RED)));
            return;
        }

        boolean input = zone.equals("вход");
        m.setAreaCorner(input, n, pos);
        ChatHelper.sendMessage(Component.literal("§a" + (input ? "Вход" : "Выход") + ", угол " + n + ": §f" + pos.toShortString()));
    }
}