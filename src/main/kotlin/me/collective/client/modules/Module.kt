package me.collective.client.modules

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.NamedFeature
import me.collective.client.events.ModuleToggleEvent
import me.collective.client.events.bus.EventBus
import me.collective.client.managers.AliasRegistry
import me.collective.client.render.text.LanguageTable
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.Setting
import me.collective.client.utils.Toggleable
import me.collective.client.utils.inventory.RegistryContainer
import net.minecraft.ChatFormatting
import java.util.ArrayList
import java.util.Arrays
import java.util.Objects
import java.util.function.Function
import java.util.function.UnaryOperator

open class Module(
    name: String,
    description: String,
    @get:JvmName("getCategoryField") val category: ModuleCategory,
    vararg aliases: String
) : NamedFeature(name), Toggleable, ClientAccess, RegistryContainer, JsonConfigurable {

    private val descriptionTranslationKey: String = "descriptions.modules." + this.name
    @get:JvmName("getSettingsField")
    val settings: MutableList<Setting<*>> = ArrayList()
    @get:JvmName("getAliasesField")
    val aliases: Array<String>
    private var _keybind: Keybind = Keybind.UNBOUND
    var keybind: Keybind
        @JvmName("getKeybindProp") get() = getKeybind()
        @JvmName("setKeybindProp") set(value) = setKeybind(value)
    private var enabled: Boolean = false
    private var drawn: Boolean = true
    private var workInProgress: Boolean = false
    private var ignoreEnabledState: Boolean = false

    init {
        this.description = description
        val suppliedAliases = aliases ?: emptyArray()
        val copy = Arrays.copyOf(suppliedAliases, suppliedAliases.size + 1)
        copy[suppliedAliases.size] = name
        this.aliases = copy
    }

    constructor(name: String, category: ModuleCategory, vararg aliases: String) : this(name, "", category, *aliases)

    open fun <S : Setting<*>> add(setting: S): S {
        this.register(setting)
        return setting
    }

    open fun <S : Setting<*>> add(setting: S, precedingSetting: Setting<*>): S {
        val insertionIndex = this.settings.indexOf(precedingSetting) + 1
        this.settings.add(insertionIndex, setting)
        return setting
    }

    open fun register(setting: Setting<*>): Boolean {
        return this.settings.add(setting)
    }

    open fun unregister(setting: Setting<*>): Boolean {
        return this.settings.remove(setting)
    }

    override fun register(entry: Any): Boolean {
        return if (entry is Setting<*>) this.register(entry) else false
    }

    override fun unregister(entry: Any): Boolean {
        return if (entry is Setting<*>) this.unregister(entry) else false
    }

    override fun getRegistry(): Collection<Any?> {
        return this.settings
    }

    override fun enable() {
        if (this.isToggled()) {
            return
        }
        this.enabled = true
        ClientAccess.eventBus.subscribe(this)
        val eventBus: EventBus = ClientAccess.eventBus
        val toggleEvent = ModuleToggleEvent(this)
        eventBus.post(toggleEvent)
        this.onToggle()
        this.onEnable()
    }

    override fun disable() {
        if (!this.isToggled()) {
            return
        }
        ClientAccess.eventBus.unsubscribe(this)
        this.enabled = false
        val eventBus: EventBus = ClientAccess.eventBus
        val toggleEvent = ModuleToggleEvent(this)
        eventBus.post(toggleEvent)
        this.onToggle()
        this.onDisable()
    }

    override fun isToggled(): Boolean {
        return this.enabled
    }

    override fun setToggled(toggled: Boolean) {
        if (toggled) {
            enable()
        } else {
            disable()
        }
    }

    var isToggled: Boolean
        @JvmName("isToggledProp") get() = isToggled()
        @JvmName("setToggledProp") set(value) = setToggled(value)

    var isDrawn: Boolean
        @JvmName("isDrawnProp") get() = isDrawn()
        @JvmName("setDrawnProp") set(value) = setDrawn(value)

    val isWip: Boolean
        @JvmName("isWipProp") get() = isWip()

    open fun getKeybind(): Keybind = this._keybind
    open fun setKeybind(keybind: Keybind) { this._keybind = keybind }
    fun getSettings(): MutableList<Setting<*>> = this.settings
    fun getCategory(): ModuleCategory = this.category
    fun getAliases(): Array<String> = this.aliases

    open fun onEnable() {
    }

    open fun onDisable() {
    }

    open fun onToggle() {
    }

    open fun getInfo(): String? {
        return null
    }

    fun isWip(): Boolean {
        return this.workInProgress
    }

    protected fun setWip(workInProgress: Boolean) {
        this.workInProgress = workInProgress
    }

    open fun isDrawn(): Boolean {
        return this.drawn
    }

    open fun setDrawn(drawn: Boolean) {
        this.drawn = drawn
    }

    override fun getDescription(): String {
        val languageTable: LanguageTable = ClientContext.languageTable
        val translationKey = this.descriptionTranslationKey
        val defaultDescription = super.getDescription()
        return languageTable.getTranslationOrDefault(translationKey, defaultDescription)
    }

    override fun fromJson(json: JsonElement) {
        val migrated = me.collective.client.config.LegacyBranding.migrateModule(name, json)
        if (migrated !is JsonObject) {
            return
        }
        val moduleJson = migrated
        if (moduleJson.has("settings") && moduleJson.get("settings") is JsonObject) {
            val settingsJson = moduleJson.getAsJsonObject("settings")
            for (setting in this.settings) {
                if (setting.isExcludedFromConfig || !settingsJson.has(setting.configName)) {
                    continue
                }
                try {
                    setting.fromJson(settingsJson.get(setting.configName))
                } catch (ignored: Exception) {
                }
            }
        }
        if (moduleJson.has("drawn")) {
            try {
                this.setDrawn(moduleJson.get("drawn").asBoolean)
            } catch (ignored: Exception) {
            }
        }
        if (moduleJson.has("key")) {
            try {
                val keyCode = moduleJson.get("key").asInt
                val mode = if (moduleJson.has("state")) {
                    Keybind.Mode.fromSerializedName(moduleJson.get("state").asString)
                } else {
                    Keybind.Mode.TOGGLE
                }
                this.modifyKeybind { kb -> kb.withCode(keyCode).withMode(mode) }
            } catch (ignored: Exception) {
            }
        }
        if (moduleJson.has("mouse")) {
            try {
                val mouse = moduleJson.get("mouse").asBoolean
                this.modifyKeybind { kb -> kb.withMouse(mouse) }
            } catch (ignored: Exception) {
            }
        }
        if (moduleJson.has("enabled") && !this.ignoreEnabledState) {
            try {
                this.setToggled(moduleJson.get("enabled").asBoolean)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun toJson(): JsonElement {
        val moduleJson = JsonObject()
        if (!this.ignoreEnabledState) {
            moduleJson.addProperty("enabled", this.enabled)
        }
        moduleJson.addProperty("key", this.keybind.code())
        moduleJson.addProperty("state", this.keybind.mode().serializedName)
        moduleJson.addProperty("mouse", this.keybind.mouse())
        moduleJson.addProperty("drawn", this.drawn)
        val settingsJson = JsonObject()
        for (setting in this.settings) {
            if (!setting.isGroupOnly && !setting.isExcludedFromConfig) {
                val configName = setting.configName
                val settingJson = setting.toJson()
                settingsJson.add(configName, settingJson)
            }
        }
        moduleJson.add("settings", settingsJson)
        return moduleJson
    }

    val infoString: String @JvmName("getInfoStringProp") get() = getInfoString()
    open fun getInfoString(): String {
        val aliasRegistry: AliasRegistry = ClientContext.aliasRegistry
        val moduleAlias: String = AliasRegistry.getModuleAlias(aliasRegistry, this)
        val info = this.getInfo()
        val infoSuffix = if (info != null) {
            val openingColor = ChatFormatting.GRAY.toString()
            val infoColor = ChatFormatting.WHITE.toString()
            val closingColor = ChatFormatting.GRAY.toString()
            "$openingColor [$infoColor$info$closingColor]"
        } else {
            ""
        }
        return moduleAlias + infoSuffix
    }

    fun modifyKeybind(modifier: Function<Keybind, Keybind>) {
        this.keybind = modifier.apply(this.keybind)
    }

    fun modifyKeybind(modifier: (Keybind) -> Keybind) {
        this.keybind = modifier(this.keybind)
    }

    val configName: String @JvmName("getConfigNameProp") get() = getConfigName()

    override fun getConfigName(): String {
        return this.name
    }

    protected fun ignoreState() {
        this.ignoreEnabledState = true
    }
}
