package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import java.awt.Color
import java.util.Objects
import java.util.function.Predicate

abstract class AbstractItemListComponent<T>(
   panel: Panel,
   protected val filter: Predicate<T>,
   initialCapacity: Int
) : AbstractComponent(panel) {

   protected val entries: MutableList<T> = ArrayList(initialCapacity)
   private var selection: T? = null
   private var scrollOffset: Int = 0

   abstract fun getEntryLabel(entry: T): String

   abstract fun getEntryIcon(entry: T): ItemStack

   override fun render(stack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(stack, mouseX, mouseY)
      this.clampScrollOffset()

      stack.pushPose()
      stack.translate((this.panel.x + 1).toFloat(), (this.panel.y + this.yOffset).toFloat(), 0.0f)
      GuiDrawUtils.flush()

      for (row in 0 until 8) {
         if (this.scrollOffset + row >= this.entries.size) break
         val entry = this.entries[this.scrollOffset + row]
         val label = this.getEntryLabel(entry)
         val hoverTextOffset = if (this.isRowHovered(mouseX, mouseY, row)) 0 else 1
         GuiDrawUtils.drawItem(stack, this.getEntryIcon(entry), 1.0f, (16 * row).toFloat(), 0.0f)
         FontRenderer.INSTANCE.drawWithShadow(
            stack,
            this.truncateLabel(label, 20.0f),
            20.0f,
            (16 - FontRenderer.INSTANCE.getHeight()) / 2.0f + hoverTextOffset + 16 * row,
            Color.WHITE
         )
      }

      stack.popPose()
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && this.entries.isNotEmpty()) {
         val relativeMouseY = (mouseY - this.panel.y - this.yOffset).toInt()
         this.selection = this.entries[Mth.clamp(this.scrollOffset + relativeMouseY / 16, 0, this.entries.size - 1)]
      }
   }

   override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollAmount: Double) {
      super.mouseScrolled(mouseX, mouseY, scrollAmount)
      if (this.isMouseOver(mouseX, mouseY)) {
         this.scrollOffset -= scrollAmount.toInt()
         Objects.requireNonNull(ClientAccess.collectiveMinecraft.gui.screen() as? ClientScreen)
      }
   }

   private fun isRowHovered(mouseX: Double, mouseY: Double, row: Int): Boolean {
      val relativeMouseY = (mouseY - this.panel.y - this.yOffset).toInt()
      return this.isMouseOver(mouseX, mouseY) && relativeMouseY / 16 == row
   }

   private fun clampScrollOffset() {
      this.scrollOffset = Mth.clamp(this.scrollOffset, 0, (this.entries.size - 8).coerceAtLeast(0))
   }

   private fun truncateLabel(label: String, textOffset: Float): String {
      if (FontRenderer.INSTANCE.getWidth(label) + textOffset < this.panel.width) {
         return label
      }

      for (endIndex in 0 until label.length) {
         val font = FontRenderer.INSTANCE
         val prefix = label.substring(0, endIndex).trim()
         if (font.getWidth("$prefix...") + textOffset >= this.panel.width) {
            return label.substring(0, endIndex - 1).trim() + "..."
         }
      }
      return label
   }

   fun getSelectedEntry(): T? {
      return this.selection
   }

   override val baseHeight: Int
      get() = 128
}

val <T> AbstractItemListComponent<T>.selectedEntry: T?
   get() = getSelectedEntry()
