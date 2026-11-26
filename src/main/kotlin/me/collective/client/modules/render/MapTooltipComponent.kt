package me.collective.client.modules.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import me.collective.client.ClientAccess
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.MapRenderer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.MapRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import org.joml.Matrix3x2fStack

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

   override fun extractImage(font: Font, x: Int, y: Int, tooltipWidth: Int, tooltipHeight: Int, graphics: GuiGraphicsExtractor) {
      val poses: Matrix3x2fStack = graphics.pose()
      poses.pushMatrix()
      try {
         poses.translate(x.toFloat(), y.toFloat())
         val pipeline: RenderPipeline = RenderPipelines.GUI_TEXTURED
         graphics.blit(pipeline, BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F,
            TOOLTIP_SIZE, TOOLTIP_SIZE, BACKGROUND_TEXTURE_SIZE, BACKGROUND_TEXTURE_SIZE, -1)
         poses.translate(BORDER.toFloat(), BORDER.toFloat())
         poses.scale(MAP_SIZE.toFloat() / MAP_TEXTURE_SIZE, MAP_SIZE.toFloat() / MAP_TEXTURE_SIZE)
         val renderer: MapRenderer = ClientAccess.collectiveMinecraft.mapRenderer
         renderer.extractRenderState(MapId(this.mapId), this.mapData, MAP_RENDER_STATE)
         graphics.map(MAP_RENDER_STATE)
         me.collective.client.render.MultiBufferSource.immediate().endBatch()
      } finally {
         poses.popMatrix()
      }
   }

   companion object {

      private const val MAP_SIZE: Int = 96
      private const val MAP_TEXTURE_SIZE: Int = 128
      private const val BACKGROUND_TEXTURE_SIZE: Int = 64
      private const val BORDER: Int = 7
      private const val TOOLTIP_SIZE: Int = MAP_SIZE + BORDER * 2
      private val BACKGROUND_TEXTURE: Identifier =
         Identifier.parse("textures/map/map_background.png")
      private val MAP_RENDER_STATE: MapRenderState = MapRenderState()

   }
}
