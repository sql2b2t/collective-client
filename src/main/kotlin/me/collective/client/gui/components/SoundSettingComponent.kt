package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.CloseState
import me.collective.client.gui.CursorType
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.gui.screens.SoundSelectionScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.Setting
import me.collective.client.utils.collections.NamedIdentifier

class SoundSettingComponent(
   panel: Panel,
   parentCloseState: CloseState,
   setting: Setting<NamedIdentifier>
) : SettingComponent<NamedIdentifier>(panel, parentCloseState, setting) {

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (!this.isUnavailable) {
         super.render(poseStack, mouseX, mouseY)
         if (this.isMouseOver(mouseX, mouseY)) {
            ClientScreen.cursor = CursorType.POINTER
         }

         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + this.getHorizontalPadding()).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.x + this.panel.width - 1).toFloat(),
            this.panel.y + this.yOffset + this.height - 0.5f,
            this.uiModule.colorSetting.value
         )
         val settingName = this.setting.name
         val soundName = this.setting.value.name

         val label = "$settingName: $soundName"
         this.renderWithTextScroll(
            poseStack,
            label,
            Runnable {
               FontRenderer.INSTANCE.drawWithShadow(
                  poseStack,
                  label,
                  (this.panel.x + 4).toFloat(),
                  this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
                  this.uiModule.textColorSetting.value
               )
            }
         )
      }
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (!this.isUnavailable && this.isMouseOver(mouseX, mouseY) && button == 0) {
         SoundSelectionScreen(
            this.setting,
            this.clickGui
         ).open()
      }
   }
}
