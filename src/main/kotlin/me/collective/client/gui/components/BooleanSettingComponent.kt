package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.client.UIModule
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ClampedInterpolatedFloat
import kotlin.math.max

open class BooleanSettingComponent(
   panel: Panel,
   parentCloseState: CloseState,
   setting: Setting<Boolean>
) : SettingComponent<Boolean>(panel, parentCloseState, setting as BooleanSetting) {

   private val enabledAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat(
      { 2.0f * UIModule.INSTANCE.animSpeedSetting.value },
      true
   )

   private val booleanSetting: BooleanSetting
      get() = this.setting as BooleanSetting

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.isUnavailable) {
         this.enabledAnimation.reset()
         return
      }

      this.enabledAnimation.setTarget(this.booleanSetting.value)
      val enabledProgress = this.enabledAnimation.updateAndGet()
      super.render(poseStack, mouseX, mouseY)

      if ((this.booleanSetting.value || enabledProgress > 0.01) && !this.booleanSetting.groupOnly) {
         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + this.getHorizontalPadding()).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            this.panel.x + max(this.panel.width * enabledProgress - 1.0f, 2.0f),
            this.panel.y + this.yOffset + this.height - 0.5f,
            this.uiModule.colorSetting.value
         )
      }

      val settingName = this.booleanSetting.name
      if (settingName.isNotEmpty()) {
         this.renderWithTextScroll(
            poseStack,
            settingName,
            Runnable {
               FontRenderer.INSTANCE.drawWithShadow(
                  poseStack,
                  settingName,
                  (this.panel.x + 4).toFloat(),
                  this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
                  this.uiModule.textColorSetting.value
               )
            }
         )
      }

      if (this.booleanSetting.visibilityDependency && !this.booleanSetting.groupOnly) {
         val expansionIndicator = if (this.booleanSetting.expanded) "-" else "+"
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            expansionIndicator,
            this.panel.x + this.panel.width - 4 - FontRenderer.INSTANCE.getWidth(expansionIndicator),
            this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
            this.uiModule.textColorSetting.value
         )
      }

      if (this.booleanSetting.groupOnly) {
         val groupIndicator = "..."
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            groupIndicator,
            this.panel.x + this.panel.width - 4 - FontRenderer.INSTANCE.getWidth(groupIndicator),
            this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
            this.uiModule.textColorSetting.value
         )
      }
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         return
      }

      if (this.booleanSetting.groupOnly && button == 0) {
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY)) {
         if (button == 0) {
            this.booleanSetting.value = !this.booleanSetting.value
         }

         if (button == 1 && this.booleanSetting.visibilityDependency) {
            this.booleanSetting.expanded = !this.booleanSetting.expanded
         }
      }
   }
}
