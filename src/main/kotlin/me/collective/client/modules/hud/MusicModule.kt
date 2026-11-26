package me.collective.client.modules.hud

import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.HudWidgetModule
import me.collective.client.render.FontRenderer
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import com.mojang.blaze3d.vertex.PoseStack
import java.util.regex.Matcher

class MusicModule : HudWidgetModule("Music") {
   private val prefixSetting: Setting<Boolean>
   private val prefixTextSetting: Setting<String>

   init {
      this.prefixSetting = this.add(BooleanSetting("Prefix", false).markVisibilityDependency())

      this.prefixTextSetting = this.add(StringSetting("Value", "♪").dependsOn(this.prefixSetting))
      val widget = ResizableHudWidgetRenderer(this)
      widget.enableHudScale()
      this.setRenderer(widget)
   }

   override fun onEnable() {

   }

   private fun shouldSkipMediaRendering(): Boolean {
      if (!this.isInHudEditor()) {
         val playbackStatus: Int = MediaSessionBridge.playbackStatus

         return playbackStatus != 4
      }

      return false
   }

   override fun calculateDimensions(): FloatArray {

      if (!this.shouldSkipMediaRendering()) {

         val welcomerType = WelcomerModule::class.java
         val moduleRegistry: ModuleRegistry = ClientContext.moduleRegistry
         val welcomer = moduleRegistry.getModule(welcomerType) as WelcomerModule
         if (!welcomer.shouldHideWidget(this)) {

            val dimensions = FloatArray(2)
            val textRenderer = FontRenderer.INSTANCE
            dimensions[0] = textRenderer.getWidth(
               this.getMusicText()
            )
            dimensions[1] = FontRenderer.INSTANCE
               .getHeight().toFloat()
            return dimensions
         }
      }

      val hiddenDimensions = FloatArray(2)
      hiddenDimensions[0] = 0.0F
      hiddenDimensions[1] = 0.0F
      return hiddenDimensions
   }

   override fun renderContent(poses: PoseStack) {

      if (this.shouldSkipMediaRendering()) {
         return
      }

      val welcomerType = WelcomerModule::class.java
      val moduleRegistry: ModuleRegistry = ClientContext.moduleRegistry
      val welcomer = moduleRegistry.getModule(welcomerType) as WelcomerModule
      if (welcomer.shouldHideWidget(this)) {
         return
      }

      FontRenderer.INSTANCE.drawWithShadow(
         poses,
         this.getMusicText(),
         0.0F,
         0.0F,
         this.getHudColor(
            this.renderer
               .getY()
         )
      )
   }

   private fun getMusicText(): String {
      var text: String = MediaSessionBridge.getDisplayText()
      if (this.isInHudEditor()) {
         val playbackStatus: Int = MediaSessionBridge.playbackStatus

         if (playbackStatus != 4) {

            text = "HydrachFM - f3dot"
         }
      }

      val prefixEnabled: Boolean = this.prefixSetting.value!!
      if (prefixEnabled) {
         val musicText: String = text
         val prefixText: String = this.prefixTextSetting.value!!
         text = "\u0001 \u0001"
            .replaceFirst("\u0001", Matcher.quoteReplacement(prefixText))
            .replaceFirst("\u0001", Matcher.quoteReplacement(musicText))
      }

      return text.trim()
   }
}
