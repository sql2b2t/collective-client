package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HudAnchor

import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

class DirectionModule : HudWidgetModule("Direction", "facing") {
   private val preciseSetting: Setting<Boolean>
   private val prefixSetting: Setting<Boolean>

   init {
      val defaultPrecise: Boolean = false
      this.preciseSetting = this.add(BooleanSetting("Precise", defaultPrecise))

      val defaultPrefix: Boolean = true
      this.prefixSetting = this.add(BooleanSetting("Prefix", defaultPrefix))
      this.setRenderer(
         HudTextListRenderer(this, HudTextRow({ Component.literal(this.formatDirection()) }, { true }))
      )

      this.getRenderer()
         .setAnchor(HudAnchor.BOTTOM_LEFT)
   }

   private fun formatYawPitch(): String {
      val cameraForYaw: Camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      val cameraYaw: Float = cameraForYaw.yRot()
      val wrappedYaw: Float = Mth.wrapDegrees(cameraYaw)
      val cameraForPitch: Camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      val pitch: Float = cameraForPitch.xRot()
      val precise: Boolean = this.preciseSetting.value!!
      return if (precise) {

         "%.1f, %.1f".format(wrappedYaw, pitch)
      } else {
         val integerYaw: Int = wrappedYaw.toInt()
         integerYaw.toString()
      }
   }

   private fun formatDirection(): String {
      val gameRenderer: GameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
      val camera: Camera = gameRenderer.getMainCamera()
      val cameraYawCapture: Float = camera.yRot()

      val sector: Int = RotationManager.getYawSector(cameraYawCapture)
      val prefix: String = this.getCardinalPrefix(sector)
      val detailsColor: ChatFormatting = ChatFormatting.WHITE
      val detailsColorCode: String = detailsColor.toString()

      val detailsText: String = "%s, %s".format(this.formatYawPitch(), this.getAxisLabel(sector))
      var details: String = detailsColorCode + detailsText
      if (!prefix.isEmpty()) {
         val parenthesesColor: ChatFormatting = ChatFormatting.WHITE
         val parenthesesColorCode: String = parenthesesColor.toString()
         val parenthesizedDetails: String = " (" + details + ")"
         details = parenthesesColorCode + parenthesizedDetails
      }

      return prefix + details
   }

   private fun getCardinalPrefix(sector: Int): String {
      val showPrefix: Boolean = this.prefixSetting.value!!
      if (!showPrefix) {
         return ""
      }

      return when (sector) {
         0 -> "South"
         1 -> "SouthWest"
         2 -> "West"
         3 -> "NorthWest"
         4 -> "North"
         5 -> "NorthEast"
         6 -> "East"
         7 -> "SouthEast"
         else -> throw IllegalStateException("Invalid index.")
      }
   }

   private fun getAxisLabel(sector: Int): String {
      return when (sector) {
         0 -> "+Z"
         1 -> "-X +Z"
         2 -> "-X"
         3 -> "-X -Z"
         4 -> "-Z"
         5 -> "+X -Z"
         6 -> "+X"
         7 -> "+X +Z"
         else -> "Waiting..."
      }
   }
}
