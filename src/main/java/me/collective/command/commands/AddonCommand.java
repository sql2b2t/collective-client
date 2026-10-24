package me.collective.command.commands;

import me.collective.addon.AddonCompiler;
import me.collective.addon.AddonManager;
import me.collective.addon.CollectiveAddon;
import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.module.Module;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AddonCommand extends Command {
    public AddonCommand() {
        super("addon", "Управление аддонами клиента.", "addons", "аддоны");
    }

    @Override
    public void execute(String[] args) {
        AddonManager manager = AddonManager.getInstance();

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            printList(manager);
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            manager.reload();
            return;
        }

        if (args[0].equalsIgnoreCase("load")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .addon load <имя.jar | путь к jar>"));
                return;
            }
            File file = resolveJar(manager, args[1]);
            if (file == null || !file.isFile()) {
                ChatHelper.sendMessage(Component.literal("§cJar-файл не найден: §e" + args[1]));
                return;
            }
            boolean ok = manager.loadJarAddon(file);
            if (ok) {
                ChatHelper.sendMessage(Component.literal("§a[Collective] Аддон загружен из §f" + file.getName()));
            } else {
                String reason = manager.getLoadFailures().get(file.getName());
                ChatHelper.sendMessage(Component.literal("§c[Collective] Не удалось загрузить §e" + file.getName()
                        + (reason == null ? "" : " §7(§c" + reason + "§7)")));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("unload")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .addon unload <имя аддона>"));
                return;
            }
            boolean ok = manager.unloadAddon(args[1]);
            if (ok) {
                ChatHelper.sendMessage(Component.literal("§a[Collective] Аддон §e" + args[1] + " §aвыгружен!"));
            } else {
                ChatHelper.sendMessage(Component.literal("§c[Collective] Загруженный аддон не найден: §e" + args[1]));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("info")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .addon info <имя аддона>"));
                return;
            }
            CollectiveAddon addon = manager.getAddon(args[1]);
            if (addon == null) {
                ChatHelper.sendMessage(Component.literal("§c[Collective] Аддон не найден: §e" + args[1]));
                return;
            }
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Аддон §e" + addon.getName() + " §7v" + addon.getVersion()));
            ChatHelper.sendMessage(Component.literal(" §7Автор: §f" + addon.getAuthor()));
            if (addon.getDescription() != null && !addon.getDescription().isBlank()) {
                ChatHelper.sendMessage(Component.literal(" §7Описание: §f" + addon.getDescription()));
            }
            if (addon.getWebsite() != null && !addon.getWebsite().isBlank()) {
                ChatHelper.sendMessage(Component.literal(" §7Сайт: §f" + addon.getWebsite()));
            }

            List<Module> modules = addon.getRegisteredModules();
            ChatHelper.sendMessage(Component.literal(" §7Модули (§f" + modules.size() + "§7): §f"
                    + (modules.isEmpty() ? "нет" : String.join(", ", modules.stream().map(Module::getName).toList()))));

            List<me.collective.command.Command> commands = addon.getRegisteredCommands();
            ChatHelper.sendMessage(Component.literal(" §7Команды (§f" + commands.size() + "§7): §f"
                    + (commands.isEmpty() ? "нет" : String.join(", ", commands.stream().map(me.collective.command.Command::getName).toList()))));
            return;
        }

        if (args[0].equalsIgnoreCase("sources")) {
            List<String> sources = manager.listAddonSources();
            if (sources.isEmpty()) {
                ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] В §fcollective/addons-src §7нет исходников."));
                return;
            }
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Исходники аддонов (§f" + sources.size() + "§7):"));
            for (String name : sources) {
                ChatHelper.sendMessage(Component.literal(" §8- §e" + name));
            }
            ChatHelper.sendMessage(Component.literal("§7Собрать: §b.addon build <имя без .java>"));
            return;
        }

        if (args[0].equalsIgnoreCase("build")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .addon build <имя исходника>"));
                return;
            }
            Path source = manager.getAddonSource(args[1]);
            if (source == null) {
                ChatHelper.sendMessage(Component.literal("§cИсходник не найден в collective/addons-src: §e" + args[1]));
                return;
            }
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Собираю §f" + source.getFileName() + "§7..."));
            String baseName = args[1].toLowerCase().endsWith(".java")
                    ? args[1].substring(0, args[1].length() - 5)
                    : args[1];
            AddonCompiler.BuildResult result = AddonCompiler.build(source, baseName);
            for (String line : result.message().split("\n")) {
                ChatHelper.sendMessage(Component.literal((result.success() ? "§a" : "§c") + line));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("folder") || args[0].equalsIgnoreCase("open")) {
            File folder = manager.getAddonsFolder().toFile();
            try {
                if (!folder.exists()) folder.mkdirs();
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(folder);
                    ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Открыта папка аддонов: §f" + folder.getAbsolutePath()));
                } else {
                    ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Папка аддонов: §f" + folder.getAbsolutePath()));
                }
            } catch (Exception e) {
                ChatHelper.sendMessage(Component.literal("§cОшибка открытия папки: " + e.getMessage()));
            }
            return;
        }

        ChatHelper.sendMessage(Component.literal("§7Использование: §b.addon <list | load <jar> | unload <имя> | info <имя> | sources | build <имя> | reload | folder>"));
    }

    private void printList(AddonManager manager) {
        List<CollectiveAddon> list = manager.getAddons();
        Map<String, String> failures = manager.getLoadFailures();

        if (list.isEmpty() && failures.isEmpty()) {
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Установленных аддонов нет. Поместите .jar в папку §fcollective/addons/"));
            return;
        }

        if (!list.isEmpty()) {
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Установленные аддоны (§f" + list.size() + "§7):"));
            for (CollectiveAddon a : list) {
                ChatHelper.sendMessage(Component.literal(" §8- §e" + a.getName() + " §7v" + a.getVersion() + " §8(автор: §f" + a.getAuthor() + "§8) §7" + a.getDescription()));
            }
        }

        if (!failures.isEmpty()) {
            ChatHelper.sendMessage(Component.literal("§7Ошибки загрузки аддонов:"));
            for (Map.Entry<String, String> e : failures.entrySet()) {
                ChatHelper.sendMessage(Component.literal(" §8- §c✖ §e" + e.getKey() + " §7" + e.getValue()));
            }
        }
    }

    private File resolveJar(AddonManager manager, String arg) {
        File direct = new File(arg);
        if (direct.isFile()) return direct;

        File inFolder = manager.getAddonsFolder().resolve(arg.endsWith(".jar") ? arg : arg + ".jar").toFile();
        return inFolder.isFile() ? inFolder : null;
    }
}