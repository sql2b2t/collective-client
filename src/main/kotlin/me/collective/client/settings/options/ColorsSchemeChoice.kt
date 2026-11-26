package me.collective.client.settings.options

import me.collective.client.render.ColorUtils
import me.collective.client.settings.NamedEntry
import java.awt.Color
import net.minecraft.util.ARGB

enum class ColorsSchemeChoice(private val displayName: String, private val matrix: Array<FloatArray>?) : NamedEntry {
    NORMAL("Normal", null),
    PROTANOPIA("Protanopia", TransformMatrices.PROTANOPIA),
    DEUTERANOPIA("Deuteranopia", TransformMatrices.DEUTERANOPIA),
    TRITANOPIA("Tritanopia", TransformMatrices.TRITANOPIA);

    override fun getName(): String = displayName

    fun transform(argb: Int): Int {
        if (matrix == null) {
            return argb
        }
        return ARGB.color(ARGB.alphaFloat(argb), applyMatrix(argb, matrix))
    }

    fun transform(color: Color): Color {
        if (matrix == null) {
            return color
        }

        val transformed = Color(applyMatrix(color.hashCode(), matrix))
        return ColorUtils.withAlpha(transformed, color.alpha)
    }

    private fun applyMatrix(argb: Int, matrix: Array<FloatArray>): Int {
        val alpha = argb ushr 24 and 0xFF
        val red = argb ushr 16 and 0xFF
        val green = argb ushr 8 and 0xFF
        val blue = argb and 0xFF
        val transformedRed = Math.clamp((red * matrix[0][0] + green * matrix[0][1] + blue * matrix[0][2]).toInt().toLong(), 0, 255)
        val transformedGreen = Math.clamp((red * matrix[1][0] + green * matrix[1][1] + blue * matrix[1][2]).toInt().toLong(), 0, 255)
        val transformedBlue = Math.clamp((red * matrix[2][0] + green * matrix[2][1] + blue * matrix[2][2]).toInt().toLong(), 0, 255)
        return alpha shl 24 or (transformedRed shl 16) or (transformedGreen shl 8) or transformedBlue
    }

    private object TransformMatrices {
        val PROTANOPIA: Array<FloatArray> = arrayOf(
            floatArrayOf(0.567f, 0.433f, 0.0f),
            floatArrayOf(0.558f, 0.442f, 0.0f),
            floatArrayOf(0.0f, 0.242f, 0.758f),
        )
        val DEUTERANOPIA: Array<FloatArray> = arrayOf(
            floatArrayOf(0.625f, 0.375f, 0.0f),
            floatArrayOf(0.7f, 0.3f, 0.0f),
            floatArrayOf(0.0f, 0.3f, 0.7f),
        )
        val TRITANOPIA: Array<FloatArray> = arrayOf(
            floatArrayOf(0.95f, 0.05f, 0.0f),
            floatArrayOf(0.0f, 0.433f, 0.567f),
            floatArrayOf(0.0f, 0.475f, 0.525f),
        )
    }
}
