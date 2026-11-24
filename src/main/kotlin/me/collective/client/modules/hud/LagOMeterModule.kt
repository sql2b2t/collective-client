package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ServerStateManager
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.render.FontRenderer
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color

class LagOMeterModule : HudWidgetModule("Lag'O'Meter", *createAliases()) {
   private val foliaSetting: Setting<Boolean>
   private val colorSetting: Setting<Color>
   private val visibilityAnimation: ClampedInterpolatedFloat

   private fun getWelcomerModule(): WelcomerModule {
      val welcomerType = WelcomerModule::class.java
      val registry: ModuleRegistry = ClientContext.moduleRegistry
      return registry.getModule(welcomerType) as WelcomerModule
   }

   private fun formatUnresponsiveDuration(): String {
      val useEntityPackets = this.foliaSetting.getValue()
      val packetKind = if (useEntityPackets || RotationManager.is2b2tMode()) {
         ServerStateManager.PacketActivity.ENTITY
      } else {
         ServerStateManager.PacketActivity.ANY
      }
      val nowMillis = System.currentTimeMillis()
      val packetTracker = ClientContext.serverStateManager
      val elapsedMillis = (nowMillis - packetTracker.getLastPacketTime(packetKind)).toFloat()
      val elapsedSeconds = elapsedMillis / 1000.0f
      if (elapsedSeconds >= 3600.0f) {
         return "more than an hour."
      } else {
         return "%.1fs".format(elapsedSeconds)
      }
   }

   override fun calculateDimensions(): FloatArray {
      val warningText = this.getWarningText()
      if (warningText != null) {
         val welcomer = this.getWelcomerModule()
         if (!welcomer.shouldHideWidget(this)) {
            val dimensions = FloatArray(2)
            dimensions[0] = FontRenderer.INSTANCE
               .getWidth(warningText)
            dimensions[1] = (FontRenderer.INSTANCE
                  .getHeight() +
         1).toFloat()
            return dimensions
         }
      }
      val hiddenDimensions = FloatArray(2)
      hiddenDimensions[0] = 0.0f
      hiddenDimensions[1] = 0.0f
      return hiddenDimensions
   }

   override fun renderContent(poses: PoseStack) {
      val warningText = this.getWarningText()
      val animationReceiver = this.visibilityAnimation
      val visible = warningText != null
      animationReceiver.setTarget(visible)
      if (warningText != null) {
         val welcomer = this.getWelcomerModule()
         if (!welcomer.shouldHideWidget(this)) {
            val visibility = this.visibilityAnimation.updateAndGet()
            val font = FontRenderer.INSTANCE
            val slideOffsetY = (1.0f - visibility) *
               -FontRenderer.INSTANCE
                  .getHeight()
            font.drawWithShadow(
               poses, warningText, 0.0f, slideOffsetY, this.colorSetting.getValue()
            )
            return
         }
      }
   }

   private fun getWarningText(): String? {
      val useEntityPackets = this.foliaSetting.getValue()
      val packetKind = if (useEntityPackets || RotationManager.is2b2tMode()) {
         ServerStateManager.PacketActivity.ENTITY
      } else {
         ServerStateManager.PacketActivity.ANY
      }
      val nowMillis = System.currentTimeMillis()
      val packetTracker = ClientContext.serverStateManager
      val elapsedMillis = nowMillis - packetTracker.getLastPacketTime(packetKind)
      val clampedAgeMillis = Math.max(elapsedMillis, 0L)
      var remoteServerUnresponsive = false
      if (clampedAgeMillis > 1000L) {
         val client = ClientAccess.collectiveMinecraft
         remoteServerUnresponsive = !client.isLocalServer
      }
      if (!remoteServerUnresponsive && !this.isInHudEditor()) {
         return null
      }
      val durationText = this.formatUnresponsiveDuration()
      return "Server is not responding for " + durationText
   }

   init {
      val defaultFolia = false
      this.foliaSetting = this.add(BooleanSetting("Folia", defaultFolia))
      this.colorSetting = this.add(ColorSetting("Color", Color.gray))
      this.visibilityAnimation = ClampedInterpolatedFloat(1.0f)
      val renderer = ResizableHudWidgetRenderer(this)
      renderer.enableHudScale()
      this.setRenderer(renderer)
   }

   companion object {
      private fun createAliases(): Array<String> {
         val aliasArray = arrayOf("lag", "lagometer")
         aliasArray[0] = "lag"
         aliasArray[1] = "lagometer"
         return aliasArray
      }
   }
}
