package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.utils.collections.IntPair
import me.collective.client.render.RenderUtils
import me.collective.client.utils.ClampedInterpolatedFloat
import java.util.Objects
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class InventoryTransferAnimation(
   stack: ItemStack,
   source: IntPair,
   destination: IntPair
) : ClientAccess {
   private val progress: ClampedInterpolatedFloat
   private val stack: ItemStack
   private val destination: IntPair
   private val source: IntPair

   init {
      this.progress = ClampedInterpolatedFloat(2.0f)
      this.stack = stack.copy()
      this.destination = destination
      this.source = source
      this.progress.setValue(0.0f)
   }

   fun render(graphics: GuiGraphics) {
      this.progress.setTarget(true)
      val fraction: Float = this.progress.updateAndGet()
      val startX: Float = this.source.first.toFloat()
      val endX: Int = this.destination.first
      val drawX: Int = (startX + (endX - this.source.first) * fraction).toInt()
      val startY: Float = this.source.second.toFloat()
      val endY: Int = this.destination.second
      val drawY: Int = (startY + (endY - this.source.second) * fraction).toInt()
      if (fraction.toDouble() > 0.8) {
         val red: Float = 1.0f
         val green: Float = 1.0f
         val blue: Float = 1.0f
         val alpha: Float = 1.0f
         val fade: Float = fraction - 0.8f
         RenderUtils.setRenderColor(
            red, green, blue,
            alpha - fade / 0.2f
         )
      }

      val renderedStack: ItemStack = this.stack
      graphics.renderItem(renderedStack, drawX, drawY)
      val decoratedStack: ItemStack = this.stack
      val font: Font = ClientAccess.collectiveMinecraft.font
      graphics.renderItemDecorations(font, decoratedStack, drawX, drawY)
      if (fraction.toDouble() > 0.8) {
         val red: Float = 1.0f
         val green: Float = 1.0f
         val blue: Float = 1.0f
         RenderUtils.setRenderColor(
            red, green, blue,
            1.0f
         )
      }
   }

   fun isFinished(): Boolean {
      return this.progress.getValue() == 1.0f
   }

   override fun equals(other: Any?): Boolean {
      if (this === other) {
         return true
      }
      if (other == null || this.javaClass != other.javaClass) {
         return false
      }
      val animation = other as InventoryTransferAnimation
      val item: Item = this.stack.item
      val otherStack: ItemStack = animation.stack
      return Objects.equals(item, otherStack.item) && Objects.equals(this.destination, animation.destination)
   }

   override fun hashCode(): Int {
      return Objects.hash(this.stack.item, this.destination)
   }

}
