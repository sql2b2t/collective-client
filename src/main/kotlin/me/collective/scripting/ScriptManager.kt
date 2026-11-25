package me.collective.scripting

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.utils.chat.ChatUtils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.Locale
import java.util.function.Consumer
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

object ScriptManager {
    private val LOG = LoggerFactory.getLogger(ScriptManager::class.java)

    // Публичные мосты для биндингов print/log.
    // НЕ использовать лямбды/метод-референсы (ctx::print) — Kotlin scripting
    // компилятор не может получить доступ к синтетическому классу
    // ScriptManager$$Lambda и падает с "Cannot access script provided property class".
    class PrintBridge(private val ctx: ScriptContext) : Consumer<Any?> {
        override fun accept(o: Any?) {
            ctx.print(o)
        }
    }

    class LogBridge(private val ctx: ScriptContext) : Consumer<Any?> {
        override fun accept(o: Any?) {
            ctx.log(o)
        }
    }

    /** Маркер: примеры уже создавались в этой папке. */
    private const val EXAMPLES_MARKER = ".examples-v1"

    val scriptsFolder: Path = FabricLoader.getInstance().gameDir.resolve("collective").resolve("scripts")

    private val _activeContexts: MutableMap<String, ScriptContext> = HashMap()
    private val failedScripts: MutableMap<String, String> = HashMap()
    private var engineManager: ScriptEngineManager? = null
    private var tickSubscribed: Boolean = false

    init {
        // Компиляция в процессе: демон Kotlin нестабилен на Windows (пути с пробелами,
        // антивирусы) и оставляет висячие процессы; in-process работает одинаково на обеих ОС.
        System.setProperty("kotlin.compiler.execution.strategy", "in-process")
        try {
            Files.createDirectories(scriptsFolder)
        } catch (_: Exception) {
        }
    }

    @JvmStatic
    fun getInstance(): ScriptManager = this

    /** Сообщение в чат с префиксом клиента. */
    fun message(text: String) {
        ChatUtils.printChat(Component.literal(text), ChatUtils.createSignature(-1))
    }

    fun init() {
        this.engineManager = ScriptEngineManager(ScriptManager::class.java.classLoader)
        this.subscribeTick()
        this.loadAllScripts()
    }

    /** Подписываем менеджер на тики — нужно для таймеров after()/every(). */
    private fun subscribeTick() {
        if (tickSubscribed) return
        tickSubscribed = true
        try {
            ClientAccess.eventBus.subscribe(this)
        } catch (t: Throwable) {
            LOG.error("[Collective-Scripting] Failed to subscribe tick handler: $t")
        }
    }

    @EventHandler
    private fun onTick(event: MinecraftTickEvent) {
        for (ctx in ArrayList(_activeContexts.values)) {
            try {
                ctx.tickTasks()
            } catch (t: Throwable) {
                LOG.error("[Collective-Scripting] Timer tick failed in ${ctx.scriptName}: $t")
            }
        }
    }

    // --- Примеры и README (создаются только командой .script examples) ---

    /** Создаёт отсутствующие примеры и README. Возвращает число созданных файлов. */
    fun writeMissingExamples(): Int {
        var written = 0
        try {
            Files.createDirectories(scriptsFolder)
            written += writeIfMissing("AutoJump.kts", EXAMPLE_AUTOJUMP)
            written += writeIfMissing("ExampleCommand.kts", EXAMPLE_COMMAND)
            written += writeIfMissing("ExampleHud.kts", EXAMPLE_HUD)
            written += writeIfMissing("ExampleTimer.kts", EXAMPLE_TIMER)
            written += writeIfMissing("README.txt", SCRIPTING_README)

            val marker = scriptsFolder.resolve(EXAMPLES_MARKER)
            if (!Files.exists(marker)) {
                Files.writeString(marker, "Примеры созданы. Удалите этот файл, чтобы недостающие примеры дозаписывались при запуске.")
            }
        } catch (e: Exception) {
            LOG.error("[Collective-Scripting] Failed to write examples: $e")
        }
        return written
    }

    private fun writeIfMissing(fileName: String, content: String): Int {
        return try {
            val file = scriptsFolder.resolve(fileName)
            if (Files.exists(file)) return 0
            Files.writeString(file, content)
            1
        } catch (_: Exception) {
            0
        }
    }

