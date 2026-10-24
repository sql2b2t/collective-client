package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.ModuleManager;
import me.collective.module.player.StashSorter;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class SorterCommand extends Command {
    public SorterCommand() {
        super("сорт", "Автосортировка стеша: зона, метки, запуск.", ".сорт [зона|метка|старт|стоп|пауза|продолжить|очистить|помощь]");
    }

    private StashSorter sorter() {
        return ModuleManager.INSTANCE.getModule(StashSorter.class);
    }

    @Override
    public void execute(String[] args) {
        StashSorter s = sorter();
        if (s == null) {
            ChatHelper.sendMessage(Component.literal("Модуль StashSorter не найден.").withStyle(st -> st.withColor(ChatFormatting.RED)));
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("помощь")) {
            ChatHelper.sendMessage(Component.literal("§6=== Настройка StashSorter ==="));
            ChatHelper.sendMessage(Component.literal("§e.сорт зона§7 — задать угол зоны (клик 1 и клик 2)"));
            ChatHelper.sendMessage(Component.literal("§e.сорт зона очистить§7 — удалить все зоны"));
            ChatHelper.sendMessage(Component.literal("§e.сорт метка§7 — закрепить категорию за сундуком"));
            ChatHelper.sendMessage(Component.literal("§e.сорт метка снять§7 — снять метку с сундука"));
            ChatHelper.sendMessage(Component.literal("§e.сорт старт / стоп§7 — включить/выключить сортировщик"));
            ChatHelper.sendMessage(Component.literal("§e.сорт очистить§7 — удалить все зоны"));
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "зона" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("очистить")) {
                    clearZones(s);
                } else {
                    s.setSelectionCorner();
                }
            }
            case "метка" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("снять")) {
                    s.clearLookedPin();
                } else {
                    s.pinLookedChest();
                }
            }
            case "старт" -> {
                s.startSorting();
                ChatHelper.sendMessage(Component.literal("§aСортировщик запущен."));
            }
            case "стоп" -> {
                s.stopSorting();
                ChatHelper.sendMessage(Component.literal("§cСортировщик остановлен."));
            }
            case "пауза" -> {
                s.pauseSorting();
                ChatHelper.sendMessage(Component.literal("§eСортировщик на паузе."));
            }
            case "продолжить" -> {
                s.resumeSorting();
                ChatHelper.sendMessage(Component.literal("§aСортировщик продолжен."));
            }
            case "очистить" -> clearZones(s);
            default -> ChatHelper.sendMessage(Component.literal("Неизвестная подкоманда. Используйте: .сорт помощь").withStyle(st -> st.withColor(ChatFormatting.RED)));
        }
    }

    private static void clearZones(StashSorter s) {
        int removed = s.clearZones();
        ChatHelper.sendMessage(Component.literal(removed > 0 ? "§aЗон удалено: " + removed : "§7Зон и не было."));
    }
}