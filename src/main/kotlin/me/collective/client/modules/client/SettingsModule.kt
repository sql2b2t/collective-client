package me.collective.client.modules.client

import me.collective.client.gui.screens.PresetScreen
import me.collective.client.audio.BuiltinSound
import me.collective.client.utils.collections.NamedIdentifier
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PermanentModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

class SettingsModule : PermanentModule("Settings", "Client's global settings.", ModuleCategory.CLIENT) {
    @JvmField
    val prefixSetting: Setting<String> = add(StringSetting("Prefix", "."))
    private val startupSetting: Setting<NamedIdentifier> = add(IdentifierSetting("Startup", BuiltinSound.BLIP.identifier))
    private val presetsSetting: Setting<Boolean> = add(BooleanSetting("Presets", true))

    init {
        prefixSetting.setChangeCallback {
            val prefixValue = prefixSetting.value
            updateCommandPrefix(prefixValue)
        }
        presetsSetting.setChangeCallback {
            if (!presetsSetting.value) {
                PresetScreen().open()
                presetsSetting.value = true
            }
        }
        prefixSetting.setExcludedFromConfig(true)
    }

    fun getStartupSound(): NamedIdentifier {
        return this.startupSetting.value
    }

    private fun updateCommandPrefix(prefixValue: String) {
        if (prefixValue != CommandRegistry.getPrefix()) {
            val updatedPrefix = prefixSetting.value
            CommandRegistry.setPrefix(updatedPrefix)
        }
    }

    enum class MultiplierOption(private val displayName: String, private val multiplier: Int) : NamedEntry {
        NONE("None", 0),
        X2("X2", 2),
        X4("X4", 4),
        X8("X8", 8);

        override fun getName(): String {
            return this.displayName
        }

        fun getMultiplier(): Int {
            return this.multiplier
        }
    }
}
