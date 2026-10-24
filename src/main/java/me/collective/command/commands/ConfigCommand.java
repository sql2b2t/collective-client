package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.config.ConfigManager;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", "Сохранение и загрузка конфигов клиента.", ".config <save|load|list|delete|folder> [имя]", "cfg", "конфиг");
    }

    @Override
    public void execute(String[] args) {
        ConfigManager cfg = ConfigManager.getInstance();

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            List<String> names = cfg.list();
            if (names.isEmpty()) {
                ChatHelper.sendMessage(Component.literal("§7[§dCollective§7] Нет сохранённых конфигов."));
                return;
            }
            ChatHelper.sendMessage(Component.literal("§7[§dCollective§7] Конфиги:"));
            for (String n : names) {
                ChatHelper.sendMessage(Component.literal(" §8- §e" + n));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("save")) {
            String name = args.length > 1 ? args[1] : ConfigManager.DEFAULT_NAME;
            if (cfg.save(name)) {
                ChatHelper.sendMessage(Component.literal("§a[Collective] Конфиг §e" + name + " §aсохранён!"));
            } else {
                ChatHelper.sendMessage(Component.literal("§c[Collective] Не удалось сохранить конфиг §e" + name));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("load")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .config load <имя>"));
                return;
            }
            if (cfg.load(args[1])) {
                ChatHelper.sendMessage(Component.literal("§a[Collective] Конфиг §e" + args[1] + " §aзагружен!"));
            } else {
                ChatHelper.sendMessage(Component.literal("§c[Collective] Конфиг не найден: §e" + args[1]));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .config delete <имя>"));
                return;
            }
            File f = cfg.fileOf(args[1]);
            if (f.isFile() && f.delete()) {
                ChatHelper.sendMessage(Component.literal("§a[Collective] Конфиг §e" + args[1] + " §aудалён!"));
            } else {
                ChatHelper.sendMessage(Component.literal("§c[Collective] Конфиг не найден: §e" + args[1]));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("folder") || args[0].equalsIgnoreCase("open")) {
            File folder = cfg.getConfigsFolder().toFile();
            try {
                if (!folder.exists()) folder.mkdirs();
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(folder);
                    ChatHelper.sendMessage(Component.literal("§7[§dCollective§7] Открыта папка конфигов: §f" + folder.getAbsolutePath()));
                } else {
                    ChatHelper.sendMessage(Component.literal("§7[§dCollective§7] Папка конфигов: §f" + folder.getAbsolutePath()));
                }
            } catch (Exception e) {
                ChatHelper.sendMessage(Component.literal("§cОшибка открытия папки: " + e.getMessage()));
            }
            return;
        }

        ChatHelper.sendMessage(Component.literal("§7Использование: §b.config <save [имя] | load <имя> | list | delete <имя> | folder>"));
    }
}