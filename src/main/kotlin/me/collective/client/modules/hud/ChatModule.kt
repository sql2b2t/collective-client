package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule

import com.mojang.blaze3d.platform.Window
import net.minecraft.client.OptionInstance
import net.minecraft.client.Options
import net.minecraft.client.gui.components.ChatComponent

class ChatModule : HudWidgetModule("Chat") {

   val chatOffsetY: Float
      get() {
         if (this.renderer.getDimensions() == null) {
            return 0.0f
         }

         val window: Window = ClientAccess.collectiveMinecraft.getWindow()
         val screenHeight: Float = window.getGuiScaledHeight().toFloat()
         val scale: Float = this.getChatScale()
         val baselineY: Float = screenHeight - 40.0f
         val unscaledOffsetY: Float = (this.renderer.getY() - baselineY) / scale
         return unscaledOffsetY + this.getUnfocusedChatHeight()
      }

   private fun getChatScale(): Float {
      val options: Options = ClientAccess.collectiveMinecraft.options
      val chatScale: OptionInstance<Double> = options.chatScale()
      val scale: Double = chatScale.get()
      return scale.toFloat()
   }

   val chatOffsetX: Float
      get() {
         if (this.renderer.getDimensions() == null) {
            return 0.0f
         }

         val scale: Float = this.getChatScale()
         return this.renderer.getX() / scale
      }

   init {
      val renderer = ResizableHudWidgetRenderer(this)
      this.setRenderer(renderer)
   }

   override fun calculateDimensions(): FloatArray {
      val dimensions = FloatArray(2)
      val options: Options = ClientAccess.collectiveMinecraft.options
      val chatWidth: OptionInstance<Double> = options.chatWidth()
      val widthOption: Double = chatWidth.get()
      var sizeValue: Float = widthOption.toFloat()
      sizeValue *= 280.0f
      sizeValue += 40.0f
      val scalePadding: Float = 12.0f
      dimensions[0] = sizeValue + scalePadding * this.getChatScale()
      sizeValue = this.getUnfocusedChatHeight()
      dimensions[1] = sizeValue * this.getChatScale()
      return dimensions
   }

   private fun getUnfocusedChatHeight(): Float {
      val options: Options = ClientAccess.collectiveMinecraft.options
      val unfocusedHeight: OptionInstance<Double> = options.chatHeightUnfocused()
      val heightOption: Double = unfocusedHeight.get()
      val roundedHeight: Double = heightOption.toFloat().toDouble()
      return ChatComponent.getHeight(roundedHeight).toFloat()
   }
}
