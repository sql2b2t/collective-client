package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractContainerScreenRenderTooltipEvent
import me.collective.client.render.ColorUtils
import me.collective.client.utils.inventory.ContainerTooltipComponent
import me.collective.client.utils.world.MapDataCache
import me.collective.client.utils.network.PacketSender
import me.collective.client.settings.Setting
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.KeyUtils
import java.awt.Color
import java.util.ArrayList
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.BundleItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.BundleContents
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import org.apache.commons.lang3.math.Fraction
import org.joml.Matrix3x2fStack
import org.lwjgl.glfw.GLFW

class BundleTooltipComponent(
   val entries: List<BundleEntry>,
   val bundleContents: BundleContents,
   val slot: Slot
) : ClientAccess, ClientTooltipComponent {

   override fun renderImage(font: Font, x: Int, y: Int, tooltipWidth: Int, tooltipHeight: Int, graphics: GuiGraphics) {
      val columns: Int = this.columns()
      var renderX: Int = x + Math.max((tooltipWidth - this.getWidth(font)) / 2, 0)
      val mouseX: Int = AbstractContainerScreenRenderTooltipEvent.mouseX
      val mouseY: Int = AbstractContainerScreenRenderTooltipEvent.mouseY
      var hoveredItem: ItemStack? = null

      for (index in this.entries.indices) {
         val entry: BundleEntry = this.entries[index]
         val itemX: Int = renderX + index % columns * CELL_SIZE
         val itemY: Int = y + index / columns * CELL_SIZE
         if (mouseX >= itemX && mouseX < itemX + 20 && mouseY >= itemY && mouseY < itemY + 20) {
            hoveredItem = entry.item
            if (this.bundleContents.getSelectedItem() != index) {
               val selectTime = System.currentTimeMillis()
               if (selectTime - lastBundleSelectTime >= SELECT_THROTTLE_MILLIS) {
                  lastBundleSelectTime = selectTime
                  BundleItem.toggleSelectedItem(this.slot.getItem(), index)
                  PacketSender.send(ServerboundSelectBundleItemPacket(this.slot.index, index))
               }
            }
         }
         this.renderEntry(font, graphics, entry.item, itemX, itemY)
         if (entry.selected) {
            this.renderSelection(graphics, font, itemX, itemY)
         }
      }

      this.renderWeightBar(graphics, renderX, y + Math.ceil(this.entries.size.toDouble() / columns).toInt() * CELL_SIZE)
      if (hoveredItem != null && !hoveredItem.isEmpty()) {
         graphics.setTooltipForNextFrame(ClientAccess.collectiveMinecraft.font, hoveredItem, mouseX, mouseY)
      }
   }

   private fun renderEntry(font: Font, graphics: GuiGraphics, item: ItemStack, x: Int, y: Int) {
      val mapIdComponent: MapId? = item.get(DataComponents.MAP_ID)
      val mapId: Int = if (mapIdComponent == null) -1 else mapIdComponent.id()
      val mapCache: MapDataCache = ClientContext.mapDataCache
      val mapData: MapItemSavedData? = if (mapId == -1) null else mapCache.getMapData(item, mapId)
      val mapOverlay: Setting<Boolean> = TOOLTIPS.mapOverlaySetting
      if (mapOverlay.getValue() && mapData != null) {
         ContainerTooltipComponent.renderMapOverlay(graphics, mapData, mapId, (x + 1).toFloat(), (y + 1).toFloat())
         if (!KeyUtils.isPhysicalKeyDown(LEFT_ALT_KEY)) {
            graphics.renderItemDecorations(font, item, x + 1, y + 1)
         }
      } else {
         graphics.renderItem(item, x + 2, y + 2)
         graphics.renderItemDecorations(font, item, x + 2, y + 2)
      }
   }

   private fun renderSelection(graphics: GuiGraphics, font: Font, x: Int, y: Int) {
      var progress: Float = (System.currentTimeMillis() % 2000L) / 1000.0F
      if (progress >= 1.0F) {
         progress = 2.0F - progress
      }
      val color: Color = ColorUtils.blend(Color.RED, Color.RED.darker(), progress)
      graphics.nextStratum()
      val poses: Matrix3x2fStack = graphics.pose()
      poses.pushMatrix()
      try {
         graphics.drawString(font, "x", x + 3, y + 1, color.hashCode(), true)
      } finally {
         poses.popMatrix()
      }
   }

   private fun renderWeightBar(graphics: GuiGraphics, x: Int, y: Int) {
      val barWidth: Int = this.columns() * CELL_SIZE - 2
      val weight: Fraction = this.bundleContents.weight()
      val filledWidth: Int = Mth.mulAndTruncate(weight, barWidth)
      weightWidthAnimation.setTarget(filledWidth.toFloat(), 150L)
      val animatedWidth: Int = weightWidthAnimation.getValue().toInt()
      weightAlphaAnimation.setTarget(filledWidth != 0, 250L)
      val alpha: Float = weightAlphaAnimation.getValue()
      val color: Int = ARGB.color(alpha, this.slot.getItem().getBarColor())
      val barColor = Color(color, true)
      graphics.fill(x + 4, y + 2, x + 4 + barWidth, y + 8, ColorUtils.darken(barColor, 0.3F).hashCode())
      graphics.fill(x + 2, y, x + 2 + animatedWidth, y + 6, color)
   }

   private fun columns(): Int {
      return if (this.entries.size > 16) MAX_COLUMNS else 5
   }

   override fun getHeight(font: Font): Int {
      return Math.ceil(this.entries.size.toDouble() / this.columns()).toInt() * CELL_SIZE + 12
   }

   override fun getWidth(font: Font): Int {
      return this.columns() * CELL_SIZE + 2
   }

   data class BundleEntry(val item: ItemStack, val selected: Boolean)

   companion object {
      private const val CELL_SIZE: Int = 20
      private const val MAX_COLUMNS: Int = 6
      private const val LEFT_ALT_KEY: Int = GLFW.GLFW_KEY_LEFT_ALT
      private const val SELECT_THROTTLE_MILLIS: Long = 200L
      private var lastBundleSelectTime: Long = 0L
      private val TOOLTIPS: TooltipsModule get() = ClientContext.getModule(TooltipsModule::class.java)!!
      private val weightWidthAnimation: FloatAnimation = FloatAnimation()
      private val weightAlphaAnimation: FloatAnimation = FloatAnimation()

      @JvmStatic
      fun fromSlot(slot: Slot): BundleTooltipComponent? {
         val contents: BundleContents? = slot.getItem().get(DataComponents.BUNDLE_CONTENTS)
         if (contents == null) {
            return null
         }
         val entries: MutableList<BundleEntry> = ArrayList()
         var index: Int = 0
         for (item: ItemStack in contents.items()) {
            val selected: Boolean = contents.hasSelectedItem() && contents.getSelectedItem() == index
            entries.add(BundleEntry(item, selected))
            index++
         }
         return BundleTooltipComponent(entries, contents, slot)
      }
   }
}
