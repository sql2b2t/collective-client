package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.gui.CloseState
import me.collective.client.gui.CursorType
import me.collective.client.gui.GuiElement
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.InterpolatedFloat
import net.minecraft.util.Mth
import org.joml.Matrix4f
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

class ColorSettingComponent(
   panel: Panel,
   parent: CloseState,
   setting: Setting<Color>
) : SettingComponent<Color>(panel, parent, setting as ColorSetting), CloseState {

   private val controls: MutableList<GuiElement> = ArrayList(4)
   val invalidPasteTimer: ElapsedTimer = ElapsedTimer()
   val pasteTimer: ElapsedTimer = ElapsedTimer()
   val copyTimer: ElapsedTimer = ElapsedTimer()
   private var expanded: Boolean = false
   private var draggingSaturationBrightness: Boolean = false
   private var draggingHue: Boolean = false
   private var draggingAlpha: Boolean = false
   private val hsb: FloatArray = FloatArray(3)
   protected val saturationMarkerX: InterpolatedFloat = InterpolatedFloat(0.35f, true)
   protected val brightnessMarkerY: InterpolatedFloat = InterpolatedFloat(0.35f, true)
   protected val hueMarkerX: InterpolatedFloat = InterpolatedFloat(0.35f, true)
   protected val alphaMarkerX: InterpolatedFloat = InterpolatedFloat(0.35f, true)
   private val markerContrast: ClampedInterpolatedFloat = ClampedInterpolatedFloat(0.35f, false)
   private val markerExpansion: ClampedInterpolatedFloat = ClampedInterpolatedFloat(0.35f, true)

   private val colorSetting: ColorSetting
      get() = this.setting as ColorSetting

   init {
      val controlsY = this.yOffset + this.baseHeight + 3 + this.pickerSize() + this.getControlSectionOffset()
      this.controls.add(CopyControl(controlsY, this))
      this.controls.add(PasteControl(controlsY, this))
      if (!this.colorSetting.isThemeColor) {
         this.controls.add(SyncControl(this))
      }
      this.controls.add(RainbowControl(this))
   }

   override fun init() {
      this.updateHsbFromColor(this.setting.value)
      this.controls.forEach { it.init() }
   }

   override fun isClosed(): Boolean {
      return this.parentCloseState.isClosed() || !this.expanded
   }

   private fun getSelectedColor(): Color {
      val alpha = this.setting.value.alpha
      return ColorUtils.withAlpha(Color.getHSBColor(this.hsb[0], this.hsb[1], this.hsb[2]), alpha)
   }

   override val height: Int
      get() {
         if (!this.expanded || this.isUnavailable) {
            return super.height
         }
         val toggleRows = this.controls.size - 2
         val controlsHeight = this.baseHeight + toggleRows * (this.baseHeight + 3)
         return this.baseHeight + 3 + this.pickerSize() + this.getControlSectionOffset() + controlsHeight
      }

   override fun keyPressed(keyCode: Int) {
      if (this.isUnavailable) {
         return
      }
      this.controls.forEach { it.keyPressed(keyCode) }
      super.keyPressed(keyCode)
   }

   private fun getPickerY(): Float = (this.panel.y + this.yOffset).toFloat()

   private fun getPickerX(): Float = this.panel.x.toFloat()

   private fun pickerSize(): Int = max(0, this.panel.width - 10)

   private fun getControlSectionOffset(): Int {
      val hueAndGap = CONTROL_GAP + SLIDER_HEIGHT
      val alphaAndGap = if (this.colorSetting.opaqueColor) 0 else CONTROL_GAP + SLIDER_HEIGHT
      return hueAndGap + alphaAndGap + CONTROL_GAP
   }

   private fun getAccentColor(): Color {
      return ColorUtils.withAlpha(this.uiModule.colorSetting.value, 255)
   }

   private fun renderPreviewCheckerboard(poses: PoseStack) {
      val x = this.getPickerX().toInt() + this.panel.width - 11
      val y = this.getPickerY().toInt() + 2
      val light = -0x1
      val dark = -0x555556
      for (row in 0 until 2) {
         for (column in 0 until 2) {
            val color = if ((row + column) % 2 == 0) light else dark
            GuiDrawUtils.fillRect(poses, (x + column * 4).toFloat(), (y + row * 4).toFloat(), (x + column * 4 + 4).toFloat(), (y + row * 4 + 4).toFloat(), color)
         }
      }
   }

   private fun squareY(): Int = this.getPickerY().toInt() + this.baseHeight + 3

   private fun hueY(): Int = this.squareY() + this.pickerSize() + CONTROL_GAP

   private fun alphaY(): Int = this.hueY() + SLIDER_HEIGHT + CONTROL_GAP

   private fun controlsY(): Int {
      val lastSliderBottom = if (this.colorSetting.opaqueColor) {
         this.hueY() + SLIDER_HEIGHT
      } else {
         this.alphaY() + SLIDER_HEIGHT
      }
      return lastSliderBottom + CONTROL_GAP
   }

   private fun renderAlphaSlider(poses: PoseStack, x: Int, y: Int, width: Int, color: Color, alpha: Float) {
      val cell = 4
      var left = 0
      while (left < width) {
         var top = 0
         while (top < SLIDER_HEIGHT) {
            val checker = if (((left / cell + top / cell) and 1) == 0) -0x1 else -0x666667
            GuiDrawUtils.fillRect(
               poses,
               (x + left).toFloat(),
               (y + top).toFloat(),
               min(x + width, x + left + cell).toFloat(),
               min(y + SLIDER_HEIGHT, y + top + cell).toFloat(),
               checker
            )
            top += cell
         }
         left += cell
      }
      val rgb = color.rgb and 0x00FFFFFF
      GuiDrawUtils.fillHorizontalGradient(poses, x.toFloat(), y.toFloat(), width.toFloat(), SLIDER_HEIGHT.toFloat(), (rgb or -0x1000000), rgb)
      this.alphaMarkerX.setTarget((width - 1) * (1.0f - alpha))
      val markerX = x + this.alphaMarkerX.updateAndGet()
      val outline = if (this.draggingAlpha) Color.WHITE else this.getAccentColor()
      GuiDrawUtils.fillRect(poses, markerX - 1.0f, (y - 1).toFloat(), markerX + 2.0f, (y + SLIDER_HEIGHT + 1).toFloat(), outline)
      GuiDrawUtils.fillRect(poses, markerX, y.toFloat(), markerX + 1.0f, (y + SLIDER_HEIGHT).toFloat(), Color.WHITE)
   }

   private fun updateControls(mouseX: Double, mouseY: Double) {
      var controlY = this.controlsY()
      for (control in this.controls) {
         control.yOffset = controlY - this.panel.y
         control.update(mouseX, mouseY)
         if (control !is ColorActionButton) {
            controlY += this.baseHeight + 3
         }
      }
   }

   override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poses, mouseX, mouseY)
      if (this.isUnavailable || !this.expanded) {
         this.saturationMarkerX.setValue(0.0f)
         this.brightnessMarkerY.setValue(0.0f)
         this.hueMarkerX.setValue(0.0f)
         this.alphaMarkerX.setValue(0.0f)
      }
      if (this.isUnavailable) {
         return
      }

      this.renderWithTextScroll(
         poses,
         this.setting.name,
         Runnable {
            FontRenderer.INSTANCE.drawWithShadow(
               poses,
               this.setting.name,
               (this.panel.x + 4).toFloat(),
               this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
               this.uiModule.textColorSetting.value
            )
         }
      )

      val previewX = this.getPickerX() + this.panel.width - 11
      val previewY = this.getPickerY() + 2
      if (!this.colorSetting.opaqueColor) {
         this.renderPreviewCheckerboard(poses)
      }
      GuiDrawUtils.fillRect(poses, previewX, previewY, previewX + 8, previewY + 8, this.setting.value)
      if (!this.expanded) {
         return
      }

      val size = this.pickerSize()
      val squareX = this.getPickerX().toInt() + 5
      val squareY = this.squareY()
      var alpha = this.setting.value.alpha
      if (this.draggingSaturationBrightness) {
         ClientScreen.cursor = CursorType.POINTER
         this.hsb[1] = Mth.clamp((mouseX - squareX) / max(size - 1, 1), 0.0, 1.0).toFloat()
         this.hsb[2] = 1.0f - Mth.clamp((mouseY - squareY) / max(size - 1, 1), 0.0, 1.0).toFloat()
      } else if (this.draggingHue) {
         ClientScreen.cursor = CursorType.POINTER
         this.hsb[0] = Mth.clamp((mouseX - squareX) / max(size - 1, 1), 0.0, 1.0).toFloat()
      } else if (this.draggingAlpha && !this.colorSetting.opaqueColor) {
         ClientScreen.cursor = CursorType.POINTER
         alpha = Math.round((1.0f - Mth.clamp((mouseX - squareX) / max(size - 1, 1), 0.0, 1.0).toFloat()) * 255.0f)
      }

      val hueColor = Color.HSBtoRGB(this.hsb[0], 1.0f, 1.0f)
      GuiDrawUtils.fillRect(poses, squareX.toFloat(), squareY.toFloat(), (squareX + size).toFloat(), (squareY + size).toFloat(), hueColor)
      GuiDrawUtils.fillGradientRect(
         poses.last().pose(),
         squareX.toFloat(),
         squareY.toFloat(),
         size.toFloat(),
         size.toFloat(),
         -0x1,
         hueColor,
         -0x1000000,
         -0x1000000
      )
      val matrix: Matrix4f = poses.last().pose()
      val hueX = squareX
      val hueY = this.hueY()
      val hueColors = intArrayOf(
         -0x10000,
         -0x100,
         -0xff0100,
         -0xff0001,
         -0xffff01,
         -0xff01,
         -0x10000
      )
      GuiDrawUtils.fillHorizontalGradient(matrix, hueX.toFloat(), hueY.toFloat(), size.toFloat(), SLIDER_HEIGHT.toFloat(), *hueColors)
      this.hueMarkerX.setTarget(this.hsb[0] * max(size - 1, 0))
      val hueMarker = hueX + this.hueMarkerX.updateAndGet()
      val hueOutline = if (this.draggingHue) Color.WHITE else this.getAccentColor()
      GuiDrawUtils.fillRect(poses, hueMarker - 1.0f, (hueY - 1).toFloat(), hueMarker + 2.0f, (hueY + SLIDER_HEIGHT + 1).toFloat(), hueOutline)
      GuiDrawUtils.fillRect(poses, hueMarker, hueY.toFloat(), hueMarker + 1.0f, (hueY + SLIDER_HEIGHT).toFloat(), Color.WHITE)

      val selectedColor = Color.getHSBColor(this.hsb[0], this.hsb[1], this.hsb[2])
      if (!this.colorSetting.opaqueColor) {
         this.renderAlphaSlider(poses, hueX, this.alphaY(), size, selectedColor, alpha / 255.0f)
      }

      this.saturationMarkerX.setTarget(this.hsb[1] * max(size - 1, 0))
      this.brightnessMarkerY.setTarget((1.0f - this.hsb[2]) * max(size - 1, 0))
      val markerX = squareX + this.saturationMarkerX.updateAndGet()
      val markerY = squareY + this.brightnessMarkerY.updateAndGet()
      this.markerExpansion.setTarget(if (this.draggingSaturationBrightness) 1.0f else 0.0f)
      val markerSize = 3.0f + this.markerExpansion.updateAndGet()
      val luminance = (0.2126f * selectedColor.red + 0.7152f * selectedColor.green + 0.0722f * selectedColor.blue) / 255.0f
      this.markerContrast.setTarget(if (luminance > 0.5f) 1.0f else 0.0f)
      val markerOutline = if (this.markerContrast.updateAndGet() > 0.5f) Color.BLACK.rgb else Color.WHITE.rgb
      GuiDrawUtils.fillRect(poses, markerX - markerSize, markerY - markerSize, markerX + markerSize + 1.0f, markerY + markerSize + 1.0f, markerOutline)
      GuiDrawUtils.fillRect(poses, markerX - markerSize + 1.0f, markerY - markerSize + 1.0f, markerX + markerSize, markerY + markerSize, selectedColor.darker())

      this.updateControls(mouseX, mouseY)
      this.controls.forEach { control -> control.render(poses, mouseX, mouseY) }
      val value = ColorUtils.withAlpha(selectedColor, if (this.colorSetting.opaqueColor) 255 else alpha)
      this.setting.value = value
      if (this.colorSetting.isRainbow) {
         val displayed = this.setting.value
         this.hsb[0] = Color.RGBtoHSB(displayed.red, displayed.green, displayed.blue, null)[0]
      }
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return super.isMouseOver(mouseX, mouseY) || this.draggingHue || this.draggingSaturationBrightness || this.draggingAlpha
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         return
      }
      if (button == 1 && this.isHeaderHovered(mouseX, mouseY)) {
         this.expanded = !this.expanded
      }
      if (!this.expanded) {
         super.mouseClicked(mouseX, mouseY, button)
         return
      }
      this.controls.forEach { control -> control.mouseClicked(mouseX, mouseY, button) }
      if (button == 0) {
         val left = this.getPickerX().toInt() + 5
         val top = this.squareY()
         val size = this.pickerSize()
         if (inside(mouseX, mouseY, left, top, size, size)) {
            this.draggingSaturationBrightness = true
         } else if (inside(mouseX, mouseY, left, this.hueY(), size, SLIDER_HEIGHT)) {
            this.draggingHue = true
         } else if (!this.colorSetting.opaqueColor && inside(mouseX, mouseY, left, this.alphaY(), size, SLIDER_HEIGHT)) {
            this.draggingAlpha = true
         }
      }
      super.mouseClicked(mouseX, mouseY, button)
   }

   fun isHeaderHovered(mouseX: Double, mouseY: Double): Boolean {
      return mouseX > this.panel.x && mouseX < this.panel.x + this.panel.width &&
         mouseY > this.panel.y + this.yOffset &&
         mouseY < this.panel.y + this.yOffset + this.baseHeight
   }

   override fun charTyped(character: Char) {
      if (this.isUnavailable) {
         return
      }
      this.controls.forEach { control -> control.charTyped(character) }
      super.charTyped(character)
   }

   override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         return
      }
      this.controls.forEach { control -> control.mouseReleased(mouseX, mouseY, button) }
      super.mouseReleased(mouseX, mouseY, button)
      this.draggingSaturationBrightness = false
      this.draggingHue = false
      this.draggingAlpha = false
   }

   private fun updateHsbFromColor(color: Color) {
      val components = Color.RGBtoHSB(color.red, color.green, color.blue, null)
      System.arraycopy(components, 0, this.hsb, 0, this.hsb.size)
   }

   private class SyncControl(editor: ColorSettingComponent) : BooleanSettingComponent(
      editor.panel,
      editor,
      BooleanSetting("", (editor.setting as ColorSetting).isSynced)
   ) {
      init {
         (this.setting as BooleanSetting).setChangeCallback {
            (editor.setting as ColorSetting).isSynced = (this.setting as BooleanSetting).value
         }
         this.addFlags(ComponentFlag.PADDING_SHIFT)
      }

      override fun init() {
         val editor = this.parentCloseState as ColorSettingComponent
         (this.setting as BooleanSetting).value = (editor.setting as ColorSetting).isSynced
      }

      override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
         super.render(poseStack, mouseX, mouseY)
         drawCenteredLabel(
            poseStack, "Sync", this.panel, this.yOffset, this.textYOffset, this.hoverAnimationValue,
            this.uiModule.textColorSetting.value
         )
      }
   }

   private class PasteControl(yOffset: Int, editor: ColorSettingComponent) : ColorActionButton(
      yOffset,
      editor,
      "Paste",
      Runnable {
         try {
            val pasted = ColorUtils.parseHexColor(ClientAccess.collectiveMinecraft.keyboardHandler.clipboard.trim())
            editor.updateHsbFromColor(pasted)
            editor.setting.value = pasted
            editor.pasteTimer.reset()
         } catch (failure: RuntimeException) {
            editor.invalidPasteTimer.reset()
         }
      }
   ) {
      override fun getLabelOffsetX(): Float {
         return this.panel.width * 0.75f - FontRenderer.INSTANCE.getWidth(this.getName()) / 2.0f
      }

      override fun getName(): String {
         if (!this.editor.invalidPasteTimer.hasElapsed(1000L)) {
            return "Invalid"
         }
         return if (this.editor.pasteTimer.hasElapsed(1000L)) super.getName() else "Pasted"
      }

      override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
         if (this.editor.isUnavailable) {
            return
         }
         GuiDrawUtils.fillRect(
            poseStack,
            this.panel.x + this.panel.width / 2.0f,
            (this.panel.y + this.yOffset).toFloat(),
            (this.panel.x + this.panel.width).toFloat(),
            (this.panel.y + this.yOffset + this.height).toFloat(),
            this.uiModule.colorSetting.value
         )
         super.render(poseStack, mouseX, mouseY)
      }
   }

   private class CopyControl(yOffset: Int, editor: ColorSettingComponent) : ColorActionButton(
      yOffset,
      editor,
      "Copy",
      Runnable {
         ClientAccess.collectiveMinecraft.keyboardHandler.clipboard = ColorUtils.toHex(editor.getSelectedColor(), true)
         editor.copyTimer.reset()
      }
   ) {
      override fun getLabelOffsetX(): Float {
         return this.panel.width * 0.25f - FontRenderer.INSTANCE.getWidth(this.getName()) / 2.0f
      }

      override fun getName(): String {
         return if (this.editor.copyTimer.hasElapsed(1000L)) super.getName() else "Copied"
      }

      override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
         if (this.editor.isUnavailable) {
            return
         }
         GuiDrawUtils.fillRect(
            poseStack,
            this.panel.x.toFloat(),
            (this.panel.y + this.yOffset).toFloat(),
            this.panel.x + this.panel.width / 2.0f,
            (this.panel.y + this.yOffset + this.height).toFloat(),
            this.uiModule.colorSetting.value
         )
         super.render(poseStack, mouseX, mouseY)
      }
   }

   private class RainbowControl(editor: ColorSettingComponent) : BooleanSettingComponent(
      editor.panel,
      editor,
      BooleanSetting("", (editor.setting as ColorSetting).isRainbow)
   ) {
      init {
         (this.setting as BooleanSetting).setChangeCallback {
            (editor.setting as ColorSetting).isRainbow = (this.setting as BooleanSetting).value
         }
         this.addFlags(ComponentFlag.PADDING_SHIFT)
      }

      override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
         super.render(poseStack, mouseX, mouseY)
         drawCenteredLabel(
            poseStack, "Rainbow", this.panel, this.yOffset, this.textYOffset, this.hoverAnimationValue,
            this.uiModule.textColorSetting.value
         )
      }
   }

   companion object {
      private const val SLIDER_HEIGHT = 6
      private const val CONTROL_GAP = 5

      @JvmStatic
      private fun inside(x: Double, y: Double, left: Int, top: Int, width: Int, height: Int): Boolean {
         return x >= left && x < left + width && y >= top && y < top + height
      }

      @JvmStatic
      private fun drawCenteredLabel(
         poses: PoseStack,
         text: String,
         panel: Panel,
         yOffset: Int,
         textYOffset: Float,
         hoverOffset: Float,
         color: Color
      ) {
         FontRenderer.INSTANCE.drawWithShadow(
            poses,
            text,
            panel.x + panel.width / 2.0f - FontRenderer.INSTANCE.getWidth(text) / 2.0f,
            panel.y + textYOffset - hoverOffset + yOffset,
            color
         )
      }
   }
}
