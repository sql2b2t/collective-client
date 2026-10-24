package me.collective.command.commands;

import me.collective.command.ChatHelper;
import me.collective.command.Command;
import me.collective.mcp.McpServer;
import me.collective.module.client.McpModule;
import net.minecraft.network.chat.Component;

public class McpCommand extends Command {
    public McpCommand() {
        super("mcp", "Управление MCP-сервером — мостом для AI-агентов (Claude, Cursor, opencode).", ".mcp <status|on|off|port <n>|config|copy|code <on|off>>", "мсп");
    }

    @Override
    public void execute(String[] args) {
        McpModule module = McpModule.INSTANCE;
        if (module == null) {
            ChatHelper.sendMessage(Component.literal("§c[Collective] Модуль McpServer не зарегистрирован."));
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            McpServer server = module.getServer();
            boolean running = module.isToggled() && server != null && server.isRunning();
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] MCP-сервер: " + (running ? "§aработает" : "§cвыключен")));
            if (running) {
                ChatHelper.sendMessage(Component.literal(" §7Адрес: §fhttp://127.0.0.1:" + server.getPort() + "/mcp"));
                ChatHelper.sendMessage(Component.literal(" §7Инструментов: §f" + server.getToolCount()));
                ChatHelper.sendMessage(Component.literal(" §7Выполнение кода: " + (server.isCodeExecutionAllowed() ? "§cразрешено до перезапуска" : "§aвыключено")));
            } else {
                ChatHelper.sendMessage(Component.literal(" §7Включить: §b.mcp on§7, порт: §b.mcp port <1024-65535>"));
            }
            ChatHelper.sendMessage(Component.literal(" §7Конфиг для AI-клиента: §b.mcp config §7или §b.mcp copy"));
            return;
        }

        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("enable") || args[0].equalsIgnoreCase("вкл")) {
            if (module.isToggled()) {
                ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] MCP-сервер уже включён."));
            } else {
                module.setToggled(true);
            }
            return;
        }

        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("disable") || args[0].equalsIgnoreCase("выкл")) {
            if (!module.isToggled()) {
                ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] MCP-сервер уже выключен."));
            } else {
                module.setToggled(false);
            }
            return;
        }

        if (args[0].equalsIgnoreCase("port")) {
            if (args.length < 2) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .mcp port <1024-65535>"));
                return;
            }
            int newPort;
            try {
                newPort = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                ChatHelper.sendMessage(Component.literal("§cПорт должен быть числом: §e" + args[1]));
                return;
            }
            if (newPort < 1024 || newPort > 65535) {
                ChatHelper.sendMessage(Component.literal("§cПорт должен быть в диапазоне 1024-65535."));
                return;
            }

            boolean wasRunning = module.isToggled();
            if (wasRunning) module.setToggled(false);
            module.port.setValue((double) newPort);
            if (wasRunning) module.setToggled(true);
            ChatHelper.sendMessage(Component.literal("§a[Collective] Порт MCP изменён на §f" + newPort + "§a."
                    + (wasRunning ? " Сервер перезапущен." : "")));
            return;
        }

        if (args[0].equalsIgnoreCase("code")) {
            McpServer server = module.getServer();
            if (server == null || !server.isRunning()) {
                ChatHelper.sendMessage(Component.literal("§cСначала включите MCP: .mcp on"));
                return;
            }
            if (args.length != 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
                ChatHelper.sendMessage(Component.literal("§cИспользование: .mcp code <on|off>. Разрешает доверенному AI-клиенту выполнение произвольного кода."));
                return;
            }
            boolean allowed = args[1].equalsIgnoreCase("on");
            server.setCodeExecutionAllowed(allowed);
            ChatHelper.sendMessage(Component.literal(allowed
                    ? "§cMCP: выполнение кода разрешено до остановки сервера. Используйте только с доверенным AI-клиентом!"
                    : "§aMCP: выполнение кода запрещено."));
            return;
        }

        if (args[0].equalsIgnoreCase("config")) {
            int port = currentPort(module);
            ChatHelper.sendMessage(Component.literal("§7[§bCollective§7] Конфиг MCP для AI-клиента (Claude Desktop / Cursor / opencode):"));
            for (String line : McpServer.buildClientConfig(port).split("\n")) {
                ChatHelper.sendMessage(Component.literal("§f" + line));
            }
            ChatHelper.sendMessage(Component.literal("§7Это шаблон без секрета. Токен: ~/.config/collective-private/mcp.token (0600); подставьте только в защищённой конфигурации AI-клиента."));
            ChatHelper.sendMessage(Component.literal("§7Скопировать шаблон в буфер: §b.mcp copy"));
            return;
        }

        if (args[0].equalsIgnoreCase("copy")) {
            int port = currentPort(module);
            mc.keyboardHandler.setClipboard(McpServer.buildClientConfig(port));
            ChatHelper.sendMessage(Component.literal("§a[Collective] Шаблон MCP без секрета скопирован. Подставьте локальный токен в защищённой конфигурации AI-клиента."));
            return;
        }

        ChatHelper.sendMessage(Component.literal("§7Использование: §b.mcp <status | on | off | port <n> | config | copy | code <on|off>>"));
    }

    private static int currentPort(McpModule module) {
        McpServer server = module.getServer();
        return server != null && server.isRunning() ? server.getPort() : module.port.getIntValue();
    }
}