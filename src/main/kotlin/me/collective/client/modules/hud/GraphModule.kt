package me.collective.client.modules.hud

import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.MeshBuffer
import me.collective.client.render.MeshDrawContext
import me.collective.client.render.VertexGeometry
import me.collective.client.render.ColorUtils
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.ResizableHudWidgetRenderer
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import java.util.ArrayList
import net.minecraft.util.Mth

class GraphModule : HudWidgetModule("Graph") {
   private val ceilSetting: Setting<Float>
   private val heightSetting: Setting<Int>
   private val widthSetting: Setting<Int>
   private val speedSamples: MutableList<Double> = ArrayList()

   override fun onEnable() {
      this.speedSamples.clear()
      for (sampleIndex in 0 until 150) {
         this.speedSamples.add(0.0)
      }
   }

   override fun calculateDimensions(): FloatArray {
      val dimensions = FloatArray(2)
      val width = this.widthSetting.getValue()
      dimensions[0] = width!!.toInt().toFloat()
      val height = this.heightSetting.getValue()
      dimensions[1] = height!!.toInt().toFloat()
      return dimensions
   }

   override fun renderContent(poses: PoseStack) {
      if (this.speedSamples.size < 2) {
         return
      }
      val buffer = MeshDrawContext.getBuffer()
      buffer.begin(Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
      for (sampleIndex in 1 until this.speedSamples.size) {
         val previousY = this.getSampleY(this.speedSamples[sampleIndex - 1])
         val currentY = this.getSampleY(this.speedSamples[sampleIndex])
         VertexGeometry.appendLine(
            buffer, poses.last(), (sampleIndex - 1).toFloat(), previousY, 0.0f, sampleIndex.toFloat(), currentY, 0.0f,
            1.0f, this.getSampleColor(previousY, sampleIndex - 1), this.getSampleColor(currentY, sampleIndex)
         )
      }
      buffer.draw(ClientRenderTypes.worldLines())
   }

   private fun getSampleY(sample: Double): Float {
      val height = this.heightSetting.getValue()!!
      val clippedSample = Math.min(sample, this.ceilSetting.getValue()!!.toDouble())
      return 1.0f + ((height - 2) * (1.0 - clippedSample / this.ceilSetting.getValue()!!)).toFloat()
   }

   private fun getSampleColor(sampleY: Float, sampleIndex: Int): Int {
      val baseColor = this.getHudColor(sampleY + this.renderer.y)
      return ColorUtils.withAlpha(baseColor, this.getSampleAlpha(sampleIndex).toFloat()).hashCode()
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(ignoredEvent: LocalPlayerTickEvent) {
      this.speedSamples.add(ClientContext.movementSpeedTracker.displaySpeed)

      while (this.speedSamples.size > this.widthSetting.getValue()!!) {
         this.speedSamples.removeAt(0)
      }
   }

   private fun getSampleAlpha(sampleIndex: Int): Int {
      if (sampleIndex <= 10) {
         var leadingAlpha = sampleIndex.toFloat()

         leadingAlpha /= 10.0f

         leadingAlpha *= 255.0f

         val leadingMaximum = 255.0f
         return Mth.clamp(leadingAlpha, 0.0f, leadingMaximum).toInt()
      } else {
         val widthForThreshold = this.widthSetting.getValue()!!

         if (sampleIndex >= widthForThreshold - 10) {
            val widthForRamp = this.widthSetting.getValue()!!
            val relativePosition = sampleIndex - widthForRamp

            val trailingPosition = (relativePosition +
         10).toFloat()

            val trailingProgress = 1.0f - trailingPosition / 10.0f

            var trailingAlpha = trailingProgress * 255.0f

            val trailingMaximum = 255.0f
            return Mth.clamp(trailingAlpha, 0.0f, trailingMaximum).toInt()
         } else {
            return 255
         }
      }
   }

   init {
      this.ceilSetting = this.add(NumberSetting<Float>(
         "Ceil",
         26.0f,
         10.0f,
         100.0f
      ))
      this.heightSetting = this.add(NumberSetting<Int>(
         "Height",
         25,
         10,
         30
      ))
      this.widthSetting = this.add(NumberSetting<Int>(
         "Width",
         75,
         50,
         100
      ))
      this.setRenderer(ResizableHudWidgetRenderer(this))
   }
}