    // --- Загрузка скриптов ---

    fun loadAllScripts() {
        val folder = scriptsFolder.toFile()
        if (!folder.exists() || !folder.isDirectory) return

        val files = folder.listFiles { _, name -> isScriptFileName(name) } ?: return
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        for (file in files) {
            try {
                this.loadScript(file)
            } catch (t: Throwable) {
                LOG.error("[Collective-Scripting] Failed to load script ${file.name}: $t")
                this.failedScripts[file.name] = summarize(t)
            }
        }
    }

    fun loadScript(file: File): Boolean {
        val name = file.name
        this.unloadScript(name)

        val ext = name.substring(name.lastIndexOf('.') + 1)
        val engine = this.getEngineForExtension(ext)
        if (engine == null) {
            val available = this.describeAvailableEngines()
            LOG.error("[Collective-Scripting] No script engine found for extension: .$ext Available: $available")
            message("§c[Script] Нет движка для .$ext. Доступно: " + (if (available.isEmpty()) "пусто, пересобери мод с Kotlin" else available))
            this.failedScripts[name] = "нет движка для .$ext"
            return false
        }

        val ctx = ScriptContext(name)
        val bindings = engine.createBindings()
        bindings["ctx"] = ctx
        bindings["mc"] = ctx.mc
        bindings["client"] = ctx.mc
        bindings["print"] = PrintBridge(ctx)
        bindings["log"] = LogBridge(ctx)
        bindings["friends"] = ClientContext.socialRegistry
        bindings["modules"] = ClientContext.moduleRegistry
        bindings["commands"] = ClientContext.commandRegistry
        bindings["events"] = ctx.events
        bindings["rotations"] = ClientContext.rotationManager
        bindings["baritone"] = ClientContext.baritoneBridge

        return try {
            val code = Files.readString(file.toPath())
            engine.eval(code, bindings)
            this._activeContexts[name] = ctx
            this.failedScripts.remove(name)
            LOG.info("[Collective-Scripting] Loaded script: $name")
            true
        } catch (e: javax.script.ScriptException) {
            // Скрипт мог успеть зарегистрировать модули/команды до ошибки — чистим
            ctx.unregisterAll()
            this.failedScripts[name] = summarize(e)
            LOG.error("[Collective-Scripting] Error executing script $name: ${e.message}")
            message("§c[Script Error] $name: §7${summarize(e)}")
            false
        } catch (e: Exception) {
            ctx.unregisterAll()
            this.failedScripts[name] = summarize(e)
            LOG.error("[Collective-Scripting] Failed to read script $name: ${e.message}")
            false
        } catch (e: Throwable) {
            // Error (LinkageError/ExceptionInInitializerError/...) тоже должен чистить
            // частично зарегистрированные модули/команды/подписки.
            ctx.unregisterAll()
            this.failedScripts[name] = summarize(e)
            LOG.error("[Collective-Scripting] Failed to load script $name: ${e.message}")
            false
        }
    }

    fun unloadScript(name: String): Boolean {
        var cleanName = name
        if (!_activeContexts.containsKey(cleanName)) {
            var match: String? = null
            for (key in _activeContexts.keys) {
                if (key.equals(cleanName, ignoreCase = true)) {
                    match = key
                    break
                }
            }
            if (match == null) {
                // Точное имя файла с расширением (в фиксированном порядке приоритета),
                // чтобы при test.kts + test.js выгружался именно запрошенный скрипт.
                for (ext in arrayOf(".kts", ".kt", ".js", ".groovy")) {
                    val candidate = cleanName + ext
                    for (key in _activeContexts.keys) {
                        if (key.equals(candidate, ignoreCase = true)) {
                            match = key
                            break
                        }
                    }
                    if (match != null) {
                        break
                    }
                }
            }
            if (match != null) {
                cleanName = match
            }
        }

        val ctx = _activeContexts.remove(cleanName)
        if (ctx != null) {
            ctx.unregisterAll()
            LOG.info("[Collective-Scripting] Unloaded script: $cleanName")
            return true
        }
        return false
    }

    fun loadScriptByName(name: String): Boolean {
        val target = this.findScriptFile(name)
        return target != null && this.loadScript(target)
    }

