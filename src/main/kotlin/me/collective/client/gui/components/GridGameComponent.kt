package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.panels.Panel
import me.collective.client.render.GuiDrawUtils
import me.collective.client.utils.ElapsedTimer
import java.awt.Color

abstract class GridGameComponent @JvmOverloads constructor(
   panel: Panel,
   yOffset: Int,
   protected val gridColumns: Int = 22,
   protected val gridRows: Int = 22
) : AbstractComponent(panel, yOffset) {

   private val tickTimer = ElapsedTimer()

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poseStack, mouseX, mouseY)
      if (this.tickTimer.hasElapsed(this.getTickDelayMillis().toLong())) {
         this.tickGame()
         this.tickTimer.reset()
      }
   }

   protected abstract fun tickGame()

   protected abstract fun getTickDelayMillis(): Int

   fun drawCell(poseStack: PoseStack, column: Int, row: Int, color: Color) {
      GuiDrawUtils.fillRect(
         poseStack,
         (this.panel.x + column * this.getCellWidth()).toFloat(),
         (this.panel.y + this.yOffset + row * this.getCellHeight()).toFloat(),
         (this.panel.x + column * this.getCellWidth() + this.getCellWidth()).toFloat(),
         (this.panel.y + this.yOffset + row * this.getCellHeight() + this.getCellHeight()).toFloat(),
         color
      )
   }

   fun getCellWidth(): Int {
      return this.panel.width / this.gridColumns
   }

   fun getCellHeight(): Int {
      return this.height / this.gridRows
   }
}

val GridGameComponent.cellWidth: Int get() = getCellWidth()
val GridGameComponent.cellHeight: Int get() = getCellHeight()
