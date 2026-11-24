package me.collective.scripting

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.Command
import me.collective.client.events.Cancellable
import me.collective.client.events.bus.EventDispatcher
import me.collective.client.events.bus.EventListener
import me.collective.client.integrations.baritone.BaritoneBridge
import me.collective.client.managers.CommandRegistry
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.function.Consumer
import kotlin.math.atan2
import kotlin.math.sqrt

class ScriptContext(val scriptName: String) {
    val mc: Minecraft = Minecraft.getInstance()

    /**
     * Мост шины событий для скриптов: трекает подписки и снимает их при выгрузке,
     * чтобы обработчики скрипта не оставались в шине после unload/reload.
     */
    val events: EventBridge = EventBridge()

    private val _registeredModules: MutableList<Module> = ArrayList()
    private val _registeredCommands: MutableList<Command> = ArrayList()
    private val _registeredHudElements: MutableList<HudWidgetModule> = ArrayList()

    private val tasks: MutableList<ScriptTask> = ArrayList()
    private val taskLock = Any()
    private var nextTaskId: Int = 1

    private val errorCounts: MutableMap<String, Int> = HashMap()

    // --- Module Registration ---

    fun module(name: String, category: ModuleCategory, description: String): ScriptModule {
        val sm = ScriptModule(name, description, category)
        sm.setScriptContext(this)
        _registeredModules.add(sm)
        ClientContext.moduleRegistry.registerModule(sm)
        return sm
    }

    fun module(name: String, category: ModuleCategory): ScriptModule {
        return module(name, category, "Scripted module from $scriptName")
    }

    fun module(name: String, categoryName: String): ScriptModule {
        var cat = ModuleCategory.MISC
        try {
            cat = ModuleCategory.valueOf(categoryName.uppercase())
        } catch (_: Exception) {
        }
        return module(name, cat)
    }

    // --- Custom Chat Commands ---

    fun command(name: String, description: String, action: Consumer<Array<String>>, vararg aliases: String): ScriptCustomCommand {
        val cmd = ScriptCustomCommand(name, description, action, *aliases)
        _registeredCommands.add(cmd)
        ClientContext.commandRegistry.register(cmd)
        return cmd
    }

    fun command(name: String, action: Consumer<Array<String>>, vararg aliases: String): ScriptCustomCommand {
        return command(name, "Script command from $scriptName", action, *aliases)
    }

    // --- Custom HUD Elements ---

    fun hud(name: String, description: String, defaultX: Float, defaultY: Float): ScriptHudElement {
        val hud = ScriptHudElement(name, description, defaultX, defaultY)
        hud.setScriptContext(this)
        _registeredHudElements.add(hud)
        ClientContext.moduleRegistry.registerModule(hud)
        return hud
    }

    fun hud(name: String, defaultX: Float, defaultY: Float): ScriptHudElement {
        return hud(name, "Script HUD element from $scriptName", defaultX, defaultY)
    }

    // --- Timers ---

    /** Выполнить действие один раз через указанное число тиков (20 тиков = 1 секунда). Возвращает id задачи. */
    fun after(ticks: Int, action: Runnable): Int {
        return schedule(maxOf(1, ticks), true, action)
    }

    /** Выполнять действие каждые N тиков. Возвращает id задачи. */
    fun every(ticks: Int, action: Runnable): Int {
        return schedule(maxOf(1, ticks), false, action)
    }

    private fun schedule(ticks: Int, once: Boolean, action: Runnable?): Int {
        if (action == null) return -1
        val task = ScriptTask(nextTaskId++, ticks, once, action)
        synchronized(taskLock) {
            tasks.add(task)
        }
        return task.id
    }

    /** Отменить задачу, созданную after()/every(), по её id. */
    fun cancelTask(id: Int) {
        synchronized(taskLock) {
            tasks.removeIf { it.id == id }
        }
    }

    fun cancelAllTasks() {
        synchronized(taskLock) {
            tasks.clear()
        }
    }

    /** Тиковый диспетчер таймеров. Вызывается ScriptManager'ом на каждом клиентском тике. */
    fun tickTasks() {
        val snapshot: List<ScriptTask>
        synchronized(taskLock) {
            snapshot = ArrayList(tasks)
        }

        for (task in snapshot) {
            if (--task.delay > 0) continue

            synchronized(taskLock) {
                if (!tasks.contains(task)) continue
            }

            try {
                task.action.run()
            } catch (t: Throwable) {
                val count = reportError("timer#${task.id}", t)
                if (count >= MAX_ERRORS) {
                    task.failed = true
                }
            }

            if (task.once || task.failed) {
                synchronized(taskLock) {
                    tasks.remove(task)
                }
            } else {
                task.delay = task.period
            }
        }
    }

