package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.gui.screens.TradeEditorScreen
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.TradeDefinition
import me.collective.client.utils.inventory.TradeEnchantmentEntry
import me.collective.client.utils.math.FloatBounds
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.roundToInt

class TradeItemsComponent(editor: TradeEditorPanel, trade: TradeDefinition) :
   AbstractComponent(editor) {

   private val fromHoverAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat(
      Supplier { 4.0f * this.uiModule.getAnimationSpeed() },
      false
   )
   private val toHoverAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat(
      Supplier { 4.0f * this.uiModule.getAnimationSpeed() },
      false
   )
   private val screen: TradeEditorScreen = editor.screen
   var isSelectorOpen: Boolean = false
      private set
   var fromStack: ItemStack = trade.from().defaultInstance
   var toStack: ItemStack = trade.to().defaultInstance

   init {
      this.applyConfiguredEnchantments(
         if (this.fromStack.`is`(Items.EMERALD)) this.toStack else this.fromStack,
         trade
      )
   }

   override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poses, mouseX, mouseY)
      GuiDrawUtils.flush()
      poses.pushPose()
      poses.translate(
         (this.panel.x + 1).toFloat(),
         (this.panel.y + this.yOffset).toFloat(),
         0.0f
      )
      GuiDrawUtils.drawItem(
         poses,
         this.fromStack,
         1.0f,
         -this.fromHoverAnimation.value.roundToInt().toFloat(),
         0.0f
      )
      GuiDrawUtils.drawItem(
         poses,
         this.toStack,
         (this.panel.width - 4 - 16).toFloat(),
         -this.toHoverAnimation.value.roundToInt().toFloat(),
         0.0f
      )
      val arrowWidth = FontRenderer.INSTANCE.getWidth(">")
      FontRenderer.INSTANCE.drawWithoutShadow(
         poses,
         ">",
         (this.panel.width - arrowWidth) / 2.0f,
         4.0f,
         Color.white
      )
      val fromHoverBounds = FloatBounds.fromPositionAndSize(
         (this.panel.x + 2).toFloat(),
         (this.panel.y + this.yOffset).toFloat(),
         16.0f,
         16.0f
      )
      if (fromHoverBounds.contains(mouseX, mouseY)) {
         this.screen.tooltipStack = this.fromStack
         this.fromHoverAnimation.setTarget(true)
      } else {
         this.fromHoverAnimation.setTarget(false)
      }

      val toHoverBounds = FloatBounds.fromPositionAndSize(
         (this.panel.x + this.panel.width - 2 - 16).toFloat(),
         (this.panel.y + this.yOffset).toFloat(),
         16.0f,
         16.0f
      )
      if (toHoverBounds.contains(mouseX, mouseY)) {
         this.screen.tooltipStack = this.toStack
         this.toHoverAnimation.setTarget(true)
      } else {
         this.toHoverAnimation.setTarget(false)
      }

      this.fromHoverAnimation.updateAndGet()
      this.toHoverAnimation.updateAndGet()
      this.drawHoverGradient(
         poses,
         0.0f,
         0.0f,
         32.0f,
         this.baseHeight - 0.5f,
         true
      )

      poses.translate(
         (this.panel.width - 34).toFloat(),
         0.0f,
         0.0f
      )
      this.drawHoverGradient(
         poses,
         0.0f,
         0.0f,
         32.0f,
         this.baseHeight - 0.5f,
         false
      )

      poses.popPose()
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
         if (this.isSelectorOpen) {
            this.isSelectorOpen = false
         } else {
            val relativeMouseX = (mouseX - this.panel.x).toInt()
            val selector: TradeSelectorPanel<*>
            if (relativeMouseX < this.panel.width / 2) {
               selector = this.createSelector(button, true)
               selector.x = this.panel.x - selector.width - 4
               selector.y = this.panel.y
            } else {
               selector = this.createSelector(button, false)
               selector.x = this.panel.x + this.panel.width + 4
               selector.y = this.panel.y
            }

            this.isSelectorOpen = true
            val screen = ClientAccess.collectiveMinecraft.gui.screen() as? ClientScreen
            screen?.panels?.add(selector)
         }
      }
   }

   private fun drawHoverGradient(
      poses: PoseStack, x: Float, y: Float, width: Float, height: Float, fromSide: Boolean
   ) {
      val hoverAnimation = if (fromSide) this.fromHoverAnimation else this.toHoverAnimation
      val color = this.uiModule.colorSetting.value
      val transparentColor = ColorUtils.toArgb(color, 0)
      val hoverColor = ColorUtils.toArgb(
         color,
         hoverAnimation.value * 0.2f
      )
      if (fromSide) {
         GuiDrawUtils.fillHorizontalGradient(
            poses, x, y, width, height, hoverColor, transparentColor
         )
      } else {
         GuiDrawUtils.fillHorizontalGradient(
            poses, x, y, width, height, transparentColor, hoverColor
         )
      }
   }

   private fun createSelector(button: Int, fromSide: Boolean): TradeSelectorPanel<*> {
      var stack = this.toStack
      if (fromSide) {
         stack = this.fromStack
      }

      return if (button != 0 && !stack.`is`(Items.EMERALD)) {
         TradeEnchantmentSelectorPanel(this, fromSide)
      } else {
         TradeItemSelectorPanel(this, fromSide)
      }
   }

   private fun applyConfiguredEnchantments(stack: ItemStack, trade: TradeDefinition) {
      for (entry in trade.enchants()) {
         val enchantment = entry as TradeEnchantmentEntry
         val holder = EnchantmentUtils.getHolder(enchantment.name())
         if (holder != null) {
            stack.enchant(holder, enchantment.level())
         }
      }
   }

   override val baseHeight: Int
      get() = 16

   fun closeSelector() {
      this.isSelectorOpen = false
   }
}
