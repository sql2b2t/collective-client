package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.scripting.ScriptManager;
import net.minecraft.text.Text;

import java.awt.Desktop;
import java.io.File;
import java.util.List;
import java.util.Map;

public class ScriptCommand extends Command {
    public ScriptCommand() {
        super("scripts", "Управление Kotlin и Java скриптами клиента.", ".scripts <list|load|unload|reload|examples|folder>", "script", "скрипты");
    }

    @Override
    public void execute(String[] args) {
        ScriptManager manager = ScriptManager.getInstance();

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Map<String, ?> active = manager.getActiveContexts();
            List<String> files = manager.listScriptFiles();
            Map<String, String> failures = manager.getFailures();

            if (files.isEmpty() && active.isEmpty()) {
                ChatHelper.sendMessage(Text.literal("§7[§dCollective§7] В папке §fcollective/scripts/ §7нет скриптов."));
                return;
            }

            ChatHelper.sendMessage(Text.literal("§7[§dCollective§7] Скрипты (Активно: §a" + active.size() + "§7):"));
            for (String fileName : files) {
                boolean isActive = active.containsKey(fileName);
                String status = isActive ? "§a[АКТИВЕН]§r" : "§c[ВЫКЛЮЧЕН]§r";
                ChatHelper.sendMessage(Text.literal(" §8- " + status + " §e" + fileName));
            }

            if (!failures.isEmpty()) {
                ChatHelper.sendMessage(Text.literal("§7Ошибки загрузки:"));
                for (Map.Entry<String, String> e : failures.entrySet()) {
                    ChatHelper.sendMessage(Text.literal(" §8- §c✖ §e" + e.getKey() + " §7" + e.getValue()));
                }
            }
            return;
        }

        // Загрузка отдельного скрипта
        if (args[0].equalsIgnoreCase("load")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Text.literal("§cИспользование: .scripts load <имя_скрипта>"));
                return;
            }
            String scriptName = args[1];
            boolean success = manager.loadScriptByName(scriptName);
            if (success) {
                ChatHelper.sendMessage(Text.literal("§a[Collective] Скрипт §e" + scriptName + " §aуспешно загружен и активирован!"));
            } else {
                String reason = manager.getFailures().getOrDefault(ScriptManager.normalizeScriptFileName(scriptName), "");
                ChatHelper.sendMessage(Text.literal("§c[Collective] Не удалось найти или загрузить скрипт: §e" + scriptName
                        + (reason.isEmpty() ? "" : " §7(§c" + reason + "§7)")));
            }
            return;
        }

        // Выгрузка отдельного скрипта
        if (args[0].equalsIgnoreCase("unload")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Text.literal("§cИспользование: .scripts unload <имя_скрипта>"));
                return;
            }
            String scriptName = args[1];
            boolean success = manager.unloadScript(scriptName);
            if (success) {
                ChatHelper.sendMessage(Text.literal("§a[Collective] Скрипт §e" + scriptName + " §aуспешно выгружен!"));
            } else {
                ChatHelper.sendMessage(Text.literal("§c[Collective] Активный скрипт не найден: §e" + scriptName));
            }
            return;
        }

        // Перезагрузка всех скриптов
        if (args[0].equalsIgnoreCase("reload")) {
            manager.reload();
            return;
        }

        // Дозапись примеров
        if (args[0].equalsIgnoreCase("examples") || args[0].equalsIgnoreCase("example")) {
            int written = manager.writeMissingExamples();
            if (written > 0) {
                ChatHelper.sendMessage(Text.literal("§a[Collective] Создано файлов: §f" + written + "§a. Смотрите папку §fcollective/scripts/"));
                ChatHelper.sendMessage(Text.literal("§7Подсказка: §b.scripts reload §7— подхватить новые примеры."));
            } else {
                ChatHelper.sendMessage(Text.literal("§7[Collective] Все примеры уже на месте."));
            }
            return;
        }

        // Открытие папки
        if (args[0].equalsIgnoreCase("folder") || args[0].equalsIgnoreCase("open")) {
            File folder = manager.getScriptsFolder().toFile();
            try {
                if (!folder.exists()) folder.mkdirs();
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(folder);
                    ChatHelper.sendMessage(Text.literal("§7[§dCollective§7] Открыта папка скриптов: §f" + folder.getAbsolutePath()));
                } else {
                    ChatHelper.sendMessage(Text.literal("§7[§dCollective§7] Папка скриптов: §f" + folder.getAbsolutePath()));
                }
            } catch (Exception e) {
                ChatHelper.sendMessage(Text.literal("§cОшибка открытия папки: " + e.getMessage()));
            }
            return;
        }

        ChatHelper.sendMessage(Text.literal("§7Использование: §b.scripts <list | load <name> | unload <name> | reload | examples | folder>"));
    }
}
