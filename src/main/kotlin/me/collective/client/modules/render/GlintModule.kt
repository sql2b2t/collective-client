package me.collective.client.modules.render

import me.collective.client.events.GlintTextureTransformEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.RenderTypeDrawEvent
import me.collective.client.events.EventStage
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.awt.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector4f

class GlintModule : Module("Glint", "Changes your enchantment glint's color.", ModuleCategory.RENDER) {
   val colorSetting: Setting<Color> = this.add(ColorSetting("Color", Color(-1, true)))
   private val spreadSetting: Setting<Int> = this.add(NumberSetting("Spread", 1, 1, 5))

   @EventHandler
   private fun onGlintTextureTransform(event: GlintTextureTransformEvent) {
      if (!ChamsModule.renderingGlintBatch) {
         val spreadChoice: Int = this.spreadSetting.value!!
         val scale: Float = 6.25F * getSpreadMultiplier(spreadChoice)
         event.scale = scale
      }
   }

   @EventHandler(priority = 100)
   private fun onRenderTypeDrawEvent(event: RenderTypeDrawEvent) {
      if (ChamsModule.renderingGlintBatch || EventStage.Pre != event.stage) {
         return
      }
      val textureManager = Minecraft.getInstance().textureManager
      val textureId = SHINE_TEXTURE
      val texture: AbstractTexture = textureManager.getTexture(textureId)
      event.override = texture.getId()
   }

   fun getGlintColor(): Vector4f {
      val selectedColor = this.colorSetting.value!!
      val colorVector = GLINT_COLOR_VECTOR
      var red: Float = selectedColor.red.toFloat()
      red /= 255.0F
      var green: Float = selectedColor.green.toFloat()
      green /= 255.0F
      var blue: Float = selectedColor.blue.toFloat()
      blue /= 255.0F
      colorVector.set(red, green, blue, 1.0F)
      return GLINT_COLOR_VECTOR
   }

   companion object {

      private val GLINT_COLOR_VECTOR: Vector4f = Vector4f()
      private val SHINE_TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath("collective-mount", "textures/shine.png")

      @JvmStatic
      fun getSpreadMultiplier(spread: Int): Float {
         return when (spread) {
            2 -> 0.24F
            3 -> 0.32F
            4 -> 0.42F
            5 -> 0.5F
            else -> 0.16F
         }
      }

   }

}
