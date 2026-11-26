package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.render.FontRenderer

abstract class ColorActionButton(
   yOffset: Int,
   protected val editor: ColorSettingComponent,
   protected val label: String,
   protected var action: Runnable
) : AbstractComponent(editor.panel, yOffset) {

   abstract fun getLabelOffsetX(): Float

   open fun getName(): String {
      return this.label
   }

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.editor.isUnavailable) {
         return
      }

      super.render(poseStack, mouseX, mouseY)
      FontRenderer.INSTANCE.drawWithShadow(
         poseStack,
         this.getName(),
         this.panel.x + this.getLabelOffsetX(),
         this.panel.y + this.yOffset + 0.0f + this.textYOffset - this.hoverAnimationValue,
         this.uiModule.textColorSetting.value
      )
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.editor.isUnavailable) {
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && button == 0) {
         this.action.run()
      }
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return mouseX > this.panel.x + this.getLabelOffsetX() &&
         mouseX < this.panel.x + this.getLabelOffsetX() + FontRenderer.INSTANCE.getWidth(this.getName()) &&
         mouseY > this.panel.y + this.yOffset + 0.0f &&
         mouseY < this.panel.y + this.yOffset + 0.0f + FontRenderer.INSTANCE.getHeight() + this.textYOffset * 2.0f
   }
}
