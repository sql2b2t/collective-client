package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.gui.CloseState
import me.collective.client.gui.GuiElement
import me.collective.client.gui.ScissorStack
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.modules.PermanentModule
import me.collective.client.modules.client.FontsModule
import me.collective.client.modules.client.UIModule
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.FloatAnimation
import me.collective.client.utils.InterpolatedFloat
import me.collective.client.utils.KeyUtils
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import java.awt.Color
import java.util.ArrayList
import java.util.Locale
import java.util.Objects
import java.util.function.Supplier
import kotlin.math.min

class ModuleComponent(
   val module: Module,
   panel: Panel,
   yOffset: Int
) : AbstractComponent(panel, yOffset), CloseState {

   @JvmField
   val children: ArrayList<GuiElement> = ArrayList()
   @JvmField
   val heightAnimation: InterpolatedFloat = InterpolatedFloat(
      Supplier { this.uiModule.animSpeedSetting.value * 2.0f },
      true
   )
   private val enabledAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat(
      Supplier { 2.0f * UIModule.INSTANCE.animSpeedSetting.value },
      true
   )
   private val searchHighlightAnimation: FloatAnimation = FloatAnimation()
   private var searchHighlightCycle: Int = 0
   private var copyShortcutHovered: Boolean = false
   private var requestedOpen: Boolean = false
   @JvmField
   var animatedHeight: Float = 0f

   init {
      var childY = this.yOffset + this.baseHeight

      for (entry in module.settings) {
         val setting = entry as Setting<*>
         val component = this.createSettingComponent(panel, setting)
         if (component != null) {
            component.yOffset = childY
            component.addFlags(ComponentFlag.PADDING_SHIFT)
            this.children.add(component)
         }
         childY += 11
      }

      if (module !is HudWidgetModule && module !is PermanentModule) {
         this.children.add(KeybindComponent(childY, this))
      }

      this.heightAnimation.value = this.calculateTargetHeight().toFloat()
   }

   override var yOffset: Int
      get() = super.yOffset
      set(yOffset) {
         super.yOffset = yOffset
         var childY = this.yOffset + this.baseHeight
         if (this.requestedOpen) {
            for (child in this.children) {
               child.yOffset = childY
               childY += child.height
            }
         }
      }

   override val height: Int
      get() = this.animatedHeight.toInt()

   fun setOpen(open: Boolean) {
      this.requestedOpen = open
   }

   fun startSearchHighlight() {
      this.searchHighlightAnimation.value = 0.0f
      this.searchHighlightAnimation.setTarget(1.0f, 150L)
      this.searchHighlightCycle = 1
   }

   override fun keyPressed(keyCode: Int) {
      this.handleCopyShortcut(keyCode)
      this.children.forEach { child -> child.keyPressed(keyCode) }
   }

   fun toggleOpen() {
      this.setOpen(!this.requestedOpen)
      if (this.requestedOpen && !ClientContext.getClickGui().openModuleComponents.contains(this)) {
         ClientContext.getClickGui().openModuleComponents.add(this)
      }
      this.panel.updateLayout()
   }

   private fun calculateTargetHeight(): Int {
      var height = this.baseHeight
      if (this.requestedOpen) {
         for (child in this.children) {
            height += child.height
         }
      }
      return height
   }

   private fun handleCopyShortcut(keyCode: Int) {
      if (this.copyShortcutHovered && KeyUtils.isControlDown() && keyCode == 67) {
         val codec = ClientContext.configurationManager.modulePresetCodec
         val encodedModule = codec.encode(this.module)
         ClientAccess.collectiveMinecraft.keyboardHandler.clipboard = encodedModule
      }
   }

   override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || this.javaClass != other.javaClass) return false
      val component = other as ModuleComponent
      return this.module == component.module
   }

   override fun hashCode(): Int = Objects.hashCode(this.module)

   override fun init() {
      for (child in this.children) {
         child.init()
      }
   }

   override fun isClosed(): Boolean {
      return !(this.animatedHeight > this.baseHeight + 1) && !this.requestedOpen
   }

   override fun isOpen(): Boolean {
      return this.requestedOpen
   }

   private fun renderButtonBackground(poseStack: PoseStack, enabledProgress: Float) {
      if (this.uiModule.elementGradientSetting.value) {
         var buttonColor: Color = this.uiModule.buttonSetting.value
         buttonColor = ColorUtils.withAlpha(buttonColor, buttonColor.alpha / 255.0f * 0.1f)
         GuiDrawUtils.fillVerticalGradient(
            poseStack,
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.width - 2) * enabledProgress,
            this.baseHeight - 0.5f,
            buttonColor.hashCode(),
            ColorUtils.toArgb(buttonColor, 0)
         )
      } else {
         val right = (this.panel.x + this.panel.width - 1).toFloat()
         GuiDrawUtils.fillRect(
            poseStack,
            min(this.panel.x + 1 + this.panel.width * enabledProgress, right),
            this.panel.y + this.yOffset + 0.5f,
            right,
            this.panel.y + this.yOffset + this.baseHeight - 0.5f,
            this.uiModule.buttonSetting.value
         )
      }
   }

   private fun createSettingComponent(panel: Panel, setting: Setting<*>): AbstractComponent? {
      return when (setting) {
         is RegistrySetSetting<*> -> RegistrySetSettingComponent(panel, this, setting)
         is me.collective.client.settings.BooleanSetting -> BooleanSettingComponent(panel, this, setting)
         is me.collective.client.settings.NumberSetting<*> -> NumericSettingComponent(panel, this, setting)
         is me.collective.client.settings.ColorSetting -> ColorSettingComponent(panel, this, setting)
         is me.collective.client.settings.StringSetting -> StringSettingComponent(panel, this, setting)
         is me.collective.client.settings.EnumSetting<*> -> EnumSettingComponent(panel, this, setting)
         is me.collective.client.settings.KeybindSetting -> KeybindSettingComponent(panel, this, setting)
         is me.collective.client.settings.IdentifierSetting -> SoundSettingComponent(panel, this, setting)
         else -> null
      }
   }

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poseStack, mouseX, mouseY)
      this.enabledAnimation.setTarget(this.module.isToggled)
      if (this.module is UIModule) {
         this.enabledAnimation.setValue(1.0f)
      }

      val gradientEnabled = this.uiModule.elementGradientSetting.value
      val enabledProgress = this.enabledAnimation.updateAndGet()
      val hoverOffset = this.hoverAnimationValue
      val baseHeight = this.baseHeight
      if (this.module.isToggled || enabledProgress > 0.01) {
         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            this.panel.x + (this.panel.width - 1.0f) * enabledProgress,
            this.panel.y + this.yOffset + baseHeight - 0.5f,
            this.uiModule.enabledSetting.value
         )
      }

      if (gradientEnabled) {
         this.renderButtonBackground(poseStack, 1.0f)
      } else if (enabledProgress < 1.0f) {
         this.renderButtonBackground(poseStack, enabledProgress)
      }

      if (this.searchHighlightCycle > 0) {
         val highlightProgress = this.searchHighlightAnimation.value
         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 0.5f,
            (this.panel.x + this.panel.width - 1).toFloat(),
            this.panel.y + this.yOffset + baseHeight - 0.5f,
            ColorUtils.packArgb(1.0f, 0.0f, 0.0f, highlightProgress)
         )
         if (highlightProgress == 1.0f || highlightProgress == 0.0f) {
            this.searchHighlightAnimation.setTarget((highlightProgress + 1.0f) % 2.0f, 150L)
         }

         if (highlightProgress == 0.0f) {
            this.searchHighlightCycle = (this.searchHighlightCycle + 1) % 4
         }
      }

      FontRenderer.INSTANCE.drawWithShadow(
         poseStack,
         this.module.name,
         (this.panel.x + 3).toFloat(),
         this.panel.y + this.textYOffset - hoverOffset + this.yOffset,
         if (this.module.isToggled) this.uiModule.enabledColorSetting.value else this.uiModule.textColorSetting.value
      )

      if (this.uiModule.gearSetting.value && this.children.isNotEmpty()) {
         val expandIndicator = if (this.requestedOpen) "-" else "+"
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            expandIndicator,
            this.panel.x + this.panel.width - 4 - FontRenderer.INSTANCE.getWidth(expandIndicator),
            this.panel.y + this.textYOffset + this.yOffset,
            this.uiModule.textColorSetting.value
         )
      }

      val statusText: MutableComponent = Component.empty()
      if (this.module.isWip) {
         statusText.append("beta ")
      }

      if (this.uiModule.bindsSetting.value && this.module.keybind.code() != -1) {
         val bindingName = this.module.keybind.displayName
         statusText.append("[").append(bindingName.uppercase(Locale.ROOT)).append("]")
      }

      if (statusText.siblings.isNotEmpty()) {
         val fontsModule = FontsModule.INSTANCE
         val statusScale = if (fontsModule?.isToggled == true) 0.65f else 0.5f
         val statusYOffset = if (fontsModule?.isToggled == true) 0.0f else 1.0f
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            statusText,
            this.panel.x + FontRenderer.INSTANCE.getWidth(this.module.name) + 3.0f,
            this.panel.y + this.textYOffset - hoverOffset + this.yOffset + statusYOffset,
            statusScale,
            this.uiModule.textColorSetting.value
         )
      }

      poseStack.pushPose()
      if (this.animatedHeight > baseHeight || this.requestedOpen) {
         GuiDrawUtils.flush()
         ScissorStack.pushIntersection(
            this.panel.x,
            this.panel.y + this.yOffset + baseHeight,
            this.panel.width - 1,
            this.animatedHeight.toInt() - baseHeight
         )

         for (child in this.children) {
            child.render(poseStack, mouseX, mouseY)
         }

         GuiDrawUtils.flush()
         ScissorStack.pop()
      }

      poseStack.popPose()
      if (this.uiModule.descriptionsSetting.value && this.isMouseOver(mouseX, mouseY)) {
         ClientContext.getClickGui().setTooltipText(this.module.description)
      }
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      super.mouseClicked(mouseX, mouseY, button)
      if (this.isMouseOver(mouseX, mouseY) && ClientContext.getClickGui().searchBox?.value?.isNotEmpty() == true) {
         this.startSearchHighlight()
      }

      if (this.isMouseOver(mouseX, mouseY) && button == 0 && this.isLeftClickEligible()) {
         this.module.toggle()
      }

      if (this.isMouseOver(mouseX, mouseY) && button == 1) {
         this.toggleOpen()
      }

      this.children.forEach { child -> child.mouseClicked(mouseX, mouseY, button) }
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return mouseX > this.panel.x
         && mouseX < this.panel.x + this.panel.width
         && mouseY > this.clampYToPanel((this.panel.y + this.yOffset).toFloat())
         && mouseY < this.clampYToPanel((this.panel.y + this.baseHeight + this.yOffset).toFloat())
   }

   override fun update(mouseX: Double, mouseY: Double) {
      if (FontRenderer.INSTANCE.isFontLoading) {
         this.heightAnimation.value = this.calculateTargetHeight().toFloat()
      } else {
         this.heightAnimation.setTarget(this.calculateTargetHeight().toFloat())
      }

      this.animatedHeight = this.heightAnimation.updateAndGet()
      this.copyShortcutHovered = super.isMouseOver(mouseX, mouseY)
      if (this.requestedOpen) {
         this.children.forEach { child -> child.update(mouseX, mouseY) }
      }
   }

   override fun charTyped(character: Char) {
      this.children.forEach { child -> child.charTyped(character) }
   }

   override fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double) {
      for (child in this.children) {
         child.mouseScrolled(mouseX, mouseY, amount)
      }
   }

   override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) {
      this.children.forEach { child -> child.mouseReleased(mouseX, mouseY, button) }
   }
}

fun ModuleComponent.getModule(): Module = this.module
