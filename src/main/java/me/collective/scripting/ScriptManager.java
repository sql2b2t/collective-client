package me.collective.scripting;

import me.collective.command.ChatHelper;
import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import javax.script.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ScriptManager {
    public static final ScriptManager INSTANCE = new ScriptManager();

    // Публичные мосты для биндингов print/log.
    // НЕ использовать лямбды/метод-референсы (ctx::print) — Kotlin scripting
    // компилятор не может получить доступ к синтетическому классу
    // ScriptManager$$Lambda и падает с "Cannot access script provided property class".
    public static final class PrintBridge implements java.util.function.Consumer<Object> {
        private final ScriptContext ctx;
        public PrintBridge(ScriptContext ctx) { this.ctx = ctx; }
        @Override public void accept(Object o) { ctx.print(o); }
    }

    public static final class LogBridge implements java.util.function.Consumer<Object> {
        private final ScriptContext ctx;
        public LogBridge(ScriptContext ctx) { this.ctx = ctx; }
        @Override public void accept(Object o) { ctx.log(o); }
    }

    /** Маркер: примеры v2 уже создавались в этой папке. */
    private static final String EXAMPLES_MARKER = ".examples-v2";

    private final Path scriptsFolder;
    private final Map<String, ScriptContext> activeContexts = new HashMap<>();
    private final Map<String, String> failedScripts = new HashMap<>();
    private ScriptEngineManager engineManager;
    private boolean tickSubscribed;

    private ScriptManager() {
        this.scriptsFolder = FabricLoader.getInstance().getGameDir().resolve("collective").resolve("scripts");
        try {
            Files.createDirectories(scriptsFolder);
        } catch (Exception ignored) {}
    }

    public static ScriptManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        this.engineManager = new ScriptEngineManager(getClass().getClassLoader());
        writeExamplesOnFirstRun();
        subscribeTick();
        loadAllScripts();
    }

    /** Подписываем менеджер на тики — нужно для таймеров after()/every(). */
    private void subscribeTick() {
        if (tickSubscribed) return;
        tickSubscribed = true;
        try {
            EventBus.INSTANCE.subscribe(this);
        } catch (Throwable t) {
            System.err.println("[Collective-Scripting] Failed to subscribe tick handler: " + t);
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        for (ScriptContext ctx : new ArrayList<>(activeContexts.values())) {
            try {
                ctx.tickTasks();
            } catch (Throwable t) {
                System.err.println("[Collective-Scripting] Timer tick failed in " + ctx.scriptName + ": " + t);
            }
        }
    }

    // --- Примеры и README ---

    private void writeExamplesOnFirstRun() {
        try {
            Path marker = scriptsFolder.resolve(EXAMPLES_MARKER);
            if (Files.exists(marker)) return;
            writeMissingExamples();
        } catch (Exception ignored) {}
    }

    /** Создаёт отсутствующие примеры и README. Возвращает число созданных файлов. */
    public int writeMissingExamples() {
        int written = 0;
        try {
            Files.createDirectories(scriptsFolder);
            written += writeIfMissing("AutoJump.kts", EXAMPLE_AUTOJUMP);
            written += writeIfMissing("ExampleCommand.kts", EXAMPLE_COMMAND);
            written += writeIfMissing("ExampleHud.kts", EXAMPLE_HUD);
            written += writeIfMissing("ExampleTimer.kts", EXAMPLE_TIMER);
            written += writeIfMissing("README.txt", SCRIPTING_README);

            Path marker = scriptsFolder.resolve(EXAMPLES_MARKER);
            if (!Files.exists(marker)) {
                Files.writeString(marker, "Примеры v2 созданы. Удалите этот файл, чтобы недостающие примеры дозаписывались при запуске.");
            }
        } catch (Exception e) {
            System.err.println("[Collective-Scripting] Failed to write examples: " + e);
        }
        return written;
    }

    private int writeIfMissing(String fileName, String content) {
        try {
            Path file = scriptsFolder.resolve(fileName);
            if (Files.exists(file)) return 0;
            Files.writeString(file, content);
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    // --- Загрузка скриптов ---

    public void loadAllScripts() {
        File folder = scriptsFolder.toFile();
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles((dir, name) -> isScriptFileName(name));
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File file : files) {
            loadScript(file);
        }
    }

    public static boolean isScriptFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".kts") || lower.endsWith(".kt") || lower.endsWith(".js") || lower.endsWith(".groovy");
    }

    public boolean loadScript(File file) {
        String name = file.getName();
        unloadScript(name);

        String ext = name.substring(name.lastIndexOf('.') + 1);
        ScriptEngine engine = getEngineForExtension(ext);
        if (engine == null) {
            String available = describeAvailableEngines();
            System.err.println("[Collective-Scripting] No script engine found for extension: ." + ext + " Available: " + available);
            ChatHelper.sendMessage(Text.literal("§c[Script] Нет движка для ." + ext + ". Доступно: " + (available.isEmpty() ? "пусто, пересобери мод с Kotlin" : available)));
            failedScripts.put(name, "нет движка для ." + ext);
            return false;
        }

        ScriptContext ctx = new ScriptContext(name);
        Bindings bindings = engine.createBindings();
        bindings.put("ctx", ctx);
        bindings.put("mc", ctx.mc);
        bindings.put("client", ctx.mc);
        bindings.put("print", new PrintBridge(ctx));
        bindings.put("log", new LogBridge(ctx));
        bindings.put("friends", me.collective.manager.FriendManager.getInstance());
        bindings.put("modules", me.collective.module.ModuleManager.INSTANCE);
        bindings.put("commands", me.collective.command.CommandManager.INSTANCE);
        bindings.put("events", me.collective.event.EventBus.INSTANCE);
        bindings.put("rotations", me.collective.util.RotationUtils.getInstance());
        bindings.put("baritone", me.collective.util.BaritoneBridge.class);
        bindings.put("inv", me.collective.util.InventoryUtil.class);
        bindings.put("fonts", me.collective.font.FontManager.INSTANCE);
        bindings.put("blur", me.collective.render.BlurEngine.INSTANCE);

        try {
            String code = Files.readString(file.toPath());
            engine.eval(code, bindings);
            activeContexts.put(name, ctx);
            failedScripts.remove(name);
            System.out.println("[Collective-Scripting] Loaded script: " + name);
            return true;
        } catch (ScriptException e) {
            // Скрипт мог успеть зарегистрировать модули/команды до ошибки — чистим
            ctx.unregisterAll();
            failedScripts.put(name, summarize(e));
            System.err.println("[Collective-Scripting] Error executing script " + name + ": " + e.getMessage());
            ChatHelper.sendMessage(Text.literal("§c[Script Error] " + name + ": §7" + summarize(e)));
        } catch (Exception e) {
            ctx.unregisterAll();
            failedScripts.put(name, summarize(e));
            System.err.println("[Collective-Scripting] Failed to read script " + name + ": " + e.getMessage());
        }
        return false;
    }

    private ScriptEngine getEngineForExtension(String ext) {
        if (engineManager == null) {
            // Пробуем сначала classloader мода, потом context classloader Fabric
            engineManager = new ScriptEngineManager(getClass().getClassLoader());
            if (engineManager.getEngineFactories().isEmpty()) {
                engineManager = new ScriptEngineManager(Thread.currentThread().getContextClassLoader());
            }
        }

        ScriptEngine engine = engineManager.getEngineByExtension(ext);
        if (engine != null) return engine;

        // Try standard Kotlin engine names (kotlin-main-kts регистрируется как "kotlin" / "kts")
        if (ext.equalsIgnoreCase("kts") || ext.equalsIgnoreCase("kt")) {
            engine = engineManager.getEngineByName("kotlin");
            if (engine != null) return engine;
            engine = engineManager.getEngineByName("Kotlin");
            if (engine != null) return engine;
            engine = engineManager.getEngineByName("kts");
            if (engine != null) return engine;
            engine = engineManager.getEngineByName("kt");
            if (engine != null) return engine;
        }

        // Try javascript or groovy as alternative fallbacks
        if (ext.equalsIgnoreCase("js")) return engineManager.getEngineByName("nashorn");
        if (ext.equalsIgnoreCase("groovy")) return engineManager.getEngineByName("groovy");

        // Прямой fallback без ServiceLoader: Fabric JiJ прячет META-INF/services
        // внутри META-INF/jars/, поэтому ScriptEngineManager может быть пустым.
        // Создаём Kotlin JSR-223 фабрику напрямую по имени класса.
        if (ext.equalsIgnoreCase("kts") || ext.equalsIgnoreCase("kt")) {
            try {
                Class<?> facClass = Class.forName(
                    "kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory",
                    true, getClass().getClassLoader());
                javax.script.ScriptEngineFactory fac =
                    (javax.script.ScriptEngineFactory) facClass.getDeclaredConstructor().newInstance();
                ScriptEngine direct = fac.getScriptEngine();
                if (direct != null) return direct;
            } catch (Throwable t) {
                System.err.println("[Collective-Scripting] Direct Kotlin factory failed: " + t);
            }
        }

        return null;
    }

    private String describeAvailableEngines() {
        StringBuilder available = new StringBuilder();
        try {
            for (javax.script.ScriptEngineFactory f : engineManager.getEngineFactories()) {
                available.append(f.getEngineName()).append(f.getExtensions()).append("; ");
            }
        } catch (Exception ignored) {}
        return available.toString();
    }

    private static String summarize(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) msg = t.toString();
        int nl = msg.indexOf('\n');
        if (nl > 0) msg = msg.substring(0, nl);
        if (msg.length() > 160) msg = msg.substring(0, 157) + "...";
        return msg;
    }

    public boolean unloadScript(String name) {
        String cleanName = name;
        if (!activeContexts.containsKey(cleanName)) {
            for (String key : activeContexts.keySet()) {
                if (key.equalsIgnoreCase(cleanName) || key.startsWith(cleanName + ".")) {
                    cleanName = key;
                    break;
                }
            }
        }

        ScriptContext ctx = activeContexts.remove(cleanName);
        if (ctx != null) {
            ctx.unregisterAll();
            System.out.println("[Collective-Scripting] Unloaded script: " + cleanName);
            return true;
        }
        return false;
    }

    public boolean loadScriptByName(String name) {
        File target = findScriptFile(name);
        return target != null && loadScript(target);
    }

    public void reload() {
        for (String name : new ArrayList<>(activeContexts.keySet())) {
            unloadScript(name);
        }
        failedScripts.clear();
        loadAllScripts();
        int failed = failedScripts.size();
        ChatHelper.sendMessage(Text.literal("§a[Collective-Scripting] Скрипты перезагружены! Активно: §f"
                + activeContexts.size() + (failed > 0 ? " §7(§cошибок: " + failed + "§7)" : "")));
    }

    // --- Файловые операции (используются командой .scripts и MCP) ---

    /** Санитайз имени файла: без путей, без "..". */
    public static String sanitizeFileName(String name) {
        if (name == null) return "";
        String clean = name.trim().replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        if (slash >= 0) clean = clean.substring(slash + 1);
        clean = clean.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        if (clean.contains("..")) clean = clean.replace("..", "_");
        return clean;
    }

    /** Имя файла скрипта с расширением по умолчанию (.kts). */
    public static String normalizeScriptFileName(String name) {
        String clean = sanitizeFileName(name);
        if (clean.isEmpty()) return null;
        String lower = clean.toLowerCase(Locale.ROOT);
        boolean hasExt = lower.endsWith(".kts") || lower.endsWith(".kt") || lower.endsWith(".js") || lower.endsWith(".groovy");
        return hasExt ? clean : clean + ".kts";
    }

    /** Путь к файлу скрипта в папке скриптов (файл может не существовать). */
    public Path getScriptFile(String name) {
        String fileName = normalizeScriptFileName(name);
        return fileName == null ? null : scriptsFolder.resolve(fileName);
    }

    /** Сохранить исходник скрипта (перезапись). Возвращает путь к файлу. */
    public Path saveScriptSource(String name, String content) throws IOException {
        String fileName = normalizeScriptFileName(name);
        if (fileName == null) throw new IOException("Недопустимое имя скрипта");
        Files.createDirectories(scriptsFolder);
        Path file = scriptsFolder.resolve(fileName);
        Files.writeString(file, content == null ? "" : content);
        return file;
    }

    /** Найти существующий файл скрипта по имени (с подбором расширения). */
    public File findScriptFile(String name) {
        String clean = sanitizeFileName(name);
        if (clean.isEmpty()) return null;

        File direct = new File(scriptsFolder.toFile(), clean);
        if (direct.isFile()) return direct;

        for (String ext : new String[]{".kts", ".kt", ".js", ".groovy"}) {
            File f = new File(scriptsFolder.toFile(), clean + ext);
            if (f.isFile()) return f;
        }
        return null;
    }

    /** Удалить файл скрипта (если активен — сначала выгружаем). */
    public boolean deleteScript(String name) {
        File file = findScriptFile(name);
        if (file == null) return false;

        unloadScript(file.getName());
        try {
            Files.deleteIfExists(file.toPath());
            failedScripts.remove(file.getName());
            return true;
        } catch (Exception e) {
            System.err.println("[Collective-Scripting] Failed to delete script " + file.getName() + ": " + e);
            return false;
        }
    }

    /** Имена файлов скриптов в папке (отсортированные). */
    public List<String> listScriptFiles() {
        File folder = scriptsFolder.toFile();
        if (!folder.isDirectory()) return List.of();
        File[] files = folder.listFiles((dir, name) -> isScriptFileName(name));
        if (files == null) return List.of();

        List<String> names = new ArrayList<>();
        for (File f : files) names.add(f.getName());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // --- Состояние ---

    public Path getScriptsFolder() {
        return scriptsFolder;
    }

    public Map<String, ScriptContext> getActiveContexts() {
        return Collections.unmodifiableMap(activeContexts);
    }

    /** Ошибки последней загрузки: имя файла -> причина. */
    public Map<String, String> getFailures() {
        return Collections.unmodifiableMap(failedScripts);
    }

    // --- Содержимое примеров ---

    private static final String EXAMPLE_AUTOJUMP = """
            // Пример скриптового модуля: автоматический прыжок при беге.
            // Модуль появится в ClickGUI (категория MOVEMENT), его можно биндить и настраивать.
            val autoJump = module("AutoJump", me.collective.category.Category.MOVEMENT, "Автоматически прыгает при движении")

            autoJump.onTick { mod ->
                if (mc.player != null && mc.player!!.isOnGround && mc.options.forwardKey.isPressed) {
                    mc.player!!.jump()
                }
            }

            print("AutoJump.kts успешно загружен!")
            """;

    private static final String EXAMPLE_COMMAND = """
            // Пример клиентской команды: .mycoords — печатает ваши координаты.
            // Можно добавлять алиасы: command("mycoords", "описание", { args -> ... }, "mc1", "coord")

            command("mycoords", "Показывает ваши координаты", { args ->
                if (mc.player != null) {
                    val p = mc.player!!
                    print("Координаты: " + p.blockX + ", " + p.blockY + ", " + p.blockZ)
                } else {
                    print("Вы не в игре")
                }
            })
            """;

    private static final String EXAMPLE_HUD = """
            // Пример HUD-элемента: координаты игрока на экране.
            // Элемент включается/выключается как модуль и перетаскивается в HUD-редакторе.

            val hudCoords = hud("ScriptCoords", "Координаты игрока (скрипт)", 5f, 20f)
            hudCoords.setSize(120f, 12f)

            hudCoords.onRender { renderContext, delta ->
                if (mc.player != null) {
                    val p = mc.player!!
                    val text = "XYZ: " + p.blockX + " " + p.blockY + " " + p.blockZ
                    fonts.drawStringWithShadow(renderContext, text, 1f, 1f, java.awt.Color(200, 230, 255))
                }
            }
            """;

    private static final String EXAMPLE_TIMER = """
            // Пример таймеров: every() повторяется, after() срабатывает один раз.
            // Тики: 20 тиков = 1 секунда. every()/after() возвращают id — его можно
            // передать в cancelTask(id), чтобы остановить задачу.

            every(200) {
                if (mc.player != null) {
                    val p = mc.player!!
                    print("Таймер: " + p.blockX + ", " + p.blockY + ", " + p.blockZ)
                }
            }

            after(60) {
                print("Прошло 3 секунды после загрузки скрипта")
            }
            """;

    private static final String SCRIPTING_README = """
            Collective Client — скрипты (Kotlin)
            =====================================

            Где лежат скрипты:
              Эта папка (collective/scripts). Поддерживаются файлы .kts и .kt (движок Kotlin).
              .js/.groovy работают только если в сборке есть соответствующий движок.

            Команды:
              .scripts list            — список скриптов, статус и ошибки загрузки
              .scripts load <имя>      — загрузить скрипт
              .scripts unload <имя>    — выгрузить скрипт
              .scripts reload          — перезагрузить все скрипты
              .scripts examples        — дозаписать недостающие примеры
              .scripts folder          — открыть эту папку

            Что доступно в скрипте (глобальные имена):
              mc, client     — MinecraftClient
              ctx            — контекст скрипта (все методы ниже)
              print(msg)     — сообщение в чат
              log(msg)       — сообщение в консоль
              friends        — FriendManager (friends.isFriend("Nick"), friends.addFriend("Nick"))
              modules        — ModuleManager
              commands       — CommandManager
              events         — EventBus
              rotations      — RotationUtils
              baritone       — BaritoneBridge (baritone.setGoal(...), baritone.clearGoal())
              inv            — InventoryUtil
              fonts          — FontManager
              blur           — BlurEngine

            Модуль:
              val mod = module("Имя", me.collective.category.Category.MISC, "описание")
              Настройки: mod.boolSetting("Имя", true)
                         mod.numberSetting("Скорость", 1.0, 0.0, 10.0, 0.1)
                         mod.modeSetting("Режим", "A", "A", "B")
                         mod.stringSetting("Текст", "привет")
                         mod.colorSetting("Цвет", java.awt.Color.RED)
              События:   mod.onTick { }, mod.onTickPost { },
                         mod.onPacketSend { event -> }, mod.onPacketReceive { event -> },
                         mod.onRender3D { event -> }, mod.onRender2D { context, delta -> },
                         mod.onPlayerMove { }, mod.onBreakBlock { }, mod.onSendMessage { },
                         mod.onMouseClick { }, mod.onKeyInput { }, mod.onInteractEntity { },
                         mod.onEnable { }, mod.onDisable { }
              Ошибки:    после 5 ошибок в колбэках модуль выключается автоматически.

            Команда:
              command("имя", "описание", { args -> ... }, "алиас1", "алиас2")   // алиасы опциональны

            HUD-элемент:
              val el = hud("Имя", "описание", 5f, 20f)
              el.setSize(120f, 12f)
              el.onRender { context, delta -> fonts.drawStringWithShadow(context, "Текст", 1f, 1f, java.awt.Color.WHITE) }

            Таймеры:
              every(100) { ... }   — выполнять каждые 100 тиков (повтор)
              after(40) { ... }    — выполнить один раз через 40 тиков
              cancelTask(id)       — отменить задачу по id, который вернули every/after

            Полезные методы ctx:
              sendChatMessage("привет")                 — отправить сообщение в чат (или /команду)
              executeClientCommand(".toggle AutoJump")  — выполнить команду клиента
              sendPacket(packet)                        — отправить пакет на сервер
              rotateSilent(yaw, pitch)                  — тихая ротация через RotationUtils
              rotateTo(x, y, z)                         — навестись на точку
              pathTo(x, y, z), cancelPath()             — Baritone
              getModule("ItemESP")                      — найти любой модуль клиента
              runOnMainThread { ... }                   — выполнить код в игровом потоке
              print(...), log(...)                      — вывод

            Если скрипт не загрузился, причина видна в .scripts list (и в консоли игры).
            """;
}
