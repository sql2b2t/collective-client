package me.collective.client.modules

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.config.EnumJsonConverter
import me.collective.client.gui.screens.HudEditorScreen
import me.collective.client.modules.client.HUDModule
import me.collective.client.modules.client.HudColorMode
import me.collective.client.render.HudAnchorGroup
import me.collective.client.render.HudHorizontalAlignment
import me.collective.client.render.HudVerticalAlignment
import me.collective.client.render.HudWidgetRenderer
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HudAnchor
import java.awt.Color

abstract class HudWidgetModule(name: String, vararg aliases: String) : Module(name, ModuleCategory.HUD, *aliases) {

    @JvmField
    protected var _renderer: HudWidgetRenderer? = null

    var renderer: HudWidgetRenderer
        @JvmName("getRendererProp") get() = _renderer!!
        @JvmName("setRendererProp") set(value) { _renderer = value }

    fun getRenderer(): HudWidgetRenderer = this._renderer!!
    fun setRenderer(renderer: HudWidgetRenderer) { this._renderer = renderer }

    override fun isDrawn(): Boolean {
        return false
    }

    fun getAnchorIndex(): Int {
        val ren = this._renderer ?: return -1
        val anchorGroup = ClientContext.getHudEditor().getAnchorGroup(ren.anchor) ?: return -1
        var index = 0
        for (candidate in anchorGroup.widgets) {
            if (candidate.widgetModule == this) {
                return index
            }
            index++
        }
        return -1
    }

    override fun fromJson(json: JsonElement) {
        super.fromJson(json)
        var config = json.asJsonObject
        if (config.has("hud")) {
            config = config.getAsJsonObject("hud")
            val codec = anchorCodec
            val ren = this._renderer
            this.setAnchor(codec.fromJson(config.get("anchor")))
            if (ren != null && ren.anchor == HudAnchor.NONE) {
                val horizontalOrdinal = if (config.has("h")) config.get("h").asInt else 0
                val verticalOrdinal = if (config.has("v")) config.get("v").asInt else 0
                ren.horizontalAlignment = HudHorizontalAlignment.values()[horizontalOrdinal]
                ren.verticalAlignment = HudVerticalAlignment.values()[verticalOrdinal]
                ren.setX(config.get("x").asFloat, false)
                ren.setY(config.get("y").asFloat, false)
            }
            ClientContext.getHudEditor().requestDirectionRefresh()
        }
    }

    override fun toJson(): JsonElement {
        val root = JsonObject()
        root.addProperty("enabled", this.isToggled())
        val settingsJson = JsonObject()
        for (setting in this.settings) {
            if (!setting.isGroupOnly && !setting.isExcludedFromConfig) {
                val configName = setting.configName
                val settingJson = setting.toJson()
                settingsJson.add(configName, settingJson)
            }
        }
        root.add("settings", settingsJson)
        val hudJson = JsonObject()
        val codec = anchorCodec
        val ren = this._renderer
        val anchor = ren?.anchor ?: HudAnchor.NONE
        hudJson.add("anchor", codec.toJson(anchor))
        hudJson.addProperty("index", this.getAnchorIndex())
        if (ren != null && ren.anchor == HudAnchor.NONE) {
            hudJson.addProperty("h", ren.horizontalAlignment.ordinal)
            hudJson.addProperty("v", ren.verticalAlignment.ordinal)
            hudJson.addProperty("x", ren.relativeX)
            hudJson.addProperty("y", ren.relativeY)
        }
        root.add("hud", hudJson)
        return root
    }

    open fun calculateDimensions(): FloatArray {
        throw RuntimeException()
    }

    open fun renderContent(poses: PoseStack) {
    }

    fun setAnchor(anchor: HudAnchor) {
        val ren = this._renderer ?: return
        ren.anchor = anchor
        val editor = ClientContext.getHudEditor()
        editor.removeWidget(ren)
        editor.addWidget(anchor, ren)
    }

    fun getHudColor(position: Float): Color {
        val fadeSetting: Setting<HudColorMode> = hud.fadeSetting
        val fadeMode: HudColorMode = fadeSetting.value
        val capturedHud: HUDModule = hud
        return fadeMode.getColor(capturedHud, position)
    }

    fun getAnchor(): HudAnchor {
        return this._renderer?.anchor ?: HudAnchor.NONE
    }

    protected fun isInHudEditor(): Boolean {
        return ClientAccess.collectiveMinecraft.gui.screen() is HudEditorScreen
    }

    companion object {
        private val anchorCodec = EnumJsonConverter(HudAnchor::class.java)

        val hud: HUDModule
            get() = ClientContext.getModule(HUDModule::class.java) as HUDModule
    }
}
