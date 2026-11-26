package me.collective.client.utils.inventory

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractContainerScreenRenderTooltipEvent
import me.collective.client.modules.render.TooltipsModule
import me.collective.client.utils.KeyUtils

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.MapRenderer
import me.collective.client.render.MultiBufferSource
import net.minecraft.client.renderer.state.MapRenderState
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import org.joml.Matrix3x2fStack

class ContainerTooltipComponent(
   val items: List<ItemStack>
) : ClientAccess,
   ClientTooltipComponent {

   override fun getHeight(font: Font): Int {
      return 53
   }

   override fun getWidth(font: Font): Int {
      return 155
   }

   override fun extractImage(
      font: Font, x: Int, y: Int, tooltipWidth: Int, tooltipHeight: Int, graphics: GuiGraphicsExtractor
   ) {
      var column = 0
      var row = 0
      val mouseX = AbstractContainerScreenRenderTooltipEvent.mouseX
      val mouseY = AbstractContainerScreenRenderTooltipEvent.mouseY
      var hoveredItem: ItemStack? = null
      for (item in items) {
         val itemX = x + 17 * column
         val itemY = y + 17 * row
         val markedCount = item.count == 69
         if (mouseX >= itemX) {
            if (mouseX <= itemX + 17 && mouseY >= itemY) {
               if (mouseY <= itemY + 17 && hoveredItem == null) {
                  hoveredItem = item
               }
            }
         }

         val mapIdType = DataComponents.MAP_ID
         val missingMapId = MapId(-1)
         val mapId = (item as DataComponentHolder).getOrDefault(mapIdType, missingMapId).id()
         val mapData = ClientContext.mapDataCache.getMapData(item, mapId)
         if (tooltips.mapOverlaySetting.value && mapId != -1 && mapData != null) {
            val mapX = itemX + 1
            val mapY = itemY + 1
            renderMapOverlay(graphics, mapData, mapId, mapX.toFloat(), mapY.toFloat())
            if (!KeyUtils.isPhysicalKeyDown(342)) {
               val mapDecorationX = itemX + 1
               val mapDecorationY = itemY + 1
               graphics.itemDecorations(font, item, mapDecorationX, mapDecorationY)
            }
         } else {
            val renderX = itemX + 1
            val renderY = itemY + 1
            graphics.item(item, renderX, renderY)
            val decorationX = itemX + 1
            val decorationY = itemY + 1
            val countLabel: String?
            if (markedCount) {
               val markFormatting = ChatFormatting.RED
               val markPrefix = markFormatting.toString()
               countLabel = markPrefix + "0"
            } else {
               countLabel = null
            }

            graphics.itemDecorations(font, item, decorationX, decorationY, countLabel)
         }

         column++
         if (column >= 9) {
            column = 0
            row++
         }
      }

      if (hoveredItem != null) {
         val airItem: Item = Items.AIR
         if (!hoveredItem.`is`(airItem)) {
            val hoverFont = ClientAccess.collectiveMinecraft.font
            graphics.setTooltipForNextFrame(hoverFont, hoveredItem, mouseX, mouseY)
         }
      }
   }

   companion object {
      private val tooltips: TooltipsModule
         get() = ClientContext.getModule(TooltipsModule::class.java)
      private val sharedMapRenderState: MapRenderState = MapRenderState()

      @JvmStatic
      fun renderMapOverlay(
         graphics: GuiGraphicsExtractor, data: MapItemSavedData?, id: Int, x: Float, y: Float
      ) {
         if (data == null) {
            return
         }

         val poses: Matrix3x2fStack = graphics.pose()
         val renderBuffers = ClientAccess.collectiveMinecraft.gameRenderer.renderBuffers()
         val buffers: MultiBufferSource.BufferSource = MultiBufferSource.immediate()
         poses.pushMatrix()
         poses.translate(x, y)
         val scaleX = 0.125F
         val scaleY = 0.125F
         poses.scale(scaleX, scaleY)
         poses.translate(0.0F, 0.0F)
         val mapRenderer = ClientAccess.collectiveMinecraft.getMapRenderer()
         val mapId = MapId(id)
         val extractState = sharedMapRenderState
         mapRenderer.extractRenderState(mapId, data, extractState)
         val submitState = sharedMapRenderState
         graphics.map(submitState)
         buffers.endBatch()
         poses.popMatrix()
      }
   }
}
