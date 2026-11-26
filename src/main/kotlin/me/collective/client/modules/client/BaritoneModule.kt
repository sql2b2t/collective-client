package me.collective.client.modules.client

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.MinecraftTickEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.ColorUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PermanentModule
import me.collective.client.modules.movement.SafeWalkModule
import me.collective.client.modules.movement.StepModule
import me.collective.client.modules.player.AutoToolModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.Setting

import baritone.api.BaritoneAPI
import baritone.api.Settings
import java.awt.Color
import java.util.function.Function
import java.util.function.Supplier

class BaritoneModule : PermanentModule("Baritone", "Manages baritone settings.", ModuleCategory.CLIENT) {
    private val freeLookSetting: Setting<Boolean> = add(BooleanSetting("FreeLook", true))
    private val backFillSetting: Setting<Boolean> = add(BooleanSetting("BackFill", false))
    private val logOnArrivalSetting: Setting<Boolean> = add(BooleanSetting("LogOnArrival", false))
    private val assumeEnabledSetting: Setting<Boolean> = add(BooleanSetting("AssumeEnabled", false))
    private val censorCoordsSetting: Setting<Boolean> = add(BooleanSetting("CensorCoords", false))
    private val replaceColorsSetting: Setting<Boolean>
    private val colorSetting: Setting<Color>
    private val goalSetting: Setting<Color>
    private var assumedSettings: List<AssumedSetting>? = null

    init {
        val replaceColors = BooleanSetting("ReplaceColors", true)
        replaceColors.markVisibilityDependency()
        replaceColorsSetting = add(replaceColors)
        colorSetting = add(ColorSetting("Color", Color(-4351489, true)).dependsOn(replaceColorsSetting))
        goalSetting = add(ColorSetting("Goal", Color(-4351489, true)).dependsOn(replaceColorsSetting))
        if (ClientContext.baritoneBridge.isAvailable()) {
            val chatControlSetting = BaritoneAPI.getSettings().chatControl
            chatControlSetting.value = false
        }
        val eventBus = ClientAccess.eventBus
        eventBus.subscribe(this)
    }

    private fun getAssumedSettings(): List<AssumedSetting> {
        var result = assumedSettings
        if (result == null) {
            result = AssumedSetting.create(step!!, autoTool!!, safeWalk!!)
            assumedSettings = result
        }
        return result
    }

    @EventHandler
    private fun onMinecraftTick(event: MinecraftTickEvent) {
        if (!ClientContext.baritoneBridge.isAvailable()) {
            return
        }
        val settings = BaritoneAPI.getSettings()
        settings.disconnectOnArrival.value = logOnArrivalSetting.value
        settings.backfill.value = backFillSetting.value
        settings.freeLook.value = freeLookSetting.value
        settings.censorCoordinates.value = censorCoordsSetting.value
        settings.censorRanCommands.value = censorCoordsSetting.value
        if (replaceColorsSetting.value) {
            val configuredPathColor = colorSetting.value
            val pathColor = ColorUtils.withAlpha(configuredPathColor, 255)
            val configuredGoalColor = goalSetting.value
            val goalColor = ColorUtils.withAlpha(configuredGoalColor, 255)
            settings.colorBestPathSoFar.value = pathColor
            settings.colorGoalBox.value = goalColor
            settings.colorInvertedGoalBox.value = goalColor
            settings.colorCurrentPath.value = pathColor
            settings.colorMostRecentConsidered.value = pathColor
            settings.colorBlocksToBreak.value = pathColor
            settings.colorBlocksToPlace.value = pathColor
            settings.colorBlocksToWalkInto.value = pathColor
            settings.colorNextPath.value = pathColor
            settings.pathRenderLineWidthPixels.value = 1.5f
            settings.goalRenderLineWidthPixels.value = 1.5f
        }
        if (assumeEnabledSetting.value) {
            getAssumedSettings().forEach(AssumedSetting::sync)
        }
    }

    private class AssumedSetting(
        private val settingSelector: Function<Settings, Settings.Setting<Boolean>>,
        private val moduleEnabled: Supplier<Boolean>
    ) {
        private var lastEnabledValue: Boolean = getSetting().value

        fun sync() {
            val enabled = moduleEnabled.get()
            if (enabled) {
                getSetting().value = true
                lastEnabledValue = true
            } else if (lastEnabledValue) {
                getSetting().value = false
                lastEnabledValue = false
            }
        }

        private fun getSetting(): Settings.Setting<Boolean> {
            return settingSelector.apply(BaritoneAPI.getSettings())
        }

        companion object {
            fun create(step: StepModule, autoTool: AutoToolModule, safeWalk: SafeWalkModule): List<AssumedSetting> {
                return listOf(
                    AssumedSetting(Function { settings -> settings.assumeStep }, Supplier { step.isToggled }),
                    AssumedSetting(Function { settings -> settings.assumeExternalAutoTool }, Supplier { autoTool.isToggled }),
                    AssumedSetting(Function { settings -> settings.assumeSafeWalk }, Supplier { safeWalk.isToggled })
                )
            }
        }
    }

    companion object {
        private var step: StepModule? = null
        private var autoTool: AutoToolModule? = null
        private var safeWalk: SafeWalkModule? = null

        @JvmStatic
        fun bindModuleDependencies(registry: ModuleRegistry) {
            step = registry.getModule(StepModule::class.java) as StepModule
            autoTool = registry.getModule(AutoToolModule::class.java) as AutoToolModule
            safeWalk = registry.getModule(SafeWalkModule::class.java) as SafeWalkModule
        }
    }
}
