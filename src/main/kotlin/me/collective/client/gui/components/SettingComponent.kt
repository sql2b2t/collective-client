package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.ScissorStack
import me.collective.client.gui.panels.Panel
import me.collective.client.gui.screens.ClickGuiScreen
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.Setting
import me.collective.client.utils.FloatAnimation

abstract class SettingComponent<T> : AbstractComponent {
   val parentCloseState: CloseState
   @JvmField
   val setting: Setting<T>
   protected val textScrollAnimation = FloatAnimation()
   private var wasHoveredForTextScroll = false
   private var textScrollHoverStartedAtMillis = 0L
   @JvmField
   var editing: Boolean = false

   constructor(panel: Panel, setting: Setting<T>) : this(panel, CloseState { false }, setting)

   constructor(panel: Panel, parentCloseState: CloseState, setting: Setting<T>) : super(panel, 0) {
      this.parentCloseState = parentCloseState
      this.setting = setting
   }

   override val height: Int
      get() = if (!this.setting.isVisible) 0 else this.baseHeight

   fun getSetting(): Setting<T> = this.setting

   val isUnavailable: Boolean
      get() {
         val unavailable = !this.setting.isVisible || this.parentCloseState.isClosed()
         if (unavailable) {
            this.setting.expanded = false
         }
         return unavailable
      }

   protected val clickGui: ClickGuiScreen
      @JvmName("getClickGuiProp") get() = ClientContext.getClickGui()

   protected fun getClickGui(): ClickGuiScreen = clickGui

   private fun updateTextScroll(textWidth: Float) {
      if (textWidth <= this.panel.width - 4) {
         this.textScrollAnimation.value = 0.0f
      } else {
         if (this.hovered) {
            if (!this.wasHoveredForTextScroll) {
               this.textScrollHoverStartedAtMillis = System.currentTimeMillis()
            }

            if (System.currentTimeMillis() > this.textScrollHoverStartedAtMillis + 750L) {
               this.textScrollAnimation.setTarget(
                  textWidth - (this.panel.width - 4),
                  500L
               )
            }
         } else {
            this.textScrollAnimation.setTarget(0.0f, 500L)
         }

         this.wasHoveredForTextScroll = this.hovered
      }
   }

   protected fun renderWithTextScroll(
      poseStack: PoseStack, text: String, renderer: Runnable
   ) {
      poseStack.pushPose()
      this.updateTextScroll(FontRenderer.INSTANCE.getWidth(text))
      val scrollOffset = -this.textScrollAnimation.value
      val scissorRequired = scrollOffset != 0.0f && this !is EnumSettingComponent<*>
      if (scissorRequired) {
         val parentModule = this.parentCloseState as ModuleComponent
         GuiDrawUtils.flush()
         ScissorStack.pushIntersection(
            this.panel.x + this.getHorizontalPadding(),
            this.panel.y + parentModule.yOffset,
            this.panel.width - this.getHorizontalPadding(),
            parentModule.height
         )
      }

      poseStack.translate(scrollOffset, 0.0f, 0.0f)
      renderer.run()
      if (scissorRequired) {
         GuiDrawUtils.flush()
         ScissorStack.pop()
      }

      poseStack.popPose()
   }

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.uiModule.descriptionsSetting.value
         && this.uiModule.settingsSetting.value
         && this.isMouseOver(mouseX, mouseY)
         && this.parentCloseState is ModuleComponent) {
         val parentModule = this.parentCloseState
         val moduleConfigName = parentModule.module.configName
         val settingConfigName = this.setting.configName
         val translations = ClientContext.languageTable
         val description = translations.getTranslation(
            "descriptions.settings.%s.%s".format(moduleConfigName, settingConfigName)
         )
         if (description != null) {
            ClientContext.getClickGui().setTooltipText(description)
         }
      }

      super.render(poseStack, mouseX, mouseY)
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (!this.isUnavailable) {
         super.mouseClicked(mouseX, mouseY, button)
      }
   }
}

fun SettingComponent<*>.getParentCloseState(): CloseState = this.parentCloseState
fun SettingComponent<*>.isUnavailable(): Boolean = this.isUnavailable
