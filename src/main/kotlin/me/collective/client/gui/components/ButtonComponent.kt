package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.panels.Panel
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.render.RenderUtils
import java.awt.Color
import java.util.function.Supplier

open class ButtonComponent : AbstractComponent {
   private val labelSupplier: Supplier<String>
   var isHoverOffsetEnabled: Boolean = false

   constructor(parentPanel: Panel, labelSupplier: Supplier<String>) : super(parentPanel, 0) {
      this.labelSupplier = labelSupplier
   }

   constructor(parentPanel: Panel, label: String) : super(parentPanel, 0) {
      this.labelSupplier = Supplier { label }
   }

   val label: String @JvmName("getLabelProp") get() = getLabel()

   open fun getLabel(): String {
      return this.labelSupplier.get()
   }

   override val height: Int
      get() = super.height + FontRenderer.INSTANCE.getHeight() * (this.getLabel().split("\n").size - 1) + 1

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      val lines = this.getLabel().split("\n")
      for (lineIndex in lines.indices) {
         val hoverOffset = if (this.isHoverOffsetEnabled && this.isMouseOver(mouseX, mouseY)) 1.0f else 0.0f
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            lines[lineIndex],
            (this.panel.x + 3).toFloat(),
            this.panel.y + this.yOffset + this.textYOffset + lineIndex * FontRenderer.INSTANCE.getHeight() + 1.0f - hoverOffset,
            Color.WHITE
         )
      }

      super.render(poseStack, mouseX, mouseY)
   }

   protected open fun renderButtonBackground(poseStack: PoseStack) {
      var buttonColor = this.uiModule.buttonSetting.value
      buttonColor = ColorUtils.withAlpha(buttonColor, buttonColor.alpha / 255.0f * 0.1f)
      if (this.uiModule.elementGradientSetting.value) {
         GuiDrawUtils.fillVerticalGradient(
            poseStack,
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.width - 1).toFloat(),
            this.height - 0.5f,
            buttonColor.hashCode(),
            ColorUtils.toArgb(buttonColor, 0)
         )
      } else {
         RenderUtils.INSTANCE.fillRect(
            poseStack,
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.x + this.panel.width - 1).toFloat(),
            this.panel.y + this.yOffset + this.height - 0.5f,
            this.uiModule.buttonSetting.value
         )
      }
   }
}

val ButtonComponent.label: String
   get() = getLabel()

fun ButtonComponent.setHoverOffsetEnabled(enabled: Boolean) {
   this.isHoverOffsetEnabled = enabled
}
