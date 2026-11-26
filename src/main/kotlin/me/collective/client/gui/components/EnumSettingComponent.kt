package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.config.EnumJsonConverter
import me.collective.client.gui.CloseState
import me.collective.client.gui.panels.Panel
import me.collective.client.modules.client.UIModule
import me.collective.client.render.FontRenderer
import me.collective.client.render.GuiDrawUtils
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.ClampedInterpolatedFloat
import me.collective.client.utils.ExperimentalFeatures
import me.collective.client.utils.InterpolatedFloat
import java.util.regex.Matcher
import kotlin.math.max

class EnumSettingComponent<E>(
   panel: Panel,
   parentCloseState: CloseState,
   setting: EnumSetting<E>
) : SettingComponent<E>(panel, parentCloseState, setting) where E : Enum<E>, E : NamedEntry {

   private val optionRows: ArrayList<EnumOptionRow<E>> = ArrayList()
   private val selectionOffsetAnimation: InterpolatedFloat = InterpolatedFloat(
      { 2.0f * this.uiModule.animSpeedSetting.value },
      true
   )
   private val fillAnimation: ClampedInterpolatedFloat = ClampedInterpolatedFloat(
      { 2.0f * UIModule.INSTANCE.animSpeedSetting.value },
      true
   )
   var isExpanded: Boolean = false
      private set
   private var componentYOffset: Int = 0
   private var cachedX: Int = 0
   private var cachedY: Int = 0
   var selectedIndex: Int = 0
   private val selectableValueCount: Int

   init {
      var rowYOffset = this.baseHeight
      var selectableIndex = 0

      for (candidate in this.setting.defaultValue.declaringJavaClass.enumConstants) {
         if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
            this.optionRows.add(
               EnumOptionRow(
                  this,
                  EnumJsonConverter.getName(candidate),
                  rowYOffset,
                  selectableIndex
               )
            )
            rowYOffset += this.baseHeight
            selectableIndex++
         }
      }

      this.selectableValueCount = selectableIndex
   }

   @Suppress("UNCHECKED_CAST")
   private fun nextValue(): E {
      val value = this.setting.value
      return value.declaringJavaClass.cast(EnumJsonConverter.nextValue(value)) as E
   }

   override var yOffset: Int
      get() = this.componentYOffset
      set(value) {
         this.componentYOffset = value
      }

   override val height: Int
      get() {
         if (this.isUnavailable) {
            val selectionOffset = -this.baseHeight.toFloat()
            this.selectionOffsetAnimation.setValue(selectionOffset)
            return 0
         } else {
            return if (this.isExpanded) {
               this.baseHeight + this.selectableValueCount * this.baseHeight + 1
            } else {
               super.height
            }
         }
      }

   override fun init() {
      this.selectedIndex = this.setting.value.ordinal
   }

   val y: Int get() = this.cachedY
   val x: Int get() = this.cachedX

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      if (this.isUnavailable) {
         this.fillAnimation.reset()
         return
      }

      super.render(poseStack, mouseX, mouseY)
      this.fillAnimation.setTarget(true)
      if (this.isExpanded) {
         this.selectionOffsetAnimation.setTarget((this.selectedIndex * this.baseHeight).toFloat())
         val selectionOffset = this.selectionOffsetAnimation.updateAndGet()
         val firstRowY = this.panel.y + this.componentYOffset + this.baseHeight - 0.5f
         GuiDrawUtils.fillRect(
            poseStack,
            (this.panel.x + this.getHorizontalPadding() + 1).toFloat(),
            max(selectionOffset + firstRowY, firstRowY),
            (this.panel.x + this.panel.width - 2).toFloat(),
            this.panel.y + this.componentYOffset + this.baseHeight * 2 + selectionOffset - 0.5f,
            this.uiModule.colorSetting.value
         )
      } else {
         this.selectionOffsetAnimation.setTarget(-this.baseHeight.toFloat())
      }

      val edgeInset = if (this.isExpanded) 1 else 0
      val fillWidth = max(this.panel.width * this.fillAnimation.updateAndGet() - 1.0f, 2.0f)
      GuiDrawUtils.fillRect(
         poseStack,
         (this.panel.x + this.getHorizontalPadding() + edgeInset).toFloat(),
         this.panel.y + this.componentYOffset + 0.5f + edgeInset,
         this.panel.x + fillWidth - edgeInset,
         this.panel.y + this.componentYOffset + this.baseHeight - 0.5f,
         this.uiModule.colorSetting.value
      )

      if (this.isExpanded) {
         this.optionRows.forEach { row -> row.render(poseStack, mouseX, mouseY) }
      }

      val settingName = this.setting.name
      val valueName = EnumJsonConverter.getName(this.setting.value)
      val label = "$settingName: \u0001".replaceFirst("\u0001", Matcher.quoteReplacement(valueName.toString()))

      this.renderWithTextScroll(
         poseStack,
         label,
         Runnable {
            FontRenderer.INSTANCE.drawWithShadow(
               poseStack,
               label,
               (this.panel.x + 4).toFloat(),
               this.panel.y + this.textYOffset - this.hoverAnimationValue + this.componentYOffset,
               this.uiModule.textColorSetting.value
            )
         }
      )

      var rowYOffset = this.baseHeight
      for (row in this.optionRows) {
         row.yOffset = rowYOffset
         rowYOffset += this.baseHeight
      }
   }

   override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
      return mouseX > this.cachedX &&
         mouseX < this.cachedX + this.panel.width &&
         mouseY > this.clampYToPanel(this.cachedY.toFloat()) &&
         mouseY < this.clampYToPanel((this.cachedY + 11).toFloat())
   }

   override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
      if (this.isUnavailable) {
         return
      }

      super.mouseClicked(mouseX, mouseY, button)
      this.optionRows.forEach { row -> row.mouseClicked(mouseX, mouseY, button) }
      if (this.isMouseOver(mouseX, mouseY)) {
         if (button == 0) {
            this.setting.value = this.nextValue()
            this.selectedIndex = (this.selectedIndex + 1) % this.selectableValueCount
         }

         if (button == 1) {
            var selectableIndex = 0
            val enumConstants = this.setting.value.declaringJavaClass.enumConstants
            for (enumIndex in enumConstants.indices) {
               val candidate = enumConstants[enumIndex]
               if (candidate == this.setting.value) {
                  this.selectedIndex = selectableIndex
                  break
               }
               if (!ExperimentalFeatures.isEnumConstantHidden(candidate)) {
                  selectableIndex++
               }
            }

            this.isExpanded = !this.isExpanded
            this.panel.updateLayout()
         }
      }
   }

   override fun update(mouseX: Double, mouseY: Double) {
      if (this.isUnavailable) {
         return
      }

      this.cachedY = this.panel.y + this.componentYOffset
      this.cachedX = this.panel.x
   }
}

fun EnumSettingComponent<*>.getY(): Int = this.y
fun EnumSettingComponent<*>.getX(): Int = this.x
fun EnumSettingComponent<*>.setSelectedIndex(index: Int) { this.selectedIndex = index }
