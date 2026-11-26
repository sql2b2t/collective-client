package me.collective.client.settings.options

import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.NamedEntry

import java.awt.Color

enum class SpeedMineColorModeChoice(private val displayName: String) : NamedEntry {
    PROGRESS("Progress") {
        override fun getColors(module: SpeedMineModule, progress: Float): Array<Color> {
            val customMode = CUSTOM
            val customColors = customMode.getColors(module, progress)!!
            var colors = arrayOf(
                Color(0.8F, 0.0F, 0.0F, customColors[0].getAlpha() / 255.0F),
                Color(0.8F, 0.0F, 0.0F, customColors[1].getAlpha() / 255.0F)
            )
            if (progress <= 0.8F) {
                return colors
            }

            val currentProgress = (0.2F - (1.0F - progress)) / 0.2F
            val unclampedRed = 0.8F - currentProgress
            val minimumRed = 0.0F
            val red = Math.max(unclampedRed, minimumRed)
            colors = arrayOf(
                Color(red, currentProgress * 0.8F, 0.0F, customColors[0].getAlpha() / 255.0F),
                Color(red, currentProgress * 0.8F, 0.0F, customColors[1].getAlpha() / 255.0F)
            )
            return colors
        }
    },
    CUSTOM("Custom") {
        override fun getColors(module: SpeedMineModule, progress: Float): Array<Color> {
            val fillSetting = module.fillSetting
            val fill = fillSetting.getValue()
            val outlineSetting = module.outlineSetting
            val outline = outlineSetting.getValue()
            return arrayOf(fill, outline)
        }
    };

    override fun getName(): String = displayName

    open fun getColors(module: SpeedMineModule, progress: Float): Array<Color>? {
        return null
    }
}
