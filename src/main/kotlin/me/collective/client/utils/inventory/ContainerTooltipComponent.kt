package me.collective.client.utils.inventory

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractContainerScreenRenderTooltipEvent
import me.collective.client.modules.render.TooltipsModule
import me.collective.client.utils.KeyUtils

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MapRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.state.MapRenderState
import net.minecraft.core.component.DataComponentHolder
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

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

   override fun renderImage(
      font: Font, x: Int, y: Int, tooltipWidth: Int, tooltipHeight: Int, graphics: GuiGraphics
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
               graphics.renderItemDecorations(font, item, mapDecorationX, mapDecorationY)
            }
         } else {
            val renderX = itemX + 1
            val renderY = itemY + 1
            graphics.renderItem(item, renderX, renderY)
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

            graphics.renderItemDecorations(font, item, decorationX, decorationY, countLabel)
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
            graphics.renderTooltip(hoverFont, hoveredItem, mouseX, mouseY)
         }
      }
   }

   companion object {
      private val tooltips: TooltipsModule
         get() = ClientContext.getModule(TooltipsModule::class.java)
      private val sharedMapRenderState: MapRenderState = MapRenderState()

      @JvmStatic
      fun renderMapOverlay(
         graphics: GuiGraphics, data: MapItemSavedData?, id: Int, x: Float, y: Float
      ) {
         if (data == null) {
            return
         }

         val poses: PoseStack = graphics.pose()
         val renderBuffers = ClientAccess.collectiveMinecraft.renderBuffers()
         val buffers: MultiBufferSource.BufferSource = renderBuffers.bufferSource()
         poses.pushPose()
         poses.translate(x, y, 0.0f)
         val scaleX = 0.125F
         val scaleY = 0.125F
         poses.scale(scaleX, scaleY, 1.0f)
         val mapRenderer = ClientAccess.collectiveMinecraft.getMapRenderer()
         val mapId = MapId(id)
         val extractState = sharedMapRenderState
         mapRenderer.extractRenderState(mapId, data, extractState)
         val submitState = sharedMapRenderState
         mapRenderer.render(submitState, poses, buffers, false, LightTexture.FULL_BRIGHT)
         buffers.endBatch()
         poses.popPose()
      }
   }
}
