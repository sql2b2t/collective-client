package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.audio.SoundClip
import me.collective.client.gui.CursorType
import me.collective.client.gui.GuiElement
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.modules.PermanentModule
import me.collective.client.utils.FloatAnimation
import net.minecraft.util.Mth

abstract class AbstractComponent @JvmOverloads constructor(
   val panel: Panel,
   override var yOffset: Int = 0
) : ClientAccess, GuiElement {
   protected val hoverAnimation: FloatAnimation = FloatAnimation()
   var flags: Int = 0
      protected set
   var hovered: Boolean = false
      protected set

   protected open fun isLeftClickEligible(): Boolean {
      return this !is ModuleComponent || this.module !is PermanentModule
   }

   protected open fun getHorizontalPadding(): Int {
      return if (this.hasFlag(ComponentFlag.PADDING_SHIFT)) 2 else 1
   }

   open val hoverAnimationValue: Float
      get() = this.hoverAnimation.value

   protected fun clampYToPanel(yCoordinate: Float): Float {
      return Mth.clamp(
         yCoordinate,
         (this.panel.y + this.panel.headerHeight - 2).toFloat(),
         (this.panel.y + this.panel.height).toFloat()
      )
   }

   fun hasFlag(flag: ComponentFlag): Boolean {
      return (this.flags and flag.mask) == flag.mask
   }

   fun addFlags(vararg flagsToAdd: ComponentFlag) {
      for (flag in flagsToAdd) {
         this.flags = this.flags or flag.mask
      }
   }

   override fun render(stack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.isMouseOver(mouseX, mouseY)) {
         if (!this.hovered &&
            this.uiModule.hoverSetting.value &&
            !this.hasFlag(ComponentFlag.SILENT) &&
            !this.hasFlag(ComponentFlag.SILENT_HOVER)) {
            val hoverSound: SoundClip? = ClientContext.soundManager.getSound(this.uiModule.hoverSoundSetting.value)
            hoverSound?.play(this.uiModule.hoverVolumeSetting.value)
         }

         this.hovered = true
         ClientContext.getClickGui().markHovered()
         ClientScreen.cursor = CursorType.POINTER
      } else {
         this.hovered = false
      }

      this.hoverAnimation.setTarget(this.isMouseOver(mouseX, mouseY), 65L)
      super.render(stack, mouseX, mouseY)
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return mouseX > this.panel.x &&
         mouseX < this.panel.x + this.panel.width &&
         mouseY > this.clampYToPanel((this.panel.y + this.yOffset).toFloat()) &&
         mouseY < this.clampYToPanel((this.panel.y + this.height + this.yOffset).toFloat())
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isMouseOver(mouseX, mouseY) && !this.hasFlag(ComponentFlag.SILENT)) {
         if (button == 0 && this.isLeftClickEligible()) {
            if (this.uiModule.leftClickSetting.value) {
               val leftClickSound: SoundClip? = ClientContext.soundManager.getSound(this.uiModule.leftClickSoundSetting.value)
               leftClickSound?.play(this.uiModule.leftClickVolumeSetting.value)
            }
         } else if (button == 1 && this.uiModule.rightClickSetting.value) {
            val rightClickSound: SoundClip? = ClientContext.soundManager.getSound(this.uiModule.rightClickSoundSetting.value)
            rightClickSound?.play(this.uiModule.rightClickVolumeSetting.value)
         }
      }
   }
}