    fun reload() {
        for (name in ArrayList(_activeContexts.keys)) {
            this.unloadScript(name)
        }
        failedScripts.clear()
        loadAllScripts()
        val failed = failedScripts.size
        message(
            "§a[Collective-Scripting] Скрипты перезагружены! Активно: §f${_activeContexts.size}" +
                if (failed > 0) " §7(§cошибок: $failed§7)" else ""
        )
    }

    // --- Файловые операции (используются командой .script) ---

    /** Имя файла скрипта с расширением по умолчанию (.kts). */
    fun normalizeScriptFileName(name: String?): String? {
        val clean = sanitizeFileName(name)
        if (clean.isEmpty()) return null
        val lower = clean.lowercase(Locale.ROOT)
        val hasExt = lower.endsWith(".kts") || lower.endsWith(".kt") || lower.endsWith(".js") || lower.endsWith(".groovy")
        return if (hasExt) clean else "$clean.kts"
    }

    /** Путь к файлу скрипта в папке скриптов (файл может не существовать). */
    fun getScriptFile(name: String): Path? {
        val fileName = normalizeScriptFileName(name) ?: return null
        return scriptsFolder.resolve(fileName)
    }

    /** Сохранить исходник скрипта (перезапись). Возвращает путь к файлу. */
    @Throws(IOException::class)
    fun saveScriptSource(name: String?, content: String?): Path {
        val fileName = normalizeScriptFileName(name) ?: throw IOException("Недопустимое имя скрипта")
        Files.createDirectories(scriptsFolder)
        val file = scriptsFolder.resolve(fileName)
        Files.writeString(file, content ?: "")
        return file
    }

    /** Найти существующий файл скрипта по имени (с подбором расширения). */
    fun findScriptFile(name: String): File? {
        val clean = sanitizeFileName(name)
        if (clean.isEmpty()) return null

        val direct = File(scriptsFolder.toFile(), clean)
        if (direct.isFile) return direct

        for (ext in arrayOf(".kts", ".kt", ".js", ".groovy")) {
            val f = File(scriptsFolder.toFile(), clean + ext)
            if (f.isFile) return f
        }
        return null
    }

    /** Удалить файл скрипта (если активен — сначала выгружаем). */
    fun deleteScript(name: String): Boolean {
        val file = this.findScriptFile(name) ?: return false

        this.unloadScript(file.name)
        return try {
            Files.deleteIfExists(file.toPath())
            failedScripts.remove(file.name)
            true
        } catch (e: Exception) {
            LOG.error("[Collective-Scripting] Failed to delete script ${file.name}: $e")
            false
        }
    }

    /** Имена файлов скриптов в папке (отсортированные). */
    fun listScriptFiles(): List<String> {
        val folder = scriptsFolder.toFile()
        if (!folder.isDirectory) return emptyList()
        val files = folder.listFiles { _, name -> isScriptFileName(name) } ?: return emptyList()

        val names = ArrayList<String>()
        for (f in files) names.add(f.name)
        names.sortWith(String.CASE_INSENSITIVE_ORDER)
        return names
    }

    /** Ошибки последней загрузки: имя файла -> причина. */
    val failures: Map<String, String>
        get() = Collections.unmodifiableMap(failedScripts)

    val activeContexts: Map<String, ScriptContext>
        get() = Collections.unmodifiableMap(_activeContexts)

    private fun getEngineForExtension(ext: String): ScriptEngine? {
        if (engineManager == null) {
            // Пробуем сначала classloader мода, потом context classloader Fabric
            engineManager = ScriptEngineManager(ScriptManager::class.java.classLoader)
            if (engineManager!!.engineFactories.isEmpty()) {
                engineManager = ScriptEngineManager(Thread.currentThread().contextClassLoader)
            }
        }

        var engine = engineManager!!.getEngineByExtension(ext)
        if (engine != null) return engine

        // Try standard Kotlin engine names
        if (ext.equals("kts", ignoreCase = true) || ext.equals("kt", ignoreCase = true)) {
            engine = engineManager!!.getEngineByName("kotlin")
            if (engine != null) return engine
            engine = engineManager!!.getEngineByName("Kotlin")
            if (engine != null) return engine
            engine = engineManager!!.getEngineByName("kts")
            if (engine != null) return engine
            engine = engineManager!!.getEngineByName("kt")
            if (engine != null) return engine
        }

        // Try javascript or groovy as alternative fallbacks
        if (ext.equals("js", ignoreCase = true)) return engineManager!!.getEngineByName("nashorn")
        if (ext.equals("groovy", ignoreCase = true)) return engineManager!!.getEngineByName("groovy")

        // Прямой fallback без ServiceLoader: Fabric JiJ прячет META-INF/services
        // внутри META-INF/jars/, поэтому ScriptEngineManager может быть пустым.
        // Создаём Kotlin JSR-223 фабрику напрямую по имени класса.
        if (ext.equals("kts", ignoreCase = true) || ext.equals("kt", ignoreCase = true)) {
            try {
                val facClass = Class.forName(
                    "kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory",
                    true, ScriptManager::class.java.classLoader
                )
                val fac = facClass.getDeclaredConstructor().newInstance() as javax.script.ScriptEngineFactory
                val direct = fac.scriptEngine
                if (direct != null) return direct
            } catch (t: Throwable) {
                LOG.error("[Collective-Scripting] Direct Kotlin factory failed: $t")
            }
        }

        return null
    }

