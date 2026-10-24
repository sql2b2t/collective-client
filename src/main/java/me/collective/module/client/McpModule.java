package me.collective.module.client;

import me.collective.category.Category;
import me.collective.mcp.McpServer;
import me.collective.module.Module;
import me.collective.setting.ActionSetting;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;

public class McpModule extends Module {
    public static McpModule INSTANCE;

    public final NumberSetting port = add(new NumberSetting("Port", 21114, 1024, 65535, 1));
    public final BooleanSetting logCalls = add(new BooleanSetting("Логировать вызовы", false));
    public final ActionSetting copyConfig = add(new ActionSetting("Конфиг для AI-клиента", 0)
            .process("Копировать")
            .handle(this::copyConfigToClipboard));

    private McpServer server;

    public McpModule() {
        super("McpServer", "Model Context Protocol сервер для управления клиентом AI-агентами и нейросетями.", Category.CLIENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        try {
            server = new McpServer(port.getIntValue());
            server.setCallLogger(tool -> {
                if (logCalls.getValue()) info("MCP: " + tool);
            });
            server.start();

            int actualPort = server.getPort();
            info("MCP-сервер запущен: http://127.0.0.1:%d/mcp — инструментов: %d", actualPort, server.getToolCount());
            if (actualPort != port.getIntValue()) {
                warning("Порт " + port.getIntValue() + " был занят — использован " + actualPort);
            }
            info("Подключение: .mcp config (шаблон). Bearer-токен хранится отдельно в ~/.config/collective-private/mcp.token (0600).");
            warning("Выполнение кода выключено. Только для доверенного AI-клиента: .mcp code on (до перезапуска MCP).");
        } catch (Exception e) {
            error("Не удалось запустить MCP-сервер: " + e.getMessage());
            setToggled(false);
        }
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
            server = null;
            info("MCP-сервер остановлен.");
        }
    }

    public McpServer getServer() {
        return server;
    }

    private void copyConfigToClipboard() {
        int currentPort = server != null ? server.getPort() : port.getIntValue();
        mc.keyboardHandler.setClipboard(McpServer.buildClientConfig(currentPort));
        info("Шаблон MCP скопирован (без секрета). Подставьте локальный токен из ~/.config/collective-private/mcp.token только в защищённой конфигурации AI-клиента.");
    }
}