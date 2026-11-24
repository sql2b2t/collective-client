package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.CursorType
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.NumberSetting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.InterpolatedFloat
import net.minecraft.util.Mth
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.regex.Matcher
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class NumericSettingComponent<N : Number>(
   panel: Panel,
   parentCloseState: CloseState,
   setting: NumberSetting<N>
) : SettingComponent<N>(panel, parentCloseState, setting) {

   private val numberSetting: NumberSetting<N> = setting
   private val sliderFillAnimation: InterpolatedFloat = InterpolatedFloat(6.0f, false)
   private val cursorBlinkTimer: ElapsedTimer = ElapsedTimer()
   private var sliderX: Int = 0
   private var sliderY: Int = 0
   private var dragging: Boolean = false
   private var cursorVisible: Boolean = false
   private var sliderFillWidth: Float = 0.0f
   private var inputText: String = ""

   private fun getBlinkingCursor(): String {
      if (this.cursorBlinkTimer.hasElapsed(500L)) {
         this.cursorVisible = !this.cursorVisible
         this.cursorBlinkTimer.reset()
      }
      return if (this.cursorVisible && this.editing) "_" else ""
   }

   override fun keyPressed(keyCode: Int) {
      if (this.isUnavailable || !this.editing || !this.parentCloseState.isOpen()) {
         return
      }

      when (keyCode) {
         256 -> {
            this.editing = false
            ClientContext.getClickGui().suppressEscapeClose()
         }
         257, 335 -> {
            this.commitEditedValue()
         }
         259 -> {
            if (this.inputText.isNotEmpty()) {
               this.inputText = this.inputText.substring(0, this.inputText.length - 1)
            }
         }
      }
   }

   private fun commitEditedValue() {
      this.editing = false
      val inputBlank = this.inputText.isBlank()

      try {
         val valueTypeName = this.setting.value.javaClass.simpleName
         when (valueTypeName) {
            "Integer" -> {
               this.numberSetting.setNumber(
                  if (inputBlank) this.setting.value else this.inputText.toDouble().roundToLong().toInt()
               )
            }
            "Float" -> {
               val floatResult: Number = if (inputBlank) {
                  this.setting.value
               } else {
                  (this.inputText + "f").toFloat()
               }
               this.numberSetting.setNumber(floatResult)
            }
            "Double" -> {
               val doubleResult: Number = if (inputBlank) {
                  this.setting.value
               } else {
                  (this.inputText + "d").toDouble()
               }
               this.numberSetting.setNumber(doubleResult)
            }
         }
      } catch (ignoredException: Exception) {
      }
   }

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.isUnavailable) {
         if (this.editing && this.clickGui.editingSetting == this) {
            this.clickGui.editingSetting = null
         }
         this.editing = false
         return
      }

      super.render(poseStack, mouseX, mouseY)
      if (this.hovered && this.editing) {
         ClientScreen.cursor = CursorType.INPUT
      }

      GuiDrawUtils.fillRect(
         poseStack,
         (this.sliderX + this.getHorizontalPadding()).toFloat(),
         this.sliderY + 0.5f,
         (this.sliderX + Mth.clamp(this.sliderFillAnimation.updateAndGet(), 0.0f, (this.panel.width - 3).toFloat()) + 2.0f),
         this.sliderY + this.height - 0.5f,
         this.uiModule.colorSetting.value
      )

      val unitText = this.numberSetting.unit
      var valueText = (NUMBER_FORMAT.format(this.setting.value.toDouble()) + "\u0001")
         .replaceFirst("\u0001", Matcher.quoteReplacement(unitText.toString()))
      if (this.setting.shouldDisplayBoundaryLabel()) {
         valueText = this.setting.boundaryLabel
      }

      val displayText: String = if (this.editing) {
         this.inputText
      } else {
         String.format("%s: %s", this.setting.name, valueText)
      }

      val editingCursorText = if (this.editing) "_" else ""

      this.renderWithTextScroll(
         poseStack,
         (displayText + "\u0001").replaceFirst("\u0001", editingCursorText),
         Runnable {
            FontRenderer.INSTANCE.drawWithShadow(
               poseStack,
               (displayText + "\u0001").replaceFirst("\u0001", this.getBlinkingCursor()),
               (this.panel.x + 4).toFloat(),
               this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
               this.uiModule.textColorSetting.value
            )
         }
      )
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         this.editing = false
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && button == 0 && this.parentCloseState.isOpen()) {
         if (this.editing) {
            this.commitEditedValue()
         } else {
            this.dragging = true
         }
      }

      if (this.isMouseOver(mouseX, mouseY) && button == 1 && this.parentCloseState.isOpen()) {
         this.editing = !this.editing
         if (this.editing) {
            val currentEditing = this.clickGui.editingSetting
            if (currentEditing != null && currentEditing != this) {
               currentEditing.editing = false
            }
            this.clickGui.editingSetting = this
            this.inputText = this.setting.value.toString()
         } else if (this.clickGui.editingSetting == this) {
            this.commitEditedValue()
            this.clickGui.editingSetting = null
         }
      }
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return super.isMouseOver(mouseX, mouseY) || this.dragging
   }

   override fun update(mouseX: Double, mouseY: Double) {
      if (this.isUnavailable) {
         this.sliderFillAnimation.setValue(0.0f)
      }

      this.sliderY = this.panel.y + this.yOffset
      this.sliderX = this.panel.x
      val sliderWidth = (this.panel.width - 2).toDouble()
      val mouseOffset = min(sliderWidth, max(0.0, mouseX - this.sliderX))
      val minVal = this.setting.minimumValue?.toDouble() ?: 0.0
      val maxVal = this.setting.maximumValue?.toDouble() ?: 1.0
      val isInteger = this.setting.value.javaClass.simpleName.equals("Integer", ignoreCase = true)
      val roundingStep = if (isInteger) 1.0 else 0.1

      val currentNum = if (roundingStep == 1.0) this.setting.value.toInt().toDouble() else this.setting.value.toFloat().toDouble()
      this.sliderFillWidth = (sliderWidth * (currentNum - minVal) / (maxVal - minVal)).toFloat()

      if (this.dragging) {
         if (mouseOffset == 0.0) {
            if (roundingStep == 1.0) {
               this.numberSetting.setNumber((this.setting.minimumValue?.toFloat() ?: 0.0f).toInt())
            } else {
               this.numberSetting.setNumber(this.setting.minimumValue?.toFloat() ?: 0.0f)
            }
         } else {
            val mappedValue = (mouseOffset / sliderWidth * (maxVal - minVal) + minVal).toFloat()
            val roundingFactor = (1.0 / roundingStep).toFloat()
            val clampedValue = max(minVal, min(maxVal, mappedValue.toDouble()))
            if (roundingStep == 0.1) {
               this.numberSetting.setNumber(
                  roundSliderValue(((clampedValue * roundingFactor).roundToLong() / roundingFactor.toDouble()))
               )
            } else {
               this.numberSetting.setNumber(roundSliderValue(clampedValue))
            }
         }
      }

      if (this.setting.maximumValue != null && this.setting.value.toFloat() >= this.setting.maximumValue!!.toFloat()) {
         this.sliderFillWidth = (this.panel.width - 2).toFloat()
      }

      this.sliderFillAnimation.setTarget(this.sliderFillWidth)
   }

   override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
      this.dragging = false
   }

   override fun charTyped(character: Char) {
      var characterCode = character.code
      if (this.isUnavailable) {
         return
      }

      val allowedCharacters = "1234567890.,-"
      if (characterCode == 44) {
         characterCode = 46
      }

      if (!this.editing || !this.parentCloseState.isOpen() || !FontRenderer.containsCharacter(allowedCharacters, characterCode)) {
         return
      }

      if (characterCode == 46 && FontRenderer.containsCharacter(this.inputText, characterCode)) {
         return
      }

      val previousInputText = this.inputText
      this.inputText = (previousInputText + "\u0001").replaceFirst("\u0001", characterCode.toChar().toString())
   }

   companion object {
      private val NUMBER_FORMAT: NumberFormat = NumberFormat.getNumberInstance().apply {
         minimumFractionDigits = 1
         maximumFractionDigits = 4
         isGroupingUsed = false
      }

      @JvmStatic
      private fun roundSliderValue(value: Double): Double {
         return BigDecimal(value).setScale(4, RoundingMode.HALF_UP).toDouble()
      }
   }
}
