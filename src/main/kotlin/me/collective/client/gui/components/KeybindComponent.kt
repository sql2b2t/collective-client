package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.gui.CursorType
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.modules.Keybind
import me.collective.client.render.FontRenderer
import me.collective.client.settings.Setting
import java.util.function.Function

class KeybindComponent : AbstractComponent {
   private val moduleRow: ModuleComponent
   private val bindingSetting: Setting<Keybind>?
   private var listening: Boolean = false

   constructor(index: Int, moduleRow: ModuleComponent) : super(moduleRow.panel, index) {
      this.moduleRow = moduleRow
      this.bindingSetting = null
   }

   constructor(index: Int, moduleRow: ModuleComponent, bindingSetting: Setting<Keybind>?) : super(moduleRow.panel, index) {
      this.moduleRow = moduleRow
      this.bindingSetting = bindingSetting
   }

   override fun keyPressed(keyCode: Int) {
      if (!this.listening) {
         return
      }

      val clearBinding = keyCode == 261 || keyCode == 256
      this.modifyKeybind { binding ->
         binding.withCode(if (clearBinding) -1 else keyCode).withMouse(false)
      }
      this.listening = false
      ClientContext.getClickGui().suppressEscapeClose()
   }

   private fun getKeybind(): Keybind {
      return if (this.bindingSetting == null) {
         this.moduleRow.module.keybind
      } else {
         this.bindingSetting.value
      }
   }

   private fun modifyKeybind(modifier: Function<Keybind, Keybind>) {
      if (this.bindingSetting == null) {
         this.moduleRow.module.modifyKeybind(modifier)
      } else {
         this.bindingSetting.value = modifier.apply(this.bindingSetting.value)
      }
   }

   override fun render(stack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(stack, mouseX, mouseY)
      if (this.hovered) {
         ClientScreen.cursor = CursorType.INPUT
      }

      var modeLabel = "Key"
      if (this.hovered || this.getKeybind().mode() != Keybind.Mode.TOGGLE) {
         modeLabel = this.getKeybind().mode().name
      }

      var keyLabel = this.getKeybind().displayName
      if (this.listening) {
         keyLabel = "..."
      }

      FontRenderer.INSTANCE.drawWithShadow(
         stack,
         "$modeLabel $keyLabel",
         (this.panel.x + 4).toFloat(),
         this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
         this.uiModule.textColorSetting.value
      )
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (!this.moduleRow.isOpen()) {
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && this.moduleRow.isOpen()) {
         if (button == 0) {
            this.listening = !this.listening
            return
         }

         if (this.listening) {
            this.modifyKeybind { binding ->
               binding.withCode(button).withMouse(true)
            }
            this.listening = !this.listening
            return
         }

         if (button == 1) {
            this.modifyKeybind { binding ->
               val modes = Keybind.Mode.values()
               binding.withMode(modes[(binding.mode().ordinal + 1) % modes.size])
            }
         }
      }
   }
}
