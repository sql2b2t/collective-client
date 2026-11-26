package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.HudRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.MaskShaderRenderer
import me.collective.client.utils.FloatAnimation
import me.collective.client.render.RenderUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class BlurModule : Module("Blur", "Blurs the background in GUI's.", ModuleCategory.RENDER) {
   private val radiusSetting: Setting<Int> = this.add(NumberSetting("Radius", 6, 1, 16))
   private val openingAnimation = FloatAnimation()

   val radius: Int
      get() = this.radiusSetting.value!!

   init {
      this.setToggled(true)
   }


   @EventHandler
   private fun onScreenChange(
      event: ScreenChangeEvent.Before
   ) {
      if (event.isCancelled) {
         return
      }
      if (event.newScreen == null) {
         val animation = this.openingAnimation
         resetOpeningAnimation(animation)
         return
      }
      if (event.previousScreen != null) {
         return
      }
      this.openingAnimation.setTarget(1.0F, 75L)
   }

   @EventHandler(priority = -200)
   private fun onHudRender(event: HudRenderEvent.Unconditional) {
      val poseStack = event.poseStack!!
      this.renderBackgroundBlur(poseStack)
   }

   fun renderBackgroundBlur(poseStack: PoseStack) {
      val currentScreen: Screen? = ClientAccess.collectiveMinecraft.gui.screen()
      if (isBlurExcluded(currentScreen)) {
         return
      }
      RenderUtils.getEntityBuffers().endBatch()
      val drawFullscreenMask = Runnable {
         val renderer: RenderUtils = RenderUtils.INSTANCE
         val width: Float = ClientAccess.collectiveMinecraft.window.guiScaledWidth.toFloat()
         val height: Float = ClientAccess.collectiveMinecraft.window.guiScaledHeight.toFloat()

         renderer.fillRect(
            poseStack, 0.0F, 0.0F, width, height, 9999, -1
         )
      }

      val radius: Float = this.radius.toFloat()
      val animationCapture = this.openingAnimation

      MaskShaderRenderer.renderBlur(
         drawFullscreenMask, radius * animationCapture.getValue()
      )
   }

   companion object {

      private val CLICK_SCREEN_TITLE: Component = Component.literal("CLICK")


      private fun isBlurExcluded(screen: Screen?): Boolean {
         if (screen == null || screen is ClientScreen || screen is ChatScreen) {
            return true
         }
         return screen.title == CLICK_SCREEN_TITLE
      }

      private fun resetOpeningAnimation(animation: FloatAnimation) {
         animation.setValue(0.0F)
      }

   }

}
