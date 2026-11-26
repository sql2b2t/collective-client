package me.collective.client.render

import me.collective.client.ClientAccess
import net.fabricmc.loader.api.FabricLoader

object GuiScaleUtils : ClientAccess {
    private val ADVANCED_UI_SCALE_LOADED: Boolean = FabricLoader.getInstance().isModLoaded("advanced-ui-scale")
    private var previousGuiScale: Double = -1.0
    private var scaleMultiplier: Double = 1.0

    @JvmStatic
    fun getScaledHeight(): Int {
        return (ClientAccess.collectiveMinecraft.window.guiScaledHeight / scaleMultiplier + 1.0).toInt()
    }

    @JvmStatic
    fun getScaledWidth(): Int {
        return (ClientAccess.collectiveMinecraft.window.guiScaledWidth / scaleMultiplier + 1.0).toInt()
    }

    @JvmStatic
    fun restoreGuiScale() {
        if (previousGuiScale < 0.0) {
            return
        }
        ClientAccess.collectiveMinecraft.window.guiScale = previousGuiScale.toInt()
        previousGuiScale = -1.0
        scaleMultiplier = 1.0
    }

    @JvmStatic
    fun isAdvancedUiScaleLoaded(): Boolean {
        return ADVANCED_UI_SCALE_LOADED
    }

    @JvmStatic
    fun getScaleMultiplier(): Double {
        return scaleMultiplier
    }

    @JvmStatic
    fun applyDefaultGuiScale() {
        applyGuiScale(1.0f)
    }

    @JvmStatic
    fun calculateGuiScale(): Int {
        val minecraft = ClientAccess.collectiveMinecraft
        return minecraft.window.calculateScale(2, minecraft.isEnforceUnicode)
    }

    @JvmStatic
    fun applyGuiScale(scale: Float) {
        if (ADVANCED_UI_SCALE_LOADED) {
            return
        }
        val window = ClientAccess.collectiveMinecraft.window
        if (previousGuiScale < 0.0) {
            previousGuiScale = window.guiScale.toDouble()
        }
        scaleMultiplier = scale.toDouble()
        window.guiScale = calculateGuiScale()
    }

    @JvmStatic
    fun calculateScaleMultiplier(scale: Float): Double {
        return if (ADVANCED_UI_SCALE_LOADED) {
            1.0
        } else {
            calculateGuiScale().toDouble() / ClientAccess.collectiveMinecraft.window.guiScale * scale
        }
    }
}