    private fun describeAvailableEngines(): String {
        val available = StringBuilder()
        try {
            for (f in engineManager!!.engineFactories) {
                available.append(f.engineName).append(f.extensions).append("; ")
            }
        } catch (_: Exception) {
        }
        return available.toString()
    }

    private fun summarize(t: Throwable): String {
        var msg = t.message
        if (msg.isNullOrBlank()) msg = t.toString()
        val nl = msg.indexOf('\n')
        if (nl > 0) msg = msg.substring(0, nl)
        if (msg.length > 160) msg = msg.substring(0, 157) + "..."
        return msg
    }

    @JvmStatic
    fun isScriptFileName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".kts") || lower.endsWith(".kt") || lower.endsWith(".js") || lower.endsWith(".groovy")
    }

    /** Санитайз имени файла: без путей, без "..". */
    @JvmStatic
    fun sanitizeFileName(name: String?): String {
        if (name == null) return ""
        var clean = name.trim().replace('\\', '/')
        val slash = clean.lastIndexOf('/')
        if (slash >= 0) clean = clean.substring(slash + 1)
        clean = clean.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        if (clean.contains("..")) clean = clean.replace("..", "_")
        if (clean == "." || clean == "..") return ""
        return clean
    }

    private const val EXAMPLE_AUTOJUMP = """// Пример скриптового модуля: автоматический прыжок при движении вперёд.
// Модуль появится в ClickGUI (категория MOVEMENT), его можно биндить и настраивать.
// Всё API доступно через ctx: ctx.module, ctx.print, ctx.every, ctx.log, ...

val autoJump = ctx.module("AutoJump", me.collective.client.modules.ModuleCategory.MOVEMENT, "Автоматически прыгает при движении")

autoJump.onTick { mod ->
    val player = mc.player
    if (player != null && player.onGround() && mc.options.keyUp.isDown) {
        player.jumpFromGround()
    }
}

ctx.print("Скрипт AutoJump.kts загружен!")
"""

    private const val EXAMPLE_COMMAND = """// Пример клиентской команды: .mycoords — печатает ваши координаты.
// Алиасы добавляются после лямбды: ctx.command("mycoords", "описание", { args -> ... }, "mc1", "coord")

ctx.command("mycoords", "Показывает ваши координаты", { args ->
    val player = mc.player
    if (player != null) {
        ctx.print("Координаты: " + player.blockX + ", " + player.blockY + ", " + player.blockZ)
    } else {
        ctx.print("Вы не в игре")
    }
})
"""

    private const val EXAMPLE_HUD = """// Пример HUD-элемента: координаты игрока на экране.
// Элемент включается/выключается как модуль и перетаскивается в HUD-редакторе.

val hudCoords = ctx.hud("ScriptCoords", "Координаты игрока (скрипт)", 5f, 20f)
hudCoords.setSize(120f, 12f)

hudCoords.onRender { poses, partialTick ->
    val player = mc.player
    if (player != null) {
        val text = "XYZ: " + player.blockX + " " + player.blockY + " " + player.blockZ
        hudCoords.drawText(poses, text, 1f, 1f, 0xFFC8E6FF.toInt())
    }
}
"""

    private const val EXAMPLE_TIMER = """// Пример таймеров: ctx.every() повторяется, ctx.after() срабатывает один раз.
// Тики: 20 тиков = 1 секунда. id из every() можно передать в ctx.cancelTask(id).

ctx.every(200) {
    ctx.print("Таймер: прошло 10 секунд")
}

ctx.after(60) {
    ctx.print("Прошло 3 секунды после загрузки скрипта")
}
"""

    private const val SCRIPTING_README = """Collective Client — скрипты (Kotlin)
=====================================

Где лежат скрипты:
  Эта папка (collective/scripts). Поддерживаются файлы .kts и .kt (движок Kotlin).
  .js/.groovy работают только если в сборке есть соответствующий движок.

ГЛАВНОЕ ПРАВИЛО: всё API вызывается через ctx с точкой.
  ctx.module(...), ctx.print(...), ctx.every(20) { ... }, ctx.command(...), ctx.hud(...)
  Голые вызовы не работают: print(...) — это встроенная функция Kotlin (пишет в stdout,
  а не в чат!), а module(...)/every(...) без ctx просто не существуют.

Команды:
  .script list             — список скриптов, статус и ошибки загрузки
  .script load <имя>       — загрузить скрипт
  .script unload <имя>     — выгрузить скрипт
  .script reload           — перезагрузить все скрипты
  .script examples         — дозаписать недостающие примеры
  .script folder           — открыть эту папку

Что доступно в скрипте (глобальные имена):
  ctx            — контекст скрипта: ВСЕ действия через него (см. ниже)
  mc, client     — Minecraft (mojmap-имена классов)
  friends        — SocialRegistry (friends.isFriendName("Nick"), friends.addFriend("Nick"))
  modules        — ModuleRegistry
  commands       — CommandRegistry
  events         — шина событий (events.subscribe(obj); подписки снимаются при выгрузке)
  rotations      — RotationManager
  baritone       — BaritoneBridge (baritone.pathTo(...), baritone.cancelPathing())
  log            — мост-консюмер (log.accept("текст")); в чат пишет ctx.print(...)

Модуль:
  val mod = ctx.module("Имя", me.collective.client.modules.ModuleCategory.MISC, "описание")
  Настройки: mod.boolSetting("Имя", true)
             mod.numberSetting("Скорость", 1.0, 0.0, 10.0, 0.1)
             mod.stringSetting("Текст", "привет")
             mod.colorSetting("Цвет", java.awt.Color.RED)
  События:   mod.onTick { }, mod.onTickPost { },
             mod.onPacketSend { event -> }, mod.onPacketReceive { event -> },
             mod.onRender3D { event -> }, mod.onRender2D { graphics, partialTick -> },
             mod.onPlayerMove { }, mod.onBreakBlock { }, mod.onSendMessage { },
             mod.onMouseClick { }, mod.onKeyInput { }, mod.onInteractEntity { },
             mod.onEnable { }, mod.onDisable { }
  Ошибки:    после 5 ошибок в колбэках модуль выключается автоматически.

Команда:
  ctx.command("имя", "описание", { args -> ... }, "алиас1", "алиас2")   // алиасы опциональны

HUD-элемент:
  val el = ctx.hud("Имя", "описание", 5f, 20f)
  el.setSize(120f, 12f)
  el.onRender { poses, partialTick -> el.drawText(poses, "Текст", 1f, 1f, 0xFFFFFFFF.toInt()) }

Таймеры:
  ctx.every(100) { ... }   — выполнять каждые 100 тиков (повтор)
  ctx.after(40) { ... }    — выполнить один раз через 40 тиков
  ctx.cancelTask(id)       — отменить задачу по id, который вернули every/after

Полезные методы ctx:
  ctx.print("msg")                             — сообщение в чат
  ctx.log("msg")                               — сообщение в консоль
  ctx.sendChatMessage("привет")                — отправить в чат (или серверную /команду)
  ctx.executeClientCommand("toggle AutoJump")  — выполнить команду клиента
  ctx.sendPacket(packet)                       — отправить пакет на сервер
  ctx.rotateSilent(yaw, pitch)                 — тихая ротация
  ctx.rotateTo(x, y, z)                        — навестись на точку
  ctx.pathTo(x, y, z), ctx.cancelPath()        — Baritone
  ctx.getModule("KillAura")                    — найти любой модуль клиента
  ctx.runOnMainThread { ... }                  — выполнить код в игровом потоке

Если скрипт не загрузился, причина видна в .script list (и в консоли игры).
"""
}
