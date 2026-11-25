package me.collective.client.modules.client

import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientContext
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.managers.ModuleRegistry
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting

import java.awt.Color

enum class HudColorMode(private val displayName: String) : NamedEntry {
    NONE("None") {
        override fun getColor(hud: HUDModule, position: Float): Color {
            val colorSetting = hud.colorSetting as ColorSetting
            val color = colorSetting.value
            if (!colorSetting.isRainbow) return color
            val offset = (position * hud.fadeOffsetSetting.value * getPositionScale()).toInt()
            return colorSetting.applyRainbow(color, offset)
        }
    },
    PULSE("Pulse") {
        override fun getColor(hud: HUDModule, position: Float): Color {
            val firstColor = hud.colorSetting.value
            val secondColor = hud.secondColorSetting.value
            val fadeOffset = hud.fadeOffsetSetting.value
            val positionOffset = position * fadeOffset
            return ColorUtils.pulse(
                firstColor,
                secondColor,
                2000.0,
                (positionOffset * getPositionScale()).toDouble()
            )
        }
    },
    RAINBOW("Rainbow") {
        override fun getColor(hud: HUDModule, position: Float): Color {
            val baseColor = hud.colorSetting.value
            val hsb = Color.RGBtoHSB(baseColor.red, baseColor.green, baseColor.blue, null)
            var position = position * 5.0f
            val rainbowTimeScale = MathConstants.FULL_TURN_DEGREES.toFloat()
            val rainbowPeriod = (rainbowTimeScale * colorsModule!!.getRainbowPeriod()).toInt()
            val fadeOffset = hud.fadeOffsetSetting.value
            val positionOffset = position * fadeOffset
            val rainbowOffset = (positionOffset * getPositionScale()).toInt()
            val saturation = hsb[1]
            val brightness = hsb[2]
            return ColorUtils.rainbow(
                rainbowPeriod, rainbowOffset, saturation, brightness, 255
            )
        }

        override fun bindColorsModule(registry: ModuleRegistry) {
            colorsModule = registry.getModule(ColorsModule::class.java) as ColorsModule
        }
    },
    RAW("Raw") {
        override fun getColor(hud: HUDModule, position: Float): Color {
            val colorAmount = maxOf(1, minOf(hud.colorAmountSetting.value, hud.rawColorSettings.size))
            val fadeOffset = maxOf(1, hud.fadeOffsetSetting.value)
            val inverseFadeOffset = 1.0f / fadeOffset
            val currentTime = System.currentTimeMillis()
            val fadeSpeed = maxOf(1, hud.fadeSpeedSetting.value)
            val timePhase = (currentTime / fadeSpeed % (colorAmount * fadeOffset)).toFloat()
            val phase = (timePhase + position / getPositionScale()).toInt()
            val palettePosition = (phase * inverseFadeOffset).toDouble()
            var colorIndex = Math.floor(palettePosition).toInt()
            if (colorIndex < 0) {
                colorIndex = 0
            }
            if (hud.mixColorsSetting.value) {
                val firstColor = hud.rawColorSettings[colorIndex % colorAmount].value
                val secondColor = hud.rawColorSettings[(colorIndex + 1) % colorAmount].value
                return ColorUtils.blend(
                    firstColor, secondColor, 1.0f - (phase * inverseFadeOffset - colorIndex)
                )
            } else {
                return hud.rawColorSettings[colorIndex % colorAmount].value
            }
        }
    };

    override fun getName(): String {
        return this.displayName
    }

    abstract fun getColor(hud: HUDModule, position: Float): Color

    open fun bindColorsModule(registry: ModuleRegistry) {
        throw IllegalStateException("This enum constant has no captured module binding.")
    }

    companion object {
        private var colorsModule: ColorsModule? = null

        private fun getPositionScale(): Float {
            return 10.0f / (FontRenderer.INSTANCE.height + 2)
        }
    }
}