    private class ScriptTask(
        val id: Int,
        val period: Int,
        val once: Boolean,
        val action: Runnable
    ) {
        var delay: Int = period
        @Volatile
        var failed: Boolean = false
    }

    // --- Error Reporting ---

    /**
     * Сообщает об ошибке скрипта: консоль + чат (не больше MAX_ERRORS сообщений на источник).
     * Возвращает текущее число ошибок данного источника.
     */
    fun reportError(source: String, error: Throwable?): Int {
        val count: Int
        synchronized(errorCounts) {
            count = (errorCounts[source] ?: 0) + 1
            errorCounts[source] = count
        }

        if (count <= MAX_ERRORS) {
            LOG.error(
                "[Collective-Scripting] $scriptName ($source) error #$count: " +
                    (error?.toString() ?: "unknown error")
            )
            error?.printStackTrace()
            val message = error?.message ?: "unknown error"
            ScriptManager.message("§c[Script] §f$scriptName §7($source): §c$message")
        }
        return count
    }

    fun getErrorCount(source: String): Int {
        synchronized(errorCounts) {
            return errorCounts[source] ?: 0
        }
    }

    // --- Utility ---

    /** Найти любой модуль клиента по имени (без учёта регистра). */
    fun getModule(name: String?): Module? {
        if (name == null) return null
        val clean = name.trim()
        val registry: ModuleRegistry = ClientContext.moduleRegistry
        for (m in registry.getRegistry()) {
            if (m.name.equals(clean, ignoreCase = true)) {
                return m
            }
        }
        return null
    }

    /** Выполнить код в основном (игровом) потоке. */
    fun runOnMainThread(action: Runnable?) {
        if (action != null) {
            mc.execute(action)
        }
    }

    // --- Game Automation & Actions API ---

    fun sendPacket(packet: Packet<*>?) {
        if (packet != null) {
            mc.connection?.send(packet)
        }
    }

    fun rotateSilent(yaw: Float, pitch: Float) {
        ClientContext.rotationManager.requestRotation(floatArrayOf(yaw, pitch), 1)
    }

    fun rotateSilent(yaw: Float, pitch: Float, priority: Int) {
        ClientContext.rotationManager.requestRotation(floatArrayOf(yaw, pitch), priority)
    }

    fun rotateInstant(yaw: Float, pitch: Float) {
        ClientContext.rotationManager.requestRotation(yaw, pitch, 100, true)
    }

