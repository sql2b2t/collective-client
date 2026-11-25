package me.collective.client.render

import me.collective.client.modules.client.UIModule
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ColorUtils {
   @JvmField
   val TRANSPARENT = Color(0, 0, 0, 0)

   @JvmStatic
   fun getLatencyColor(latency: Int): Color {
      val progress = MathUtils.rangeProgress(latency.toFloat(), 30.0f, 150.0f)
      return if (progress <= 0.5f) {
         Color(progress * 2.0f, 1.0f, 0.0f)
      } else {
         Color(0.8f, 1.0f - (progress - 0.5f) * 2.0f, 0.0f)
      }
   }

   @JvmStatic
   fun getHealthColor(healthRatio: Double): Int {
      val progress = Mth.clamp(healthRatio - 0.20000000298023224, 0.0, 1.0).toFloat()
      var brightness = 1.0f
      if (healthRatio <= 0.20000000298023224) {
         brightness = Mth.clamp(healthRatio / 0.20000000298023224, 0.5, 1.0).toFloat()
      }
      val red = Mth.clamp(2.0f * (1.0f - progress), 0.3f, 1.0f) * brightness
      val green = min(2.0f * progress, 1.0f) * brightness
      val blue = 0.3f * brightness
      return ARGB.colorFromFloat(1.0f, red, green, blue)
   }

   @JvmStatic
   fun modulateWithShaderColor(argb: Int): Int {
      return modulateArgb(argb, RenderUtils.getRenderColor())
   }

   @JvmStatic
   fun fromArgb(argb: Int): Color {
      return Color(argb, true)
   }

   @JvmStatic
   fun getThemeColor(): Color {
      return UIModule.INSTANCE.colorSetting.value
   }

   @JvmStatic
   fun parseHexColor(text: String): Color {
      var hex = text
      if (hex.startsWith("#")) {
         hex = hex.substring(1)
      }

      var length = hex.length
      if (length == 3) {
         val characters = hex.toCharArray()
         hex = "" + characters[0] + characters[0] + characters[1] + characters[1] + characters[2] + characters[2]
         length = 6
      }

      if (length != 6 && length != 8) {
         throw IllegalArgumentException()
      }

      val hasAlpha = length == 8
      val channelCount = if (hasAlpha) 4 else 3
      val channels = IntArray(channelCount)
      for (index in 0 until channelCount) {
         channels[index] = hex.substring(index * 2, (index + 1) * 2).toInt(16)
      }

      val channelOffset = if (hasAlpha) 1 else 0
      return Color(
         channels[channelOffset],
         channels[1 + channelOffset],
         channels[2 + channelOffset],
         if (hasAlpha) channels[0] else 255
      )
   }

   @JvmStatic
   fun toRgbVector(color: Color): Vec3 {
      return Vec3(
         (color.red / 255.0f).toDouble(),
         (color.green / 255.0f).toDouble(),
         (color.blue / 255.0f).toDouble()
      )
   }

   @JvmStatic
   fun darken(color: Color, factor: Float): Color {
      return Color(
         max((color.red * factor).toInt(), 0),
         max((color.green * factor).toInt(), 0),
         max((color.blue * factor).toInt(), 0),
         color.alpha
      )
   }

   private fun toHexComponent(channel: Int): String {
      return (if (channel < 16) "0" else "") + Integer.toHexString(channel)
   }

   @JvmStatic
   fun formatRgb(color: Color, includeAlpha: Boolean): String {
      return if (!includeAlpha) {
         "rgb(%d, %d, %d)".format(color.red, color.green, color.blue)
      } else {
         "rgba(%d, %d, %d, %d)".format(color.red, color.green, color.blue, color.alpha)
      }
   }

   @JvmStatic
   fun pulse(firstColor: Color, secondColor: Color, period: Double, offset: Double): Color {
      if (period <= 0.0) {
         return firstColor
      }

      val phase = (System.currentTimeMillis() + offset) % period / period
      return if (phase > MathConstants.HALF) {
         blend(secondColor, firstColor, ((phase - MathConstants.HALF) * 2.0).toFloat())
      } else {
         blend(firstColor, secondColor, (phase * 2.0).toFloat())
      }
   }

   @JvmStatic
   fun rainbow(offset: Int, saturation: Float, brightness: Float, alpha: Int): Color {
      return rainbow(MathConstants.FULL_TURN_DEGREES, offset, saturation, brightness, alpha)
   }

   @JvmStatic
   fun modulateArgb(
      argb: Int,
      redMultiplier: Int,
      greenMultiplier: Int,
      blueMultiplier: Int,
      alphaMultiplier: Int,
   ): Int {
      val alpha = (argb ushr 24 and 255) * alphaMultiplier shr 8
      val red = (argb ushr 16 and 255) * redMultiplier shr 8
      val green = (argb ushr 8 and 255) * greenMultiplier shr 8
      val blue = (argb and 255) * blueMultiplier shr 8
      return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
   }

   @JvmStatic
   fun modulateArgb(argb: Int, multipliers: IntArray): Int {
      return modulateArgb(argb, multipliers[0], multipliers[1], multipliers[2], multipliers[3])
   }

   @JvmStatic
   fun rainbow(period: Int, offset: Int, saturation: Float, brightness: Float, alpha: Int): Color {
      if (period <= 0) {
         return withAlpha(Color.getHSBColor(0.0f, saturation, brightness), alpha)
      }

      var phase = ceil((System.currentTimeMillis() + offset) / 20.0)
      phase %= period
      val color = Color.getHSBColor((phase / period).toFloat(), saturation, brightness)
      return withAlpha(color, alpha)
   }

   @JvmStatic
   fun packArgb(red: Int, green: Int, blue: Int, alpha: Int): Int {
      return ((alpha and 255) shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)
   }

   @JvmStatic
   fun blend(firstColor: Color, secondColor: Color, firstWeight: Float): Color {
      return try {
         Color(
            (firstColor.red * firstWeight + secondColor.red * (1.0f - firstWeight)).toInt(),
            (firstColor.green * firstWeight + secondColor.green * (1.0f - firstWeight)).toInt(),
            (firstColor.blue * firstWeight + secondColor.blue * (1.0f - firstWeight)).toInt(),
            (firstColor.alpha * firstWeight + secondColor.alpha * (1.0f - firstWeight)).toInt()
         )
      } catch (exception: IllegalArgumentException) {
         exception.printStackTrace()
         Color.white
      }
   }

   @JvmStatic
   fun withAlpha(color: Color, alpha: Float): Color {
      return withAlpha(color, (alpha * 255.0f).roundToInt())
   }

   @JvmStatic
   fun packArgb(red: Float, green: Float, blue: Float, alpha: Float): Int {
      return (((alpha * 255.0f).toInt() and 255) shl 24) or
         (((red * 255.0f).toInt() and 255) shl 16) or
         (((green * 255.0f).toInt() and 255) shl 8) or
         ((blue * 255.0f).toInt() and 255)
   }

   @JvmStatic
   fun brighten(color: Color, factor: Float): Color {
      var red = color.red
      var green = color.green
      var blue = color.blue
      val alpha = color.alpha
      val minimumChannel = Mth.clamp((1.0 / (1.0 - factor)).toInt(), 0, 255)
      if (red == 0 && green == 0 && blue == 0) {
         return Color(minimumChannel, minimumChannel, minimumChannel, alpha)
      }

      if (red in 1 until minimumChannel) {
         red = minimumChannel
      }

      if (green in 1 until minimumChannel) {
         green = minimumChannel
      }

      if (blue in 1 until minimumChannel) {
         blue = minimumChannel
      }

      return Color(
         Mth.clamp((red / factor).toInt(), 0, 255),
         Mth.clamp((green / factor).toInt(), 0, 255),
         Mth.clamp((blue / factor).toInt(), 0, 255),
         alpha
      )
   }

   @JvmStatic
   fun redToGreen(progress: Float): Color {
      return Color(min((1.0f - progress) * 2.0f, 1.0f), min(progress * 2.0f, 1.0f), 0.0f)
   }

   @JvmStatic
   fun toHex(color: Color, includeHash: Boolean): String {
      val hex = StringBuilder()
      hex.append(toHexComponent(color.alpha))
      hex.append(toHexComponent(color.red))
      hex.append(toHexComponent(color.green))
      hex.append(toHexComponent(color.blue))
      return (if (includeHash) "#" else "") + hex
   }

   @JvmStatic
   fun withAlpha(color: Color, alpha: Int): Color {
      return Color(
         color.red,
         color.green,
         color.blue,
         Mth.clamp(alpha, 0, 255)
      )
   }

   @JvmStatic
   fun toArgb(color: Color, alpha: Float): Int {
      return packArgb(color.red, color.green, color.blue, Mth.clamp((alpha * 255.0f).toInt(), 0, 255))
   }

   @JvmStatic
   fun modulateRgb(argb: Int, redMultiplier: Int, greenMultiplier: Int, blueMultiplier: Int): Int {
      val alpha = argb ushr 24 and 255
      val red = (argb ushr 16 and 255) * redMultiplier shr 8
      val green = (argb ushr 8 and 255) * greenMultiplier shr 8
      val blue = (argb and 255) * blueMultiplier shr 8
      return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
   }

   @JvmStatic
   fun transparent(color: Color): Color {
      return withAlpha(color, 0)
   }

   @JvmStatic
   fun multiplyAlpha(color: Color, factor: Float): Color {
      return withAlpha(color, (color.alpha * factor).toInt())
   }

   @JvmStatic
   fun toArgb(color: Color, alpha: Int): Int {
      return packArgb(color.red, color.green, color.blue, alpha)
   }
}
