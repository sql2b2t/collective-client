package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.Keybind
import me.collective.client.render.FontRenderer
import me.collective.client.settings.KeybindSetting
import org.lwjgl.glfw.GLFW

class KeybindSettingComponent(
   panel: Panel,
   parentCloseState: CloseState,
   setting: KeybindSetting
) : SettingComponent<Keybind>(panel, parentCloseState, setting) {

   private var listening: Boolean = false

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.isUnavailable) {
         this.listening = false
         return
      }

      super.render(poseStack, mouseX, mouseY)

      val settingName = this.setting.name
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

      val valueLabel = this.getValueLabel()
      FontRenderer.INSTANCE.drawWithShadow(
         poseStack,
         valueLabel,
         this.panel.x + this.panel.width - 4 - FontRenderer.INSTANCE.getWidth(valueLabel),
         this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
         if (this.listening) this.uiModule.colorSetting.value else this.uiModule.textColorSetting.value
      )
   }

   private fun getValueLabel(): String {
      if (this.listening) {
         return "..."
      }

      val keybind = this.setting.value
      val keyName = keybind.displayName
      return if (keybind.mode() == Keybind.Mode.TOGGLE) {
         keyName
      } else {
         keybind.mode().name + " " + keyName
      }
   }

   override fun keyPressed(keyCode: Int) {
      if (!this.listening || this.isUnavailable) {
         this.listening = false
         return
      }

      val clearBinding = keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE
      val current = this.setting.value
      this.setting.value = if (clearBinding) {
         current.withCode(-1).withMouse(false)
      } else {
         current.withCode(keyCode).withMouse(false)
      }
      this.listening = false
      ClientContext.getClickGui().suppressEscapeClose()
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         this.listening = false
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (!this.isMouseOver(mouseX, mouseY)) {
         return
      }

      if (this.listening) {
         this.setting.value = this.setting.value.withCode(button).withMouse(true)
         this.listening = false
         return
      }

      when (button) {
         0 -> this.listening = true
         1 -> {
            val modes = Keybind.Mode.values()
            this.setting.value = this.setting.value.withMode(
               modes[(this.setting.value.mode().ordinal + 1) % modes.size]
            )
         }
         2 -> {
            this.setting.value = this.setting.value.withCode(-1).withMouse(false)
         }
      }
   }
}
