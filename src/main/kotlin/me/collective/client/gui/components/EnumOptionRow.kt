package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.render.FontRenderer
import me.collective.client.settings.NamedEntry

class EnumOptionRow<E>(
   val enumComponent: EnumSettingComponent<E>,
   val label: String,
   yOffset: Int,
   val selectableIndex: Int
) : AbstractComponent(enumComponent.panel, yOffset) where E : Enum<E>, E : NamedEntry {

   override val height: Int
      get() = if (this.enumComponent.isUnavailable) 0 else super.height

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poseStack, mouseX, mouseY)
      FontRenderer.INSTANCE.drawWithShadow(
         poseStack,
         this.label,
         this.panel.x + this.panel.width / 2.0f - FontRenderer.INSTANCE.getWidth(this.label) / 2.0f,
         this.enumComponent.y + this.yOffset + this.textYOffset - 1.0f - this.hoverAnimationValue,
         this.uiModule.textColorSetting.value
      )
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.enumComponent.isUnavailable || !this.isMouseOver(mouseX, mouseY) || !this.enumComponent.isExpanded) {
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (this.enumComponent.parentCloseState.isOpen() && button == 0) {
         val enumConstants = this.enumComponent.setting.value.declaringJavaClass.enumConstants ?: emptyArray()
         for (candidate in enumConstants) {
            val candidateDisplayName = (candidate as? NamedEntry)?.getName() ?: candidate.name
            if (candidateDisplayName.equals(this.label, ignoreCase = true) || candidate.name.equals(this.label, ignoreCase = true)) {
               this.enumComponent.setting.value = candidate
               this.enumComponent.selectedIndex = this.selectableIndex
            }
         }
      }
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return mouseX > this.enumComponent.x &&
         mouseX < this.enumComponent.x + this.panel.width &&
         mouseY > this.enumComponent.y + this.yOffset &&
         mouseY < this.enumComponent.y + this.baseHeight + this.yOffset
   }
}
