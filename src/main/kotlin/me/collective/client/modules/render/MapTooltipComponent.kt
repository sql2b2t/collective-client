package me.collective.client.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MapRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.state.MapRenderState
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

data class MapTooltipComponent(
   val mapId: Int,
   val mapData: MapItemSavedData
) : ClientAccess, ClientTooltipComponent {

   override fun getHeight(font: Font): Int {
      return TOOLTIP_SIZE
   }

   override fun getWidth(font: Font): Int {
      return TOOLTIP_SIZE
   }

   override fun renderImage(font: Font, x: Int, y: Int, tooltipWidth: Int, tooltipHeight: Int, graphics: GuiGraphics) {
      val poses: PoseStack = graphics.pose()
      poses.pushPose()
      try {
         poses.translate(x.toFloat(), y.toFloat(), 0.0F)
         graphics.blit(RenderType::guiTextured, BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F,
            TOOLTIP_SIZE, TOOLTIP_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, -1)
         poses.translate(BORDER.toFloat(), BORDER.toFloat(), 0.0F)
         poses.scale(MAP_SIZE.toFloat() / MAP_TEXTURE_SIZE, MAP_SIZE.toFloat() / MAP_TEXTURE_SIZE, 1.0F)
         val renderer: MapRenderer = ClientAccess.collectiveMinecraft.mapRenderer
         renderer.extractRenderState(MapId(this.mapId), this.mapData, MAP_RENDER_STATE)
         val bufferSource = ClientAccess.collectiveMinecraft.renderBuffers().bufferSource()
         renderer.render(MAP_RENDER_STATE, poses, bufferSource, true, LightTexture.FULL_BRIGHT)
         bufferSource.endBatch()
      } finally {
         poses.popPose()
      }
   }

   companion object {

      private const val MAP_SIZE: Int = 96
      private const val MAP_TEXTURE_SIZE: Int = 128
      private const val BACKGROUND_TEXTURE_SIZE: Int = 64
      private const val BORDER: Int = 7
      private const val TOOLTIP_SIZE: Int = MAP_SIZE + BORDER * 2
      private val BACKGROUND_TEXTURE: ResourceLocation =
         ResourceLocation.parse("textures/map/map_background.png")
      private val MAP_RENDER_STATE: MapRenderState = MapRenderState()

   }
}
