package me.collective.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import me.collective.addon.AddonCompiler;
import me.collective.addon.AddonManager;
import me.collective.addon.CollectiveAddon;
import me.collective.command.Command;
import me.collective.command.CommandManager;
import me.collective.manager.FriendManager;
import me.collective.module.Module;
import me.collective.module.ModuleManager;
import me.collective.scripting.ScriptManager;
import me.collective.setting.ActionSetting;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.Keybind;
import me.collective.setting.KeybindSetting;
import me.collective.setting.ModeSetting;
import me.collective.setting.NumberSetting;
import me.collective.setting.Setting;
import me.collective.setting.StringSetting;
import me.collective.util.BaritoneBridge;
import me.collective.util.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * HTTP-сервер Model Context Protocol для AI-агентов.
 * Статeless-транспорт (streamable HTTP без SSE): каждый POST — один JSON-RPC обмен.
 * Все обращения к состоянию игры выполняются в игровом потоке (callSync).
 */
public class McpServer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SERVER_VERSION = "1.1.0";
    private static final List<String> SUPPORTED_PROTOCOLS = List.of("2025-06-18", "2025-03-26", "2024-11-05");
    private static final String LATEST_PROTOCOL = SUPPORTED_PROTOCOLS.get(0);

    private final int requestedPort;
    private volatile int port;
    private volatile boolean running;
    private HttpServer server;
    private volatile Consumer<String> callLogger;

    public McpServer(int port) {
        this.requestedPort = port;
        this.port = port;
    }

    /** Запускает сервер, при занятом порте перебирает следующие 10 портов. */
    public synchronized void start() throws IOException {
        if (running) return;
        IOException lastError = null;
        int maxPort = Math.min(65535, requestedPort + 10);

        for (int candidate = requestedPort; candidate < maxPort; candidate++) {
            try {
                HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", candidate), 0);
                s.createContext("/", new McpHttpHandler());
                s.createContext("/mcp", new McpHttpHandler());
                s.setExecutor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "Collective-MCP-Worker");
                    t.setDaemon(true);
                    return t;
                }));
                s.start();

                this.server = s;
                this.port = candidate;
                this.running = true;
                System.out.println("[Collective-MCP] Server listening on http://127.0.0.1:" + candidate + "/mcp");
                return;
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Не удалось запустить MCP-сервер");
    }

    public synchronized void stop() {
        if (!running) return;
        if (server != null) {
            server.stop(1);
            server = null;
        }
        running = false;
        System.out.println("[Collective-MCP] Server stopped.");
    }

    public boolean isRunning() {
        return running;
    }

    /** Фактический порт (может отличаться от настроенного, если тот был занят). */
    public int getPort() {
        return port;
    }

    public int getToolCount() {
        return getToolsArray().size();
    }

    /** Логгер вызовов инструментов (подключается модулем). */
    public void setCallLogger(Consumer<String> logger) {
        this.callLogger = logger;
    }

    /** Готовый JSON-конфиг для Claude Desktop / Cursor / opencode. */
    public static String buildClientConfig(int port) {
        return "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"collective\": {\n"
                + "      \"url\": \"http://127.0.0.1:" + port + "/mcp\"\n"
                + "    }\n"
                + "  }\n"
                + "}";
    }

    // =====================================================================
    //  Выполнение в игровом потоке
    // =====================================================================

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /** Выполняет действие в основном (игровом) потоке и ждёт результат. */
    private static <T> T callSync(ThrowingSupplier<T> supplier) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.isOnThread()) {
            try {
                return supplier.get();
            } catch (Exception e) {
                throw asRuntime(e);
            }
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw asRuntime(e.getCause() != null ? e.getCause() : e);
        } catch (Exception e) {
            throw new RuntimeException("Таймаут исполнения в игровом потоке (клиент занят или свёрнут)");
        }
    }

    private static RuntimeException asRuntime(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        return new RuntimeException(t);
    }

    // =====================================================================
    //  JSON-RPC обработчики
    // =====================================================================

    private class McpHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Mcp-Session-Id, Authorization");

            String method = exchange.getRequestMethod();
            try {
                if ("OPTIONS".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                    String accept = firstHeader(exchange, "Accept");
                    if (accept != null && accept.contains("text/event-stream")) {
                        // SSE-поток не поддерживаем (stateless-режим) — спецификация разрешает 405
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    respondJson(exchange, 200, getMcpManifest());
                    return;
                }

                if (!"POST".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonElement parsed;
                try {
                    parsed = JsonParser.parseString(body);
                } catch (Exception e) {
                    respondJson(exchange, 200, errorResponse(null, -32700, "Parse error: " + e.getMessage()));
                    return;
                }

                if (parsed.isJsonArray()) {
                    JsonArray batch = parsed.getAsJsonArray();
                    JsonArray responses = new JsonArray();
                    for (JsonElement el : batch) {
                        if (!el.isJsonObject()) continue;
                        JsonObject resp = handleRpc(el.getAsJsonObject());
                        if (resp != null) responses.add(resp);
                    }
                    if (responses.isEmpty()) {
                        respondAccepted(exchange);
                    } else if (responses.size() == 1 && batch.size() == 1) {
                        respondJson(exchange, 200, responses.get(0).getAsJsonObject());
                    } else {
                        respondRawJson(exchange, 200, GSON.toJson(responses));
                    }
                    return;
                }

                JsonObject response = handleRpc(parsed.getAsJsonObject());
                if (response == null) {
                    respondAccepted(exchange);
                    return;
                }
                respondJson(exchange, 200, response);
            } finally {
                exchange.close();
            }
        }

        private JsonObject handleRpc(JsonObject req) {
            if (req == null || !req.has("method")) {
                return errorResponse(null, -32600, "Invalid Request");
            }

            String method = req.get("method").getAsString();
            // Уведомления (notifications/*) ответа не получают
            if (method.startsWith("notifications/")) return null;

            JsonObject params = req.has("params") && req.get("params").isJsonObject()
                    ? req.getAsJsonObject("params") : new JsonObject();

            try {
                switch (method) {
                    case "initialize" -> {
                        return successResponse(req, handleInitialize(params));
                    }
                    case "ping" -> {
                        return successResponse(req, new JsonObject());
                    }
                    case "tools/list" -> {
                        JsonObject result = new JsonObject();
                        result.add("tools", getToolsArray());
                        return successResponse(req, result);
                    }
                    case "tools/call" -> {
                        return successResponse(req, handleToolsCall(params));
                    }
                    case "resources/list" -> {
                        return successResponse(req, handleResourcesList());
                    }
                    case "resources/templates/list" -> {
                        return successResponse(req, handleResourceTemplatesList());
                    }
                    case "resources/read" -> {
                        return successResponse(req, handleResourcesRead(params));
                    }
                    case "prompts/list" -> {
                        JsonObject result = new JsonObject();
                        result.add("prompts", new JsonArray());
                        return successResponse(req, result);
                    }
                    default -> {
                        return errorResponse(req.has("id") ? req.get("id") : null, -32601, "Method not found: " + method);
                    }
                }
            } catch (Exception e) {
                return errorResponse(req.has("id") ? req.get("id") : null, -32603, "Internal error: " + e.getMessage());
            }
        }

        private JsonObject handleToolsCall(JsonObject params) {
            String toolName = params.has("name") ? params.get("name").getAsString() : "";
            JsonObject args = params.has("arguments") && params.get("arguments").isJsonObject()
                    ? params.getAsJsonObject("arguments") : new JsonObject();

            if (callLogger != null) callLogger.accept(toolName);

            try {
                String text = callSync(() -> performTool(toolName, args));
                return toolResult(text, false);
            } catch (Throwable t) {
                String message = t.getMessage() == null ? t.toString() : t.getMessage();
                return toolResult(message, true);
            }
        }

        private JsonObject toolResult(String text, boolean isError) {
            JsonObject content = new JsonObject();
            content.addProperty("type", "text");
            content.addProperty("text", text);

            JsonArray arr = new JsonArray();
            arr.add(content);

            JsonObject res = new JsonObject();
            res.add("content", arr);
            if (isError) res.addProperty("isError", true);
            return res;
        }

        private JsonObject handleResourcesList() {
            JsonArray arr = new JsonArray();
            arr.add(resource("collective://player/state", "Состояние игрока", "Координаты, здоровье, инвентарь, сервер."));
            arr.add(resource("collective://world", "Мир", "Измерение, время, погода, сложность."));
            arr.add(resource("collective://modules", "Модули", "Список модулей и их статус."));
            arr.add(resource("collective://friends", "Друзья", "Список друзей."));
            arr.add(resource("collective://scripts", "Скрипты", "Активные скрипты, файлы и ошибки загрузки."));
            arr.add(resource("collective://addons", "Аддоны", "Загруженные аддоны и ошибки загрузки."));

            JsonObject result = new JsonObject();
            result.add("resources", arr);
            return result;
        }

        private JsonObject resource(String uri, String name, String description) {
            JsonObject o = new JsonObject();
            o.addProperty("uri", uri);
            o.addProperty("name", name);
            o.addProperty("description", description);
            o.addProperty("mimeType", "application/json");
            return o;
        }

        private JsonObject handleResourceTemplatesList() {
            JsonArray arr = new JsonArray();
            JsonObject t = new JsonObject();
            t.addProperty("uriTemplate", "collective://modules/{name}");
            t.addProperty("name", "Настройки модуля");
            t.addProperty("description", "Настройки конкретного модуля клиента (JSON).");
            t.addProperty("mimeType", "application/json");
            arr.add(t);

            JsonObject result = new JsonObject();
            result.add("resourceTemplates", arr);
            return result;
        }

        private JsonObject handleResourcesRead(JsonObject params) {
            String uri = params.has("uri") ? params.get("uri").getAsString() : "";
            String text = callSync(() -> {
                if (uri.equals("collective://player/state")) return playerStateJson();
                if (uri.equals("collective://world")) return worldInfoJson();
                if (uri.equals("collective://modules")) return modulesJson();
                if (uri.equals("collective://friends")) return friendsJson();
                if (uri.equals("collective://scripts")) return listScriptsJson();
                if (uri.equals("collective://addons")) return listAddonsJson();
                if (uri.startsWith("collective://modules/")) {
                    String name = URLDecoder.decode(uri.substring("collective://modules/".length()), StandardCharsets.UTF_8);
                    return moduleSettingsJson(name);
                }
                throw new IllegalArgumentException("Неизвестный ресурс: " + uri);
            });

            JsonObject content = new JsonObject();
            content.addProperty("uri", uri);
            content.addProperty("mimeType", "application/json");
            content.addProperty("text", text);

            JsonArray contents = new JsonArray();
            contents.add(content);

            JsonObject result = new JsonObject();
            result.add("contents", contents);
            return result;
        }

        private JsonObject getMcpManifest() {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", "Collective Client MCP Server");
            obj.addProperty("version", SERVER_VERSION);
            obj.addProperty("description", "Direct Model Context Protocol bridge for AI agents into Minecraft via Collective Client");
            obj.addProperty("endpoint", "/mcp");
            obj.addProperty("transport", "streamable-http (stateless JSON)");
            obj.add("tools", getToolsArray());
            return obj;
        }

        private JsonObject handleInitialize(JsonObject params) {
            String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : null;
            String version = requested != null && SUPPORTED_PROTOCOLS.contains(requested) ? requested : LATEST_PROTOCOL;

            JsonObject result = new JsonObject();
            result.addProperty("protocolVersion", version);

            JsonObject serverInfo = new JsonObject();
            serverInfo.addProperty("name", "collective-client-mcp");
            serverInfo.addProperty("version", SERVER_VERSION);
            result.add("serverInfo", serverInfo);

            JsonObject capabilities = new JsonObject();
            JsonObject toolsCap = new JsonObject();
            toolsCap.addProperty("listChanged", false);
            capabilities.add("tools", toolsCap);
            JsonObject resCap = new JsonObject();
            resCap.addProperty("subscribe", false);
            resCap.addProperty("listChanged", false);
            capabilities.add("resources", resCap);
            capabilities.add("prompts", new JsonObject());
            result.add("capabilities", capabilities);

            result.addProperty("instructions",
                    "Collective Client MCP — управление Minecraft-клиентом: состояние игрока и мира, модули и их настройки, команды, чат, "
                    + "Baritone, друзья, скрипты (collective/scripts, Kotlin .kts) и аддоны (исходники в collective/addons-src). "
                    + "Скрипты можно писать и сразу загружать через write_script; аддоны — write_addon_source, затем build_addon.");
            return result;
        }

        private JsonObject successResponse(JsonObject req, JsonObject result) {
            JsonObject res = new JsonObject();
            res.addProperty("jsonrpc", "2.0");
            if (req.has("id")) res.add("id", req.get("id"));
            res.add("result", result);
            return res;
        }

        private void respondJson(HttpExchange exchange, int status, JsonObject json) throws IOException {
            respondRawJson(exchange, status, GSON.toJson(json));
        }

        private void respondRawJson(HttpExchange exchange, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private void respondAccepted(HttpExchange exchange) throws IOException {
            // Ответ на уведомление: тело не требуется
            exchange.sendResponseHeaders(202, -1);
        }

        private String firstHeader(HttpExchange exchange, String name) {
            List<String> values = exchange.getRequestHeaders().get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    private static JsonObject errorResponse(JsonElement id, int code, String message) {
        JsonObject res = new JsonObject();
        res.addProperty("jsonrpc", "2.0");
        if (id != null) res.add("id", id);
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        res.add("error", err);
        return res;
    }

    // =====================================================================
    //  Описание инструментов
    // =====================================================================

    private static final class SchemaBuilder {
        private final JsonObject properties = new JsonObject();
        private final JsonArray required = new JsonArray();

        SchemaBuilder p(String name, String type, String desc) {
            JsonObject prop = new JsonObject();
            if (type != null) prop.addProperty("type", type);
            prop.addProperty("description", desc);
            properties.add(name, prop);
            return this;
        }

        SchemaBuilder req(String... names) {
            for (String n : names) required.add(n);
            return this;
        }

        JsonObject build() {
            JsonObject o = new JsonObject();
            o.addProperty("type", "object");
            if (properties.size() > 0) o.add("properties", properties);
            if (required.size() > 0) o.add("required", required);
            return o;
        }
    }

    private static JsonObject tool(String name, String description, JsonObject schema) {
        JsonObject t = new JsonObject();
        t.addProperty("name", name);
        t.addProperty("description", description);
        t.add("inputSchema", schema);
        return t;
    }

    private static JsonObject noSchema() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        return o;
    }

    private static JsonArray getToolsArray() {
        JsonArray tools = new JsonArray();

        tools.add(tool("get_player_state", "Состояние игрока: координаты, здоровье, голод, инвентарь, сервер, измерение.", noSchema()));
        tools.add(tool("get_world_info", "Информация о мире: измерение, время, погода, сложность, игроки.", noSchema()));
        tools.add(tool("get_world_entities", "Сущности рядом (игроки, мобы, предметы) с дистанцией и координатами.",
                new SchemaBuilder().p("radius", "number", "Радиус в блоках (по умолчанию 32)").build()));
        tools.add(tool("list_modules", "Список всех модулей клиента со статусом вкл/выкл и категорией.", noSchema()));
        tools.add(tool("toggle_module", "Включить/выключить или переключить модуль по имени.",
                new SchemaBuilder().p("name", "string", "Имя модуля (регистр не важен)")
                        .p("enabled", "boolean", "Желаемое состояние (необязательно; без него — переключение)")
                        .req("name").build()));
        tools.add(tool("get_module_settings", "Настройки модуля: имена, типы и текущие значения.",
                new SchemaBuilder().p("name", "string", "Имя модуля").req("name").build()));
        tools.add(tool("set_module_setting", "Изменить настройку модуля (boolean/number/mode/string/enum/цвет #RRGGBB или #AARRGGBB).",
                new SchemaBuilder().p("module", "string", "Имя модуля")
                        .p("setting", "string", "Имя настройки")
                        .p("value", null, "Новое значение")
                        .req("module", "setting", "value").build()));
        tools.add(tool("execute_command", "Выполнить внутреннюю команду клиента (например, .elytrafly 1000 2000, .bind, .toggle).",
                new SchemaBuilder().p("command", "string", "Строка команды вместе с префиксом (.)").req("command").build()));
        tools.add(tool("chat_message", "Отправить сообщение или серверную команду (/...) в чат игры.",
                new SchemaBuilder().p("message", "string", "Текст сообщения").req("message").build()));
        tools.add(tool("baritone_path", "Маршрут Baritone к координатам или отмена маршрута.",
                new SchemaBuilder().p("x", "integer", "X цели")
                        .p("y", "integer", "Y цели (по умолчанию — текущая высота)")
                        .p("z", "integer", "Z цели")
                        .p("stop", "boolean", "true — отменить текущий маршрут").build()));
        tools.add(tool("set_rotation", "Плавно/тихо выставить углы обзора игрока.",
                new SchemaBuilder().p("yaw", "number", "Yaw").p("pitch", "number", "Pitch (-90..90)").req("yaw", "pitch").build()));
        tools.add(tool("get_friends", "Список друзей клиента.", noSchema()));
        tools.add(tool("add_friend", "Добавить игрока в друзья.",
                new SchemaBuilder().p("name", "string", "Ник игрока").req("name").build()));
        tools.add(tool("remove_friend", "Убрать игрока из друзей.",
                new SchemaBuilder().p("name", "string", "Ник игрока").req("name").build()));

        tools.add(tool("list_scripts", "Скрипты: активные, файлы и ошибки загрузки.", noSchema()));
        tools.add(tool("write_script", "Записать исходник скрипта (.kts) в collective/scripts и (по умолчанию) сразу загрузить.",
                new SchemaBuilder().p("name", "string", "Имя файла (без расширения или с .kts)")
                        .p("content", "string", "Код скрипта (Kotlin DSL: module(), command(), hud(), every(), after())")
                        .p("load", "boolean", "Загрузить сразу (по умолчанию true)")
                        .req("name", "content").build()));
        tools.add(tool("read_script", "Прочитать исходник скрипта из collective/scripts.",
                new SchemaBuilder().p("name", "string", "Имя файла").req("name").build()));
        tools.add(tool("delete_script", "Удалить скрипт (с выгрузкой, если активен).",
                new SchemaBuilder().p("name", "string", "Имя файла").req("name").build()));
        tools.add(tool("load_script", "Загрузить скрипт по имени.",
                new SchemaBuilder().p("name", "string", "Имя файла").req("name").build()));
        tools.add(tool("unload_script", "Выгрузить активный скрипт по имени.",
                new SchemaBuilder().p("name", "string", "Имя скрипта").req("name").build()));
        tools.add(tool("reload_scripts", "Перезагрузить все скрипты.", noSchema()));

        tools.add(tool("list_addons", "Загруженные аддоны и ошибки загрузки.", noSchema()));
        tools.add(tool("reload_addons", "Перезагрузить все аддоны.", noSchema()));
        tools.add(tool("list_addon_sources", "Исходники аддонов в collective/addons-src.", noSchema()));
        tools.add(tool("write_addon_source", "Записать исходник аддона (.java) в collective/addons-src, затем его можно собрать через build_addon.",
                new SchemaBuilder().p("name", "string", "Имя файла = имя публичного класса (без .java)")
                        .p("content", "string", "Java-код: класс-наследник CollectiveAddon с @AddonInfo")
                        .req("name", "content").build()));
        tools.add(tool("read_addon_source", "Прочитать исходник аддона.",
                new SchemaBuilder().p("name", "string", "Имя файла").req("name").build()));
        tools.add(tool("delete_addon_source", "Удалить исходник аддона.",
                new SchemaBuilder().p("name", "string", "Имя файла").req("name").build()));
        tools.add(tool("build_addon", "Скомпилировать исходник аддона, упаковать в jar и загрузить (нужен dev-запуск клиента на JDK).",
                new SchemaBuilder().p("name", "string", "Имя исходника (без .java)").req("name").build()));

        return tools;
    }

    // =====================================================================
    //  Реализация инструментов
    // =====================================================================

    private String performTool(String name, JsonObject args) throws Exception {
        return switch (name) {
            case "get_player_state" -> playerStateJson();
            case "get_world_info" -> worldInfoJson();
            case "get_world_entities" -> entitiesJson(args);
            case "list_modules" -> modulesJson();
            case "toggle_module" -> toggleModule(args);
            case "get_module_settings" -> moduleSettingsJson(requiredString(args, "name"));
            case "set_module_setting" -> setModuleSetting(args);
            case "execute_command" -> executeCommandTool(args);
            case "chat_message" -> chatMessageTool(args);
            case "baritone_path" -> baritonePathTool(args);
            case "set_rotation" -> setRotationTool(args);
            case "get_friends" -> friendsJson();
            case "add_friend" -> friendTool(args, true);
            case "remove_friend" -> friendTool(args, false);
            case "list_scripts" -> listScriptsJson();
            case "write_script" -> writeScriptTool(args);
            case "read_script" -> readScriptTool(args);
            case "delete_script" -> deleteScriptTool(args);
            case "load_script" -> loadScriptTool(args);
            case "unload_script" -> unloadScriptTool(args);
            case "reload_scripts" -> {
                ScriptManager.getInstance().reload();
                yield "Скрипты перезагружены";
            }
            case "list_addons" -> listAddonsJson();
            case "reload_addons" -> {
                AddonManager.getInstance().reload();
                yield "Аддоны перезагружены";
            }
            case "list_addon_sources" -> {
                List<String> sources = AddonManager.getInstance().listAddonSources();
                yield sources.isEmpty() ? "Исходников нет (collective/addons-src пуст)" : String.join("\n", sources);
            }
            case "write_addon_source" -> writeAddonSourceTool(args);
            case "read_addon_source" -> readAddonSourceTool(args);
            case "delete_addon_source" -> deleteAddonSourceTool(args);
            case "build_addon" -> buildAddonTool(args);
            default -> throw new IllegalArgumentException("Неизвестный инструмент: " + name);
        };
    }

    // --- Игрок и мир ---

    private static String playerStateJson() {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject state = new JsonObject();

        if (mc.player == null) {
            state.addProperty("inGame", false);
            state.addProperty("screen", mc.currentScreen != null ? mc.currentScreen.getClass().getSimpleName() : "None");
        } else {
            state.addProperty("inGame", true);
            state.addProperty("name", mc.player.getName().getString());
            state.addProperty("health", mc.player.getHealth());
            state.addProperty("hunger", mc.player.getHungerManager().getFoodLevel());
            state.addProperty("x", mc.player.getX());
            state.addProperty("y", mc.player.getY());
            state.addProperty("z", mc.player.getZ());
            state.addProperty("yaw", mc.player.getYaw());
            state.addProperty("pitch", mc.player.getPitch());
            state.addProperty("onGround", mc.player.isOnGround());
            state.addProperty("dimension", mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "unknown");
            state.addProperty("server", mc.getCurrentServerEntry() != null
                    ? mc.getCurrentServerEntry().address
                    : (mc.isInSingleplayer() ? "Singleplayer" : "None"));

            JsonArray inv = new JsonArray();
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack is = mc.player.getInventory().getStack(i);
                if (!is.isEmpty()) {
                    JsonObject it = new JsonObject();
                    it.addProperty("slot", i);
                    it.addProperty("item", Registries.ITEM.getId(is.getItem()).toString());
                    it.addProperty("count", is.getCount());
                    inv.add(it);
                }
            }
            state.add("inventory", inv);
        }
        return GSON.toJson(state);
    }

    private static String worldInfoJson() {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject o = new JsonObject();

        if (mc.world == null || mc.player == null) {
            o.addProperty("inGame", false);
            return GSON.toJson(o);
        }

        o.addProperty("inGame", true);
        o.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
        o.addProperty("timeOfDay", mc.world.getTimeOfDay());
        o.addProperty("day", mc.world.isDay());
        o.addProperty("raining", mc.world.isRaining());
        o.addProperty("thundering", mc.world.isThundering());
        o.addProperty("difficulty", mc.world.getDifficulty().getName());
        o.addProperty("players", mc.world.getPlayers().size());
        o.addProperty("server", mc.getCurrentServerEntry() != null
                ? mc.getCurrentServerEntry().address
                : (mc.isInSingleplayer() ? "Singleplayer" : "None"));
        return GSON.toJson(o);
    }

    private static String entitiesJson(JsonObject args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double radius = args.has("radius") ? args.get("radius").getAsDouble() : 32.0;
        JsonArray arr = new JsonArray();

        if (mc.world != null && mc.player != null) {
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                double dist = mc.player.distanceTo(e);
                if (dist > radius) continue;

                JsonObject o = new JsonObject();
                o.addProperty("name", e.getName().getString());
                o.addProperty("type", e.getType().toString());
                o.addProperty("distance", Math.round(dist * 100.0) / 100.0);
                o.addProperty("x", e.getX());
                o.addProperty("y", e.getY());
                o.addProperty("z", e.getZ());
                if (e instanceof PlayerEntity pe) {
                    o.addProperty("health", pe.getHealth());
                }
                if (e instanceof ItemEntity ie) {
                    ItemStack stack = ie.getStack();
                    o.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
                    o.addProperty("count", stack.getCount());
                }
                arr.add(o);
            }
        }
        return GSON.toJson(arr);
    }

    // --- Модули ---

    private static Module findModule(String name) {
        if (name == null) return null;
        String clean = name.trim();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getName().equalsIgnoreCase(clean)) return m;
        }
        return null;
    }

    private static String modulesJson() {
        JsonArray arr = new JsonArray();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m.getName());
            o.addProperty("category", m.getCategory().getName());
            o.addProperty("enabled", m.isToggled());
            o.addProperty("description", m.getDescription());
            arr.add(o);
        }
        return GSON.toJson(arr);
    }

    private static String toggleModule(JsonObject args) {
        String name = requiredString(args, "name");
        Module target = findModule(name);
        if (target == null) throw new IllegalArgumentException("Модуль '" + name + "' не найден");

        boolean newState = args.has("enabled") && !args.get("enabled").isJsonNull()
                ? parseBoolean(args.get("enabled"))
                : !target.isToggled();

        target.setToggled(newState);
        return "Модуль '" + target.getName() + "' теперь " + (newState ? "ВКЛЮЧЁН" : "ВЫКЛЮЧЕН");
    }

    private static String moduleSettingsJson(String name) {
        Module m = findModule(name);
        if (m == null) throw new IllegalArgumentException("Модуль '" + name + "' не найден");

        JsonObject o = new JsonObject();
        o.addProperty("module", m.getName());
        o.addProperty("description", m.getDescription());
        o.addProperty("enabled", m.isToggled());

        JsonArray arr = new JsonArray();
        for (Setting<?> s : m.getSettings()) {
            arr.add(describeSetting(s));
        }
        o.add("settings", arr);
        return GSON.toJson(o);
    }

    private static JsonObject describeSetting(Setting<?> s) {
        JsonObject o = new JsonObject();
        o.addProperty("name", s.getName());
        o.addProperty("type", s.getClass().getSimpleName());
        o.addProperty("visible", s.isVisible());

        Object v = s.getValue();
        if (s instanceof ActionSetting action) {
            o.addProperty("value", "-");
            o.addProperty("button", action.getButtonText());
        } else if (s instanceof KeybindSetting keybind) {
            o.addProperty("value", keybind.getKeyName());
            o.addProperty("keyCode", keybind.getValue());
        } else if (v instanceof Boolean b) {
            o.addProperty("value", b);
        } else if (v instanceof Number n) {
            o.addProperty("value", n.doubleValue());
        } else if (v instanceof Color c) {
            o.addProperty("value", String.format("#%08X", c.getRGB()));
        } else if (v instanceof Keybind k) {
            o.addProperty("value", k.getKey());
        } else if (v instanceof List<?> list) {
            o.addProperty("count", list.size());
            if (list.size() <= 64) {
                JsonArray values = new JsonArray();
                for (Object item : list) values.add(describeValue(item));
                o.add("values", values);
            }
        } else if (v instanceof Enum<?> e) {
            o.addProperty("value", e.name());
        } else if (v == null) {
            o.addProperty("value", "-");
        } else {
            o.addProperty("value", String.valueOf(v));
        }
        return o;
    }

    private static String describeValue(Object item) {
        if (item == null) return "null";
        if (item instanceof Block b) return Registries.BLOCK.getId(b).toString();
        if (item instanceof Item i) return Registries.ITEM.getId(i).toString();
        if (item instanceof EntityType<?> t) return Registries.ENTITY_TYPE.getId(t).toString();
        return String.valueOf(item);
    }

    private static String setModuleSetting(JsonObject args) {
        String moduleName = requiredString(args, "module");
        String settingName = requiredString(args, "setting");
        JsonElement valueEl = args.get("value");
        if (valueEl == null || valueEl.isJsonNull()) {
            throw new IllegalArgumentException("Не передано значение (value)");
        }

        Module m = findModule(moduleName);
        if (m == null) throw new IllegalArgumentException("Модуль '" + moduleName + "' не найден");

        Setting<?> target = null;
        for (Setting<?> s : m.getSettings()) {
            if (s.getName().equalsIgnoreCase(settingName.trim())) {
                target = s;
                break;
            }
        }
        if (target == null) {
            StringBuilder names = new StringBuilder();
            for (Setting<?> s : m.getSettings()) {
                if (names.length() > 0) names.append(", ");
                names.append(s.getName());
            }
            throw new IllegalArgumentException("Настройка '" + settingName + "' не найдена. Доступные: " + names);
        }

        String applied = applySettingValue(target, valueEl);
        return "OK: " + m.getName() + " → " + target.getName() + " = " + applied;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String applySettingValue(Setting<?> target, JsonElement valueEl) {
        if (target instanceof ActionSetting action) {
            action.run();
            return "кнопка нажата";
        }
        if (target instanceof BooleanSetting bs) {
            boolean v = parseBoolean(valueEl);
            bs.setValue(v);
            return String.valueOf(bs.getValue());
        }
        if (target instanceof NumberSetting ns) {
            double v;
            if (valueEl.isJsonPrimitive() && valueEl.getAsJsonPrimitive().isNumber()) {
                v = valueEl.getAsDouble();
            } else {
                try {
                    v = Double.parseDouble(valueEl.getAsString().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Не число: " + valueEl.getAsString());
                }
            }
            ns.setValue(v);
            return String.valueOf(ns.getValue());
        }
        if (target instanceof ModeSetting ms) {
            String v = valueEl.getAsString();
            for (String mode : ms.getModes()) {
                if (mode.trim().equalsIgnoreCase(v.trim())) {
                    ms.setValue(mode);
                    return mode;
                }
            }
            throw new IllegalArgumentException("Недопустимый режим '" + v + "'. Доступные: " + String.join(", ", ms.getModes()));
        }
        if (target instanceof StringSetting ss) {
            ss.setValue(valueEl.getAsString());
            return ss.getValue();
        }
        if (target instanceof ColorSetting cs) {
            Color c = parseColor(valueEl.getAsString());
            cs.setValue(c);
            return String.format("#%08X", c.getRGB());
        }
        if (target instanceof EnumSetting es) {
            String v = valueEl.getAsString();
            if (!es.setByName(v)) {
                throw new IllegalArgumentException("Недопустимое значение '" + v + "'. Доступные: "
                        + java.util.Arrays.toString(es.getValues()));
            }
            return String.valueOf(es.getValue());
        }
        throw new IllegalArgumentException("Настройку типа " + target.getClass().getSimpleName() + " пока нельзя менять через MCP");
    }

    // --- Действия ---

    private static String executeCommandTool(JsonObject args) {
        String cmd = requiredString(args, "command");
        boolean ok = CommandManager.INSTANCE.execute(cmd);
        return ok ? "Выполнено: " + cmd : "Команда не выполнена (проверьте префикс '.', например .toggle ItemESP): " + cmd;
    }

    private static String chatMessageTool(JsonObject args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String msg = requiredString(args, "message");
        if (mc.getNetworkHandler() == null) throw new IllegalStateException("Нет соединения с сервером");
        if (msg.startsWith("/")) {
            mc.getNetworkHandler().sendChatCommand(msg.substring(1));
        } else {
            mc.getNetworkHandler().sendChatMessage(msg);
        }
        return "Отправлено: " + msg;
    }

    private static String baritonePathTool(JsonObject args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (args.has("stop") && parseBoolean(args.get("stop"))) {
            BaritoneBridge.clearGoal();
            return "Маршрут Baritone отменён";
        }
        int x = args.has("x") ? args.get("x").getAsInt() : 0;
        int y = args.has("y") ? args.get("y").getAsInt() : (mc.player != null ? mc.player.getBlockY() : 64);
        int z = args.has("z") ? args.get("z").getAsInt() : 0;
        BaritoneBridge.setGoal(new BlockPos(x, y, z));
        return "Маршрут Baritone: " + x + ", " + y + ", " + z;
    }

    private static String setRotationTool(JsonObject args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float yaw = args.has("yaw") ? args.get("yaw").getAsFloat() : 0f;
        float pitch = args.has("pitch") ? args.get("pitch").getAsFloat() : 0f;

        RotationUtils.getInstance().setRotationSilent(yaw, pitch);
        if (mc.player != null) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
        return "Углы выставлены: yaw=" + yaw + ", pitch=" + pitch;
    }

    // --- Друзья ---

    private static String friendsJson() {
        JsonArray arr = new JsonArray();
        for (String f : FriendManager.getInstance().getFriends()) arr.add(f);

        JsonObject o = new JsonObject();
        o.add("friends", arr);
        return GSON.toJson(o);
    }

    private static String friendTool(JsonObject args, boolean add) {
        String name = requiredString(args, "name");
        if (add) {
            FriendManager.getInstance().addFriend(name);
            return "Добавлен в друзья: " + name;
        }
        FriendManager.getInstance().removeFriend(name);
        return "Убран из друзей: " + name;
    }

    // --- Скрипты ---

    private static String listScriptsJson() {
        ScriptManager manager = ScriptManager.getInstance();
        JsonObject o = new JsonObject();

        JsonArray active = new JsonArray();
        for (String n : manager.getActiveContexts().keySet()) active.add(n);
        o.add("active", active);

        JsonArray files = new JsonArray();
        for (String n : manager.listScriptFiles()) {
            JsonObject f = new JsonObject();
            f.addProperty("name", n);
            f.addProperty("active", manager.getActiveContexts().containsKey(n));
            files.add(f);
        }
        o.add("files", files);

        JsonObject failures = new JsonObject();
        for (Map.Entry<String, String> e : manager.getFailures().entrySet()) {
            failures.addProperty(e.getKey(), e.getValue());
        }
        o.add("failures", failures);
        return GSON.toJson(o);
    }

    private static String writeScriptTool(JsonObject args) throws IOException {
        String name = requiredString(args, "name");
        String content = args.has("content") ? args.get("content").getAsString() : "";
        boolean load = !args.has("load") || args.get("load").isJsonNull() || parseBoolean(args.get("load"));

        ScriptManager manager = ScriptManager.getInstance();
        Path file = manager.saveScriptSource(name, content);

        StringBuilder sb = new StringBuilder("Скрипт сохранён: " + file.getFileName());
        if (load) {
            boolean ok = manager.loadScript(file.toFile());
            if (ok) {
                sb.append(" — загружен и активен");
            } else {
                sb.append(" — НЕ загрузился");
                String reason = manager.getFailures().get(file.getFileName().toString());
                if (reason != null) sb.append(": ").append(reason);
            }
        }
        return sb.toString();
    }

    private static String readScriptTool(JsonObject args) throws IOException {
        String name = requiredString(args, "name");
        File file = ScriptManager.getInstance().findScriptFile(name);
        if (file == null) throw new IllegalArgumentException("Скрипт не найден: " + name);
        return Files.readString(file.toPath());
    }

    private static String deleteScriptTool(JsonObject args) {
        String name = requiredString(args, "name");
        boolean ok = ScriptManager.getInstance().deleteScript(name);
        return ok ? "Скрипт удалён: " + name : "Скрипт не найден: " + name;
    }

    private static String loadScriptTool(JsonObject args) {
        String name = requiredString(args, "name");
        boolean ok = ScriptManager.getInstance().loadScriptByName(name);
        return ok ? "Скрипт загружен: " + name : "Не удалось загрузить: " + name + " (нет файла или ошибка, смотрите list_scripts)";
    }

    private static String unloadScriptTool(JsonObject args) {
        String name = requiredString(args, "name");
        boolean ok = ScriptManager.getInstance().unloadScript(name);
        return ok ? "Скрипт выгружен: " + name : "Активный скрипт не найден: " + name;
    }

    // --- Аддоны ---

    private static String listAddonsJson() {
        AddonManager manager = AddonManager.getInstance();
        JsonObject root = new JsonObject();

        JsonArray loaded = new JsonArray();
        for (CollectiveAddon addon : manager.getAddons()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", addon.getName());
            o.addProperty("version", addon.getVersion());
            o.addProperty("author", addon.getAuthor());
            o.addProperty("description", addon.getDescription());

            JsonArray modules = new JsonArray();
            for (Module m : addon.getRegisteredModules()) modules.add(m.getName());
            o.add("modules", modules);

            JsonArray commands = new JsonArray();
            for (Command c : addon.getRegisteredCommands()) commands.add(c.getName());
            o.add("commands", commands);

            loaded.add(o);
        }
        root.add("loaded", loaded);

        JsonObject failures = new JsonObject();
        for (Map.Entry<String, String> e : manager.getLoadFailures().entrySet()) {
            failures.addProperty(e.getKey(), e.getValue());
        }
        root.add("failures", failures);
        return GSON.toJson(root);
    }

    private static String writeAddonSourceTool(JsonObject args) throws IOException {
        String name = requiredString(args, "name");
        String content = args.has("content") ? args.get("content").getAsString() : "";
        Path file = AddonManager.getInstance().saveAddonSource(name, content);
        return "Исходник сохранён: " + file.getFileName() + " (collective/addons-src). Дальше соберите и загрузите: build_addon.";
    }

    private static String readAddonSourceTool(JsonObject args) throws IOException {
        String name = requiredString(args, "name");
        Path file = AddonManager.getInstance().getAddonSource(name);
        if (file == null) throw new IllegalArgumentException("Исходник не найден: " + name);
        return Files.readString(file);
    }

    private static String deleteAddonSourceTool(JsonObject args) {
        String name = requiredString(args, "name");
        boolean ok = AddonManager.getInstance().deleteAddonSource(name);
        return ok ? "Исходник удалён: " + name : "Исходник не найден: " + name;
    }

    private static String buildAddonTool(JsonObject args) {
        String name = requiredString(args, "name");
        Path source = AddonManager.getInstance().getAddonSource(name);
        if (source == null) throw new IllegalArgumentException("Исходник не найден: " + name + " (сначала write_addon_source)");

        String baseName = source.getFileName().toString();
        baseName = baseName.substring(0, baseName.length() - ".java".length());

        AddonCompiler.BuildResult result = AddonCompiler.build(source, baseName);
        return result.message();
    }

    // --- Общие хелперы ---

    private static String requiredString(JsonObject args, String key) {
        if (!args.has(key) || args.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Не передан обязательный параметр: " + key);
        }
        return args.get(key).getAsString();
    }

    private static boolean parseBoolean(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
        String s = el.getAsString().trim();
        if (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("on") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("вкл")) {
            return true;
        }
        if (s.equalsIgnoreCase("false") || s.equals("0") || s.equalsIgnoreCase("off") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("выкл")) {
            return false;
        }
        throw new IllegalArgumentException("Не булево значение: " + s);
    }

    private static Color parseColor(String raw) {
        String v = raw.trim();
        if (v.startsWith("#")) v = v.substring(1);
        if (v.startsWith("0x") || v.startsWith("0X")) v = v.substring(2);
        try {
            long n = Long.parseLong(v, 16);
            if (v.length() <= 6) {
                return new Color((int) (n | 0xFF000000L), true);
            }
            if (v.length() == 8) {
                return new Color((int) n, true); // #AARRGGBB
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Цвет должен быть в формате #RRGGBB или #AARRGGBB, получено: " + raw);
    }
}
