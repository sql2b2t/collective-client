package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.registry.RegistrySetScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.RegistrySetSetting

class RegistrySetSettingComponent<E : Any>(
   panel: Panel,
   moduleComponent: ModuleComponent,
   private val registrySetting: RegistrySetSetting<E>
) : SettingComponent<Set<E>>(panel, moduleComponent, registrySetting) {

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (!this.isUnavailable) {
         super.render(poseStack, mouseX, mouseY)
         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + this.getHorizontalPadding()).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.x + this.panel.width - 1).toFloat(),
            this.panel.y + this.yOffset + this.height - 0.5f,
            this.uiModule.colorSetting.value
         )
         val settingName = this.setting.name
         val selectionCount = "[${this.setting.value.size}]"
         val textY = this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            settingName,
            (this.panel.x + 4).toFloat(),
            textY,
            this.uiModule.textColorSetting.value
         )
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            selectionCount,
            this.panel.x + this.panel.width - FontRenderer.INSTANCE.getWidth(selectionCount) - 2.5f,
            textY,
            this.uiModule.textColorSetting.value
         )
      }
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (!this.isUnavailable) {
         super.mouseClicked(mouseX, mouseY, button)
         val parentScreen = ClientAccess.collectiveMinecraft.screen ?: return
         if (this.isMouseOver(mouseX, mouseY) && (button == 0 || button == 1)) {
            RegistrySetScreen(
               parentScreen,
               this.registrySetting
            ).open()
         }
      }
   }
}
