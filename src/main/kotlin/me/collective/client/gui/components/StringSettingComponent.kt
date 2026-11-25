package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.CursorType
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClientScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import me.collective.client.utils.ElapsedTimer
import net.minecraft.util.Mth
import org.lwjgl.glfw.GLFW
import java.util.regex.Matcher

class StringSettingComponent(
   panel: Panel,
   closeState: CloseState,
   setting: Setting<String>
) : SettingComponent<String>(panel, closeState, setting as StringSetting) {

   private val cursorBlinkTimer: ElapsedTimer = ElapsedTimer()
   private var cursorVisible: Boolean = false
   private var editingText: String? = null
   private var cursorIndex: Int = 0

   private val stringSetting: StringSetting
      get() = this.setting as StringSetting

   fun getCursorMarker(): String {
      if (this.cursorBlinkTimer.hasElapsed(500L)) {
         this.cursorVisible = !this.cursorVisible
         this.cursorBlinkTimer.reset()
      }
      return if (this.cursorVisible) "_" else ""
   }

   override fun keyPressed(key: Int) {
      if (this.isUnavailable || !this.editing) {
         return
      }
      ClientContext.getClickGui().suppressEscapeClose()
      when (key) {
         86 -> {
            if (GLFW.glfwGetKey(ClientAccess.collectiveMinecraft.window.window, 341) == 1) {
               val text = this.editingText ?: ""
               val clipboard = ClientAccess.collectiveMinecraft.keyboardHandler.clipboard
               this.editingText = StringBuilder(text).insert(this.cursorIndex, clipboard).toString()
            }
         }
         256 -> {
            this.editing = false
         }
         257 -> {
            this.editing = false
            this.editingText?.let { this.stringSetting.value = it }
            this.cursorIndex = (this.editingText ?: "").length
         }
         259 -> {
            val text = this.editingText ?: ""
            if (text.isEmpty() || this.cursorIndex == 0) {
               return
            }
            val deletionIndex = this.cursorIndex - 1
            if (this.cursorIndex == text.length) {
               this.editingText = text.substring(0, text.length - 1)
            } else if (deletionIndex in 0..text.length) {
               this.editingText = StringBuilder(text).deleteCharAt(deletionIndex).toString()
            }
            this.cursorIndex--
         }
         262 -> {
            val text = this.editingText ?: ""
            if (this.cursorIndex < text.length) {
               this.cursorIndex++
            }
         }
         263 -> {
            if (this.cursorIndex > 0) {
               this.cursorIndex--
            }
         }
      }
   }

   val text: String
      @JvmName("getTextProp") get() = getText()

   fun getText(): String {
      return if (this.editing) (this.editingText ?: "") else this.stringSetting.value
   }

   fun beginEditing() {
      if (!this.editing) {
         this.editing = true
         this.editingText = this.stringSetting.value
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
      if (this.hovered) {
         ClientScreen.cursor = CursorType.INPUT
      }

      GuiDrawUtils.fillRect(
         poseStack,
         (this.panel.x + this.getHorizontalPadding()).toFloat(),
         this.panel.y + this.yOffset + 0.5f,
         (this.panel.x + this.panel.width - 1).toFloat(),
         this.panel.y + this.yOffset + this.height - 0.5f,
         this.uiModule.colorSetting.value
      )

      val curText = this.editingText
      if (curText != null) {
         this.cursorIndex = Mth.clamp(this.cursorIndex, 0, curText.length)
      }

      if (this.editing) {
         poseStack.pushPose()
         val cursorMarker = this.getCursorMarker()
         val text = this.editingText ?: ""
         val caretWidth = FontRenderer.INSTANCE.getWidth(text.substring(0, this.cursorIndex) + "_")
         val maxVisibleWidth = (this.panel.width - this.getHorizontalPadding() * 2).toFloat()
         if (caretWidth > maxVisibleWidth) {
            poseStack.translate(maxVisibleWidth - caretWidth, 0.0f, 0.0f)
         }

         if (text.isNotEmpty()) {
            FontRenderer.INSTANCE.drawWithShadow(
               poseStack,
               text,
               (this.panel.x + 4).toFloat(),
               this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
               this.uiModule.textColorSetting.value
            )
         }

         if (cursorMarker.isNotBlank()) {
            FontRenderer.INSTANCE.drawWithShadow(
               poseStack,
               cursorMarker,
               this.panel.x + 4 + FontRenderer.INSTANCE.getWidth(text.substring(0, this.cursorIndex)),
               this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
               this.uiModule.textColorSetting.value
            )
         }

         poseStack.popPose()
      } else {
         val labelText = this.getDisplayText()
         this.renderWithTextScroll(
            poseStack,
            labelText,
            Runnable {
               FontRenderer.INSTANCE.drawWithShadow(
                  poseStack,
                  this.getDisplayText(),
                  (this.panel.x + 4).toFloat(),
                  this.panel.y + this.textYOffset - this.hoverAnimationValue + this.yOffset,
                  this.uiModule.textColorSetting.value
               )
            }
         )
      }
   }

   private fun getDisplayText(): String {
      val name = this.stringSetting.name
      val value = this.stringSetting.value
      return "\u0001: \u0001"
         .replaceFirst("\u0001", Matcher.quoteReplacement(name.toString()))
         .replaceFirst("\u0001", Matcher.quoteReplacement(value.toString()))
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         this.editing = false
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && button == 0) {
         this.editing = !this.editing
         if (this.editing) {
            val currentEditing = this.clickGui.editingSetting
            if (currentEditing != null && currentEditing != this) {
               currentEditing.editing = false
            }
            this.clickGui.editingSetting = this
         } else if (this.clickGui.editingSetting == this) {
            this.clickGui.editingSetting = null
         }

         this.editingText = this.stringSetting.value
         val text = this.editingText
         if (text != null && text.isNotEmpty()) {
            this.cursorIndex = text.length
         }
      }
   }

   override fun charTyped(character: Char) {
      if (!this.isUnavailable && this.editing) {
         val text = this.editingText ?: ""
         this.editingText = StringBuilder(text).insert(this.cursorIndex, character).toString()
         this.cursorIndex++
      }
   }
}
