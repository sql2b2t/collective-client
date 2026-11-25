package me.collective.client.settings.options

import me.collective.client.ClientContext
import me.collective.client.config.JsonUtils
import me.collective.client.managers.ConfigurationManager
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.ColorsModule
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.client.HUDModule
import me.collective.client.modules.client.NotificationsModule
import me.collective.client.modules.client.UIModule
import me.collective.client.modules.misc.KillEffectsModule
import me.collective.client.modules.misc.SwingModule
import me.collective.client.modules.render.LogoutSpotsModule
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.nio.file.Path
import java.util.HashSet
import java.util.Locale
import java.util.function.Predicate

enum class PresetCategory(private val displayName: String) : JsonConfigurable, NamedEntry {
    MODULES("Modules") {
        override fun fromJson(json: JsonElement) {
            if (!json.isJsonObject) {
                return
            }
            val filter = ModulePresetFilter.INSTANCE
            // Work on a copy: the stored preset contents must not be mutated.
            val jsonObject = json.asJsonObject.deepCopy()

            filter.retainScalarSettings(jsonObject)
            super.fromJson(jsonObject)
        }

        override fun toJson(): JsonElement {
            val preset = JsonObject()
            this.writeMetadata(preset)
            val moduleRegistry = ClientContext.moduleRegistry
            val modules = (moduleRegistry.getRegistry() as List<*>).iterator()

            while (modules.hasNext()) {
                val module = modules.next() as Module
                if (module !is HudWidgetModule && module !is FontsModule) {
                    val moduleJson = JsonObject()

                    moduleJson.addProperty("enabled", module.isToggled())
                    val settingsJson = JsonObject()

                    val settings = module.settings.iterator()

                    while (settings.hasNext()) {
                        val setting = settings.next()
                        if (setting !is ColorSetting) {
                            if (!setting.isGroupOnly()) {
                                if (!setting.isExcludedFromConfig()) {
                                    val settingName = setting.getConfigName()
                                    val settingJson = setting.toJson()
                                    settingsJson.add(settingName, settingJson)
                                }
                            }
                        }
                    }

                    if (!JsonUtils.isRecursivelyEmpty(settingsJson)) {
                        moduleJson.add("settings", settingsJson)
                    }

                    val moduleName = module.getConfigName()
                    preset.add(moduleName, moduleJson)
                }
            }

            return preset
        }
    },
    BINDS("Binds") {
        override fun fromJson(json: JsonElement) {
            if (!json.isJsonObject) {
                return
            }
            val filter = ModulePresetFilter.INSTANCE
            // Work on a copy: the stored preset contents must not be mutated.
            val jsonObject = json.asJsonObject.deepCopy()

            filter.retainKeyBindings(jsonObject)
            super.fromJson(jsonObject)
        }

        override fun toJson(): JsonElement {
            val preset = JsonObject()
            this.writeMetadata(preset)
            val moduleRegistry = ClientContext.moduleRegistry
            val modules = (moduleRegistry.getRegistry() as List<*>).iterator()

            while (modules.hasNext()) {
                val module = modules.next() as Module
                if (module !is HudWidgetModule) {
                    val moduleJson = JsonObject()

                    var keybind = module.getKeybind()

                    moduleJson.addProperty("key", keybind.code())

                    keybind = module.getKeybind()

                    val keyMode = keybind.mode()
                    val modeName = keyMode.name
                    val modeValue = modeName.lowercase(Locale.ROOT)
                    moduleJson.addProperty("state", modeValue)

                    keybind = module.getKeybind()

                    moduleJson.addProperty("mouse", keybind.mouse())
                    val moduleName = module.getConfigName()
                    preset.add(moduleName, moduleJson)
                }
            }

            return preset
        }
    },
    HUD("Hud") {
        override fun fromJson(json: JsonElement) {
            if (!json.isJsonObject) {
                return
            }
            // Work on a copy: the stored preset contents must not be mutated.
            val preset = json.asJsonObject.deepCopy()
            val moduleRegistry = ClientContext.moduleRegistry
            val modules = (moduleRegistry.getRegistry() as List<*>).iterator()

            while (modules.hasNext()) {
                val module = modules.next() as Module
                if (this.isHudModule(module)) {
                    try {
                        val moduleName = module.getConfigName()
                        val moduleElement = preset.get(moduleName)
                        if (moduleElement == null || !moduleElement.isJsonObject) {
                            continue
                        }
                        val moduleJson = moduleElement.asJsonObject
                        val bindingKeys = arrayOf("key", "state", "mouse")
                        JsonUtils.removeProperties(moduleJson, *bindingKeys)
                        module.fromJson(moduleJson)
                    } catch (ignored: Exception) {
                    }
                }
            }
        }

        override fun toJson(): JsonElement {
            val preset = JsonObject()
            this.writeMetadata(preset)
            val moduleRegistry = ClientContext.moduleRegistry
            val modules = (moduleRegistry.getRegistry() as List<*>).iterator()

            while (modules.hasNext()) {
                val module = modules.next() as Module
                if (this.isHudModule(module)) {
                    val moduleElement = module.toJson()
                    val moduleJson = moduleElement.getAsJsonObject()
                    val bindingKeys = arrayOf("key", "state", "mouse")
                    JsonUtils.removeProperties(moduleJson, *bindingKeys)
                    val moduleName = module.getConfigName()
                    preset.add(moduleName, moduleJson)
                }
            }

            return preset
        }

        private fun isHudModule(module: Module): Boolean {
            return module is HudWidgetModule || module is HUDModule
        }
    },
    COLORS("Colors") {
        override fun fromJson(json: JsonElement) {
            if (!json.isJsonObject) {
                return
            }
            val filter = ModulePresetFilter.INSTANCE
            // Work on a copy: the stored preset contents must not be mutated.
            val jsonObject = json.asJsonObject.deepCopy()
            val includeModule = Predicate<Module> { false }
            filter.retainModuleSettings(jsonObject, includeModule)
            super.fromJson(jsonObject)
        }

        override fun toJson(): JsonElement {
            val preset = JsonObject()
            this.writeMetadata(preset)
            val moduleRegistry = ClientContext.moduleRegistry
            val modules = (moduleRegistry.getRegistry() as List<*>).iterator()

            while (modules.hasNext()) {
                val module = modules.next() as Module
                if (module !is HudWidgetModule) {
                    val moduleJson = JsonObject()
                    val settingsJson = JsonObject()
                    val settings = module.settings.iterator()

                    while (settings.hasNext()) {
                        val setting = settings.next()
                        if (setting is ColorSetting) {
                            if (!setting.isExcludedFromConfig()) {
                                val settingName = setting.getConfigName()
                                val settingJson = setting.toJson()
                                settingsJson.add(settingName, settingJson)
                            }
                        }
                    }

                    moduleJson.add("settings", settingsJson)
                    if (!JsonUtils.isRecursivelyEmpty(moduleJson)) {
                        val moduleName = module.getConfigName()
                        preset.add(moduleName, moduleJson)
                    }
                }
            }

            return preset
        }
    },
    VISUALS("Visuals") {
        private val VISUAL_MODULE_CLASSES: Set<Class<*>> = HashSet(
            listOf(
                UIModule::class.java,
                FontsModule::class.java,
                HUDModule::class.java,
                ColorsModule::class.java,
                NotificationsModule::class.java,
                SwingModule::class.java,
                KillEffectsModule::class.java
            )
        )

        override fun fromJson(json: JsonElement) {
            if (!json.isJsonObject) {
                return
            }
            val filter = ModulePresetFilter.INSTANCE
            // Work on a copy: the stored preset contents must not be mutated.
            val jsonObject = json.asJsonObject.deepCopy()
            val includeModule = Predicate<Module> { module -> isVisualModule(module) }
            filter.retainModuleSettings(jsonObject, includeModule)
            super.fromJson(jsonObject)
        }

        override fun toJson(): JsonElement {
            val preset = JsonObject()
            this.writeMetadata(preset)
            val moduleRegistry = ClientContext.moduleRegistry
            val modules = (moduleRegistry.getRegistry() as List<*>).iterator()

            while (modules.hasNext()) {
                val module = modules.next() as Module
                if (module !is HudWidgetModule) {
                    val moduleJson = JsonObject()
                    val visualModule = this.isVisualModule(module)
                    val logoutSpots = module is LogoutSpotsModule
                    if (visualModule && !logoutSpots) {
                        moduleJson.addProperty("enabled", module.isToggled())
                    }

                    val settingsJson = JsonObject()

                    val settings = module.settings.iterator()

                    while (settings.hasNext()) {
                        val setting = settings.next()

                        if (!setting.isExcludedFromConfig()) {
                            if (this.isVisualSetting(setting) || visualModule) {
                                val settingName = setting.getConfigName()
                                val settingJson = setting.toJson()
                                settingsJson.add(settingName, settingJson)
                            }
                        }
                    }

                    moduleJson.add("settings", settingsJson)
                    if (!JsonUtils.isRecursivelyEmpty(moduleJson)) {
                        val moduleName = module.getConfigName()
                        preset.add(moduleName, moduleJson)
                    }
                }
            }

            return preset
        }

        fun isVisualModule(module: Module): Boolean {
            if (module is HudWidgetModule) {
                return true
            }

            if (VISUAL_MODULE_CLASSES.contains(module.javaClass)) {
                return true
            }

            if (module.category != ModuleCategory.RENDER) {
                if (module.category != ModuleCategory.HUD) {
                    return false
                }
            }

            return true
        }

        fun isVisualSetting(setting: Setting<*>): Boolean {
            val settingName = setting.name

            if (settingName.equals("render", ignoreCase = true)) {
                return true
            }

            if (settingName.equals("linewidth", ignoreCase = true)) {
                return true
            }

            if (!settingName.equals("colormode", ignoreCase = true)) {
                if (!settingName.equals("rendermode", ignoreCase = true)) {
                    return setting is ColorSetting
                }
            }

            return true
        }
    },
    MACRO("Macro") {
        override fun fromJson(json: JsonElement) {
            val registryForLock = ClientContext.macroRegistry
            synchronized(registryForLock.getRegistry()) {
                val registryForClear = ClientContext.macroRegistry
                (registryForClear.getRegistry() as MutableList<*>).clear()
                val registryForLoad = ClientContext.macroRegistry
                registryForLoad.fromJson(json)
            }
        }

        override fun toJson(): JsonElement {
            val macroRegistry = ClientContext.macroRegistry
            return macroRegistry.toJson()
        }
    },
    ALL("All") {
        override fun toJson(): JsonElement {
            val moduleRegistry = ClientContext.moduleRegistry
            val modulesJson = moduleRegistry.toJson()
            return modulesJson.getAsJsonObject()
        }
    };

    val directory: Path = ConfigurationManager.PRESETS_DIRECTORY.resolve(displayName.lowercase(Locale.ROOT))

    override fun getName(): String = displayName

    override fun fromJson(json: JsonElement) {
        val moduleRegistry = ClientContext.moduleRegistry
        moduleRegistry.fromJson(json)
    }

    fun writeMetadata(json: JsonObject) {
        json.addProperty("client", "collective")

        val categoryName = this.getName()
        val categoryValue = categoryName.lowercase(Locale.ROOT)
        json.addProperty("category", categoryValue)
    }

    companion object {
        @JvmStatic
        fun findByName(name: String?): PresetCategory? {
            for (category in entries) {
                val categoryName = category.getName()
                if (categoryName.equals(name, ignoreCase = true)) {
                    return category
                }
            }

            return null
        }
    }
}
