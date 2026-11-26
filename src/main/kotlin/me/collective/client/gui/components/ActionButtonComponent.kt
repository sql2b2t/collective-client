package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.panels.Panel
import me.collective.client.render.GuiDrawUtils
import java.util.function.Supplier

open class ActionButtonComponent : ButtonComponent {
   private val action: Runnable

   constructor(parentPanel: Panel, label: String, action: Runnable) : this(parentPanel, Supplier { label }, action)

   constructor(parentPanel: Panel, labelSupplier: Supplier<String>, action: Runnable) : super(parentPanel, labelSupplier) {
      this.action = action
      this.setHoverOffsetEnabled(true)
   }

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.isHighlighted()) {
         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.x + this.panel.width - 1).toFloat(),
            this.panel.y + this.yOffset + this.height - 0.5f,
            this.uiModule.colorSetting.value
         )
      }

      super.render(poseStack, mouseX, mouseY)
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && button == 0) {
         this.action.run()
      }
   }

   open fun isHighlighted(): Boolean {
      return true
   }
}
