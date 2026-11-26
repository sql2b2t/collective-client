package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.utils.entity.SidePlayer
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.render.ColorUtils
import me.collective.client.render.EntityChamsRenderer
import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.world.phys.AABB

class FakeLagRenderer(
   private val module: FakeLagModule
) : ClientAccess {
   private val fadeProgress: ClampedInterpolatedFloat = ClampedInterpolatedFloat(2.0F, true)

   private fun fadeAlpha(alpha: Int): Int {
      return Math.max(alpha - (alpha.toFloat() * fadeProgress.getValue()).toInt(), 0)
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      if (!module.renderSetting.value || isWorldUnavailable()) {
         return
      }
      val snapshot: SidePlayer = module.bufferedPlayerSnapshot ?: return
      fadeProgress.setTarget(!module.isToggled())
      fadeProgress.updateAndGet()
      val configuredFill = module.fillSetting.value
      val fill = ColorUtils.withAlpha(configuredFill, fadeAlpha(configuredFill.alpha))
      val configuredOutline = module.outlineSetting.value
      val outline = ColorUtils.withAlpha(configuredOutline, fadeAlpha(configuredOutline.alpha))
      val lineWidth = module.lineWidthSetting.value
      val poseStack: PoseStack = event.poseStack!!
      if (module.chamsSetting.value) {
         EntityChamsRenderer.setColors(outline, fill)
         EntityChamsRenderer.setLineWidth(lineWidth.toDouble())
         EntityChamsRenderer.renderEntity(poseStack, snapshot)
      } else {
         val bounds: AABB = snapshot.boundingBox
         WorldBoxRenderer.fillBox(poseStack, bounds, fill)
         WorldBoxRenderer.outlineBox(poseStack, bounds, outline, lineWidth)
      }
   }
}
