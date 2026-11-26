package me.collective.client.modules.hud

import me.collective.client.ClientContext
import me.collective.client.modules.client.HUDModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.settings.Setting
import me.collective.client.utils.InterpolatedFloat

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.function.Supplier
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

open class HudTextRow(
   private val textSupplier: Supplier<out Component>,
   private val visibilitySupplier: Supplier<*>
) {
   private var visibility: Float = 0.0F
   private var cachedText: Component = Component.empty()

   @JvmField
   val widthAnimation: InterpolatedFloat = InterpolatedFloat(2.0F, true)

   open fun updateVisibility() {
      val frameRate: Int = ClientContext.frameTiming.getFramesPerSecond()
      val visibilityStep: Float = 1.0F / (frameRate shr 2)
      if (this.isDrawn()) {
         this.visibility += visibilityStep
      } else {
         this.visibility -= visibilityStep
      }
      val visibilityForClamp: Float = this.visibility
      val maximumVisibility: Float = 1.0F
      this.visibility = Mth.clamp(visibilityForClamp, 0.0F, maximumVisibility)
      if (this.visibility > 0.0F) {
         this.cachedText = this.textSupplier.get()
      }
   }

   open fun isDrawn(): Boolean {
      val visible: Boolean = this.visibilitySupplier.get() as Boolean
      return visible
   }

   open fun renderRow(
      poses: PoseStack, x: Float, y: Float, color: Color, shadow: Boolean
   ) {
      if (this.visibility == 0.0F) {
         val animationForReset = this.widthAnimation
         resetWidth(animationForReset)
      } else {
         var font = FontRenderer.INSTANCE
         val widthText = this.getText()
         val textWidth: Float = font.getWidth(widthText.getString())
         val smoothWidthOption: Setting<Boolean> = hudSettings.smoothWidthSetting
         val smoothWidth: Boolean = smoothWidthOption.value!!
         if (smoothWidth) {
            this.widthAnimation.setTarget(textWidth)
         } else {
            this.widthAnimation.setValue(textWidth)
         }
         if (this.visibility > 0.0F) {
            val originalAlpha: Double = color.getAlpha().toDouble()
            val visibilityForAlpha: Float = this.visibility
            val scaledVisibility: Double = (visibilityForAlpha * 2.0F).toDouble()
            val packedColor: Int = ColorUtils.toArgb(
               color, (originalAlpha * Mth.clamp(scaledVisibility, 0.1, 1.0)).toInt()
            )
            font = FontRenderer.INSTANCE
            if (shadow) {
               font.drawWithShadow(poses, this.getText(), x, y, packedColor)
            } else {
               font.drawWithoutShadow(poses, this.getText(), x, y, packedColor, DisplayMode.NORMAL)
            }
         }
      }
   }

   open fun getText(): Component {
      return this.cachedText
   }

   open fun getVisibility(): Float {
      return this.visibility
   }

   companion object {
      private val hudSettings: HUDModule get() = ClientContext.getModule(HUDModule::class.java)

      private fun resetWidth(animation: InterpolatedFloat) {
         animation.setValue(0.0F)
      }
   }
}
