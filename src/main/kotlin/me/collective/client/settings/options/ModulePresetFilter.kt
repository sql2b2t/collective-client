package me.collective.client.settings.options

import me.collective.client.ClientContext
import me.collective.client.config.LegacyBranding
import me.collective.client.modules.Module
import me.collective.client.modules.render.LogoutSpotsModule

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.function.Predicate

class ModulePresetFilter private constructor() {
    fun retainScalarSettings(modulesJson: JsonObject) {
        val moduleValues = modulesJson.asMap().values.iterator()

        while (moduleValues.hasNext()) {
            val moduleElement = moduleValues.next()
            if (moduleElement is JsonObject) {
                moduleElement.remove("key")

                if (moduleElement.has("settings")) {
                    val settingsElement = moduleElement.get("settings")
                    if (settingsElement.isJsonObject) {
                        val nestedSettingKeys = ArrayList<String>()
                        val settingsJson = settingsElement.asJsonObject
                        val settingEntries = settingsJson.asMap().entries.iterator()

                        while (settingEntries.hasNext()) {
                            val settingEntry = settingEntries.next()
                            val settingValue = settingEntry.value
                            if (settingValue.isJsonObject) {
                                nestedSettingKeys.add(settingEntry.key)
                            }
                        }

                        for (removalKey in nestedSettingKeys) {
                            settingsElement.asJsonObject.remove(removalKey)
                        }
                    }
                }
            }
        }
    }

    fun retainModuleSettings(modulesJson: JsonObject, includedModules: Predicate<Module>) {
        val moduleNames = modulesJson.asMap().keys.iterator()

        while (moduleNames.hasNext()) {
            val moduleName = moduleNames.next()

            val moduleJson = modulesJson.get(moduleName)
            if (moduleJson is JsonObject) {
                val modules = ClientContext.moduleRegistry
                val modulePredicate = Predicate<Module> { module ->
                    module.configName == LegacyBranding.canonicalModuleName(moduleName)
                }

                val matchingModule = modules.findFirst(modulePredicate)

                if (!matchingModule.isEmpty) {
                    val includeModule = includedModules.test(matchingModule.get() as Module)
                    if (!includeModule || matchingModule.orElse(null) is LogoutSpotsModule) {
                        moduleJson.remove("enabled")
                    }

                    moduleJson.remove("key")

                    if (moduleJson.has("settings")) {
                        val settingsElement = moduleJson.get("settings")
                        if (settingsElement.isJsonObject) {
                            val filteredSettingKeys = ArrayList<String>()
                            val settingsJson = settingsElement.asJsonObject
                            val settingEntries = settingsJson.asMap().entries.iterator()

                            while (settingEntries.hasNext()) {
                                if (includeModule) {
                                    break
                                }

                                val settingEntry = settingEntries.next()
                                val settingName = settingEntry.key
                                val settingValue = settingEntry.value

                                if (!isVisualSetting(settingName, settingValue)) {
                                    filteredSettingKeys.add(settingEntry.key)
                                }
                            }

                            for (removalKey in filteredSettingKeys) {
                                settingsElement.asJsonObject.remove(removalKey)
                            }
                        }
                    }
                }
            }
        }
    }

    fun retainKeyBindings(modulesJson: JsonObject) {
        val moduleValues = modulesJson.asMap().values.iterator()

        while (moduleValues.hasNext()) {
            val moduleElement = moduleValues.next()
            if (moduleElement is JsonObject) {
                moduleElement.remove("enabled")

                moduleElement.remove("settings")
            }
        }
    }

    fun isVisualSetting(settingName: String, settingValue: JsonElement): Boolean {
        return settingName.equals("render", ignoreCase = true) ||
         settingName.equals("linewidth", ignoreCase = true) ||
         settingValue.isJsonObject
    }

    companion object {
        @JvmField
        val INSTANCE = ModulePresetFilter()
    }
}