    fun rotateTo(target: Vec3) {
        val player = mc.player ?: return
        val eyes = player.eyePosition
        val dx = target.x - eyes.x
        val dy = target.y - eyes.y
        val dz = target.z - eyes.z
        val horizontal = sqrt(dx * dx + dz * dz)
        val yaw = (Math.toDegrees(atan2(dz, dx)) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizontal))).toFloat()
        rotateSilent(yaw, pitch)
    }

    fun rotateTo(x: Double, y: Double, z: Double) {
        rotateTo(Vec3(x, y, z))
    }

    fun pathTo(x: Int, y: Int, z: Int) {
        val bridge: BaritoneBridge? = ClientContext.baritoneBridge
        if (bridge != null && bridge.isAvailable()) {
            bridge.pathTo(BlockPos(x, y, z))
        }
    }

    fun cancelPath() {
        val bridge: BaritoneBridge? = ClientContext.baritoneBridge
        if (bridge != null && bridge.isAvailable()) {
            bridge.cancelPathing()
        }
    }

    fun sendChatMessage(message: String?) {
        if (mc.player != null && message != null) {
            ChatUtils.sendChatOrCommand(message)
        }
    }

    fun executeClientCommand(commandWithPrefix: String?) {
        var input = commandWithPrefix?.trim() ?: ""
        val prefix = CommandRegistry.getPrefixCharacter()
        if (input.isNotEmpty() && input[0] == prefix) {
            input = input.substring(1)
        }
        CommandRegistry.executeCommand(input)
    }

    // --- Output & Logging ---

    fun print(msg: Any?) {
        ScriptManager.message("§7[§d$scriptName§7] §f$msg")
    }

    fun log(msg: Any?) {
        LOG.info("[$scriptName] $msg")
    }

    // --- Cleanup & Lifecycle ---

    fun unregisterAll() {
        cancelAllTasks()

        try {
            this.events.unregisterAll()
        } catch (_: Throwable) {
        }

        val registry: ModuleRegistry = ClientContext.moduleRegistry
        val stale = ArrayList<Module>()
        for (m in _registeredModules) {
            try {
                if (m.isToggled()) {
                    m.disable()
                }
            } catch (_: Throwable) {
            }
            registry.unregisterModule(m)
            stale.add(m)
        }
        _registeredModules.clear()

        for (c in _registeredCommands) {
            ClientContext.commandRegistry.unregister(c)
        }
        _registeredCommands.clear()

        for (h in _registeredHudElements) {
            if (!stale.contains(h)) {
                try {
                    if (h.isToggled()) {
                        h.disable()
                    }
                } catch (_: Throwable) {
                }
                registry.unregisterModule(h)
            }
        }
        _registeredHudElements.clear()

        synchronized(errorCounts) {
            errorCounts.clear()
        }
    }

    val registeredModules: List<Module>
        get() = _registeredModules

    val registeredCommands: List<Command>
        get() = _registeredCommands

    val registeredHudElements: List<HudWidgetModule>
        get() = _registeredHudElements

    /** Прокси EventBus для скриптов: все подписки трекаются и снимаются при unregisterAll(). */
    class EventBridge : EventDispatcher {
        private val trackedSubscribers: MutableList<Any> = ArrayList()
        private val trackedListeners: MutableList<EventListener> = ArrayList()
        private val trackedStaticClasses: MutableList<Class<*>> = ArrayList()

        override fun subscribe(subscriber: Any) {
            ClientAccess.eventBus.subscribe(subscriber)
            synchronized(trackedSubscribers) {
                if (!trackedSubscribers.contains(subscriber)) {
                    trackedSubscribers.add(subscriber)
                }
            }
        }

        override fun unsubscribe(subscriber: Any) {
            ClientAccess.eventBus.unsubscribe(subscriber)
            synchronized(trackedSubscribers) {
                trackedSubscribers.remove(subscriber)
            }
        }

        override fun subscribeStatic(listenerClass: Class<*>) {
            ClientAccess.eventBus.subscribeStatic(listenerClass)
            synchronized(trackedStaticClasses) {
                if (!trackedStaticClasses.contains(listenerClass)) {
                    trackedStaticClasses.add(listenerClass)
                }
            }
        }

        override fun unsubscribeStatic(listenerClass: Class<*>) {
            ClientAccess.eventBus.unsubscribeStatic(listenerClass)
            synchronized(trackedStaticClasses) {
                trackedStaticClasses.remove(listenerClass)
            }
        }

        override fun subscribeListener(listener: EventListener) {
            ClientAccess.eventBus.subscribeListener(listener)
            synchronized(trackedListeners) {
                if (!trackedListeners.contains(listener)) {
                    trackedListeners.add(listener)
                }
            }
        }

        override fun unsubscribeListener(listener: EventListener) {
            ClientAccess.eventBus.unsubscribeListener(listener)
            synchronized(trackedListeners) {
                trackedListeners.remove(listener)
            }
        }

        override fun post(event: Any): Any {
            return ClientAccess.eventBus.post(event)
        }

        override fun postCancellable(event: Cancellable): Cancellable {
            return ClientAccess.eventBus.postCancellable(event)
        }

        /** Снять все подписки, сделанные скриптом через этот мост. */
        fun unregisterAll() {
            val subscribers: List<Any>
            val listeners: List<EventListener>
            val staticClasses: List<Class<*>>
            synchronized(trackedSubscribers) {
                subscribers = ArrayList(trackedSubscribers)
                trackedSubscribers.clear()
            }
            synchronized(trackedListeners) {
                listeners = ArrayList(trackedListeners)
                trackedListeners.clear()
            }
            synchronized(trackedStaticClasses) {
                staticClasses = ArrayList(trackedStaticClasses)
                trackedStaticClasses.clear()
            }
            for (subscriber in subscribers) {
                try {
                    ClientAccess.eventBus.unsubscribe(subscriber)
                } catch (_: Throwable) {
                }
            }
            for (listener in listeners) {
                try {
                    ClientAccess.eventBus.unsubscribeListener(listener)
                } catch (_: Throwable) {
                }
            }
            for (listenerClass in staticClasses) {
                try {
                    ClientAccess.eventBus.unsubscribeStatic(listenerClass)
                } catch (_: Throwable) {
                }
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ScriptContext::class.java)

        /** Сколько ошибок допускается от одного источника (модуль/таймер), прежде чем он будет остановлен. */
        const val MAX_ERRORS: Int = 5
    }
}
