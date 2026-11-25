package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientContext
import me.collective.client.config.ColorJsonConverter
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.client.ColorsModule
import me.collective.client.render.ColorUtils
import me.collective.client.utils.math.MathConstants
import java.awt.Color
import java.util.function.Predicate

class ColorSetting : Setting<Color> {
    private val colorCodec = ColorJsonConverter()
    private var rainbow: Boolean = false
    private var synced: Boolean = false

    @JvmOverloads
    constructor(
        name: String,
        defaultColor: Color,
        visibility: Predicate<in Color>? = null
    ) : super(name, defaultColor, visibility)

    override fun getValue(): Color = this.getColor()

    var isRainbow: Boolean
        get() {
            val cm = colorsModule
            if (cm != null && this === cm.themeColorSetting) {
                return this.rainbow
            }
            if (!this.rainbow) {
                if (this.isSynced && cm != null) {
                    val themeSetting = cm.themeColorSetting as? ColorSetting
                    if (themeSetting?.isRainbow == true) {
                        return true
                    }
                }
                return false
            } else {
                return true
            }
        }
        set(value) {
            this.rainbow = value
        }

    val isThemeColor: Boolean
        @JvmName("isThemeColorProp") get() = isThemeColor()

    fun isThemeColor(): Boolean {
        val cm = colorsModule
        return cm != null && this === cm.themeColorSetting
    }

    fun setColor(color: Color) {
        if (this.opaqueColor) {
            super.setValue(Color(color.hashCode(), false))
        } else {
            super.setValue(color)
        }
    }

    override fun fromJson(json: JsonElement) {
        if (json.isJsonArray) {
            this.setColor(this.colorCodec.fromJson(json))
        } else if (json.isJsonObject) {
            val jsonObject = json.asJsonObject
            val rgba = jsonObject.get("rgba")
            if (rgba != null) {
                this.setColor(this.colorCodec.fromJson(rgba))
            }
            this.isRainbow = jsonObject.get("rainbow")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            this.isSynced = jsonObject.get("sync")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        }
    }

    override fun toJson(): JsonElement {
        val json = JsonObject()
        val color = this.getColor()
        json.add("rgba", this.colorCodec.toJson(color))
        json.addProperty("rainbow", this.isRainbow)
        json.addProperty("sync", this.isSynced)
        return json
    }

    fun applyRainbow(color: Color, phaseOffset: Int): Color {
        val red = color.red
        val green = color.green
        val blue = color.blue
        val hsb = Color.RGBtoHSB(red, green, blue, null)
        val rainbowPeriodFactor = MathConstants.FULL_TURN_DEGREES
        val colorsSnapshot = colorsModule ?: return color

        val rainbowPeriod = (rainbowPeriodFactor * colorsSnapshot.getRainbowPeriod()).toInt()
        val saturation = hsb[1]
        val brightness = hsb[2]

        return ColorUtils.rainbow(
            rainbowPeriod, phaseOffset, saturation, brightness, color.alpha
        )
    }

    val color: Color
        @JvmName("getColorProp") get() = getColor()

    fun getColor(): Color {
        val color = super.getValue()
        val cm = colorsModule
        return if (this.synced && cm != null) {
            val themeSetting = cm.themeColorSetting
            val themeColor = themeSetting.getValue()
            ColorUtils.withAlpha(themeColor, color.alpha)
        } else if (this.rainbow) {
            this.applyRainbow(color, 0)
        } else {
            color
        }
    }

    var isSynced: Boolean
        get() {
            val cm = colorsModule
            return if (cm != null && this === cm.themeColorSetting) {
                false
            } else {
                this.synced
            }
        }
        set(value) {
            val cm = colorsModule
            if (cm == null || this !== cm.themeColorSetting) {
                this.synced = value
            }
        }

    override fun parseValue(text: String) {
        try {
            val parsedColor = ColorUtils.parseHexColor(text)
            this.setColor(parsedColor)
        } catch (_: Throwable) {
        }
    }

    override fun setValue(value: Color) {
        this.setColor(value)
    }

    override fun markVisibilityDependency(): ColorSetting = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): ColorSetting = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): ColorSetting = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): ColorSetting = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): ColorSetting = apply { super.forceOpaqueColor() }

    companion object {
        @JvmStatic
        var colorsModule: ColorsModule? = null

        init {
            try {
                colorsModule = ClientContext.getModuleOrNull(ColorsModule::class.java)
            } catch (_: Throwable) {
            }
        }

        @JvmStatic
        fun bindModuleRegistry(registry: ModuleRegistry) {
            colorsModule = registry.getModule(ColorsModule::class.java) as? ColorsModule
        }
    }
}
