package me.collective.addon

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.Command
import me.collective.client.events.bus.EventBus
import me.collective.client.integrations.baritone.BaritoneBridge
import me.collective.client.managers.CommandRegistry
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module

abstract class CollectiveAddon {
    @JvmField
    protected val access: MinecraftAccess = MinecraftAccess()

    private var _name: String? = null
    private var _version: String = "1.0.0"
    private var _author: String = "Unknown"
    private var _description: String = ""
    private var _website: String? = null

    private val _registeredModules: MutableList<Module> = ArrayList()
    private val _registeredCommands: MutableList<Command> = ArrayList()
    private val _subscribedListeners: MutableList<Any> = ArrayList()

    /** Простой держатель Minecraft-инстанса для аддонов. */
    class MinecraftAccess {
        fun mc(): net.minecraft.client.Minecraft = ClientAccess.collectiveMinecraft
    }

    fun initMetadata(name: String?, version: String?, author: String?, description: String?, website: String?) {
        this._name = name
        if (version != null) this._version = version
        if (author != null) this._author = author
        if (description != null) this._description = description
        this._website = website
    }

    /** Вызывается при загрузке аддона. */
    abstract fun onInitialize()

    /** Вызывается при выгрузке/перезагрузке аддона. */
    open fun onShutdown() {
        // Автоматически очищаем зарегистрированные модули и команды при выгрузке аддона
        val modules: ModuleRegistry = ClientContext.moduleRegistry
        for (m in _registeredModules) {
            try {
                if (m.isToggled()) {
                    m.disable()
                }
            } catch (_: Throwable) {
            }
            modules.unregisterModule(m)
        }
        _registeredModules.clear()

        val commands: CommandRegistry = ClientContext.commandRegistry
        for (c in _registeredCommands) {
            commands.unregister(c)
        }
        _registeredCommands.clear()

        // Отписываем слушателей, подписанных через subscribeEvents (иначе утечка
        // подписчиков и classloader'а аддона после выгрузки).
        val subscribedListeners: List<Any>
        synchronized(_subscribedListeners) {
            subscribedListeners = ArrayList(_subscribedListeners)
            _subscribedListeners.clear()
        }
        for (listener in subscribedListeners) {
            try {
                ClientAccess.eventBus.unsubscribe(listener)
            } catch (_: Throwable) {
            }
        }

        // Отписываем аддон от EventBus
        ClientAccess.eventBus.unsubscribe(this)
    }

    /** Регистрация обычного или HUD модуля. */
    fun registerModule(module: Module) {
        _registeredModules.add(module)
        ClientContext.moduleRegistry.registerModule(module)
    }

    /** Регистрация HUD элемента. */
    fun registerHud(hudModule: HudWidgetModule) {
        registerModule(hudModule)
    }

    /** Регистрация команды чата. */
    fun registerCommand(command: Command) {
        _registeredCommands.add(command)
        ClientContext.commandRegistry.register(command)
    }

    /** Подписка объекта на событийную шину. */
    fun subscribeEvents(listener: Any) {
        synchronized(_subscribedListeners) {
            if (!_subscribedListeners.contains(listener)) {
                _subscribedListeners.add(listener)
            }
        }
        ClientAccess.eventBus.subscribe(listener)
    }

    /** Отписка от событийной шины. */
    fun unsubscribeEvents(listener: Any) {
        synchronized(_subscribedListeners) {
            _subscribedListeners.remove(listener)
        }
        ClientAccess.eventBus.unsubscribe(listener)
    }

    // --- Удобный доступ к внутренним системам клиента ---

    val moduleRegistry: ModuleRegistry
        get() = ClientContext.moduleRegistry

    val commandRegistry: CommandRegistry
        get() = ClientContext.commandRegistry

    val eventBus: EventBus
        get() = ClientAccess.eventBus

    val baritone: BaritoneBridge
        get() = ClientContext.baritoneBridge

    val rotations: RotationManager
        get() = ClientContext.rotationManager

    val name: String
        get() = _name ?: javaClass.simpleName ?: "Unknown"

    val version: String
        get() = _version

    val author: String
        get() = _author

    val description: String
        get() = _description

    val website: String?
        get() = _website

    val registeredModules: MutableList<Module>
        get() = _registeredModules

    val registeredCommands: MutableList<Command>
        get() = _registeredCommands
}
