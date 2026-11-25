package me.collective.client.modules.hud

import me.collective.client.ClientContext
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.client.HUDModule
import me.collective.client.render.FontRenderer
import me.collective.client.render.HudWidgetRenderer

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.Collections
import net.minecraft.util.Mth

open class HudTextListRenderer : HudWidgetRenderer {
   private val rows: MutableList<out HudTextRow>

   constructor(widget: HudWidgetModule, row: HudTextRow) : this(widget, Collections.singletonList(row))

   constructor(widget: HudWidgetModule, rows: MutableList<out HudTextRow>) : super(widget) {
      this.rows = rows
   }

   override fun calculateDimensions(): FloatArray {
      var height: Float = 0.0F
      var width: Float = 0.0F
      for (row in this.rows) {
         row.updateVisibility()
         if (row.getVisibility() !=
         0.0F
         ) {
            var easedVisibility: Float = row.getVisibility()
            easedVisibility = Mth.clamp(
               1.0 - Math.pow((1.0F - easedVisibility).toDouble(), 4.0), 0.0, 1.0
            ).toFloat()
            val textWidth: Float = FontRenderer.INSTANCE
               .getWidth(
                  row.getText()
                     .getString()
               )
            height += this.height * easedVisibility + 1
            if (textWidth > width) {
               width = textWidth
            }
         }
      }
      if (!HudWidgetRenderer.renderingHud && width == 0.0F) {
         width = 50.0F
         height = this.height.toFloat()
      }
      width *= hudSettings.getTextScale()
      height *= hudSettings.getTextScale()
      val dimensions = FloatArray(2)
      dimensions[0] = width
      dimensions[1] = height
      return dimensions
   }

   override fun render(poses: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poses, mouseX, mouseY)
      val scale: Float = hudSettings.getTextScale()
      var rowY: Float = this.getContentY(
         this.height * scale
      )
      for (row in this.rows) {
         var easedVisibility: Float = row.getVisibility()
         if (easedVisibility != 0.0F) {
            poses.pushPose()
            easedVisibility = Mth.clamp(
               1.0 - Math.pow((1.0F - easedVisibility).toDouble(), 4.0), 0.0, 1.0
            ).toFloat()
            val rowColor: Color = this.getRowColor(rowY, row)
            val animatedWidth: Float = row.widthAnimation
               .updateAndGet() * scale
            val hiddenWidth: Float = animatedWidth * (1.0F - easedVisibility)
            poses.translate(
               this.getContentX(animatedWidth)
                  - hiddenWidth * this.getHorizontalDirection(),
               rowY,
               0.0F
            )
            poses.scale(scale, scale, 1.0F)
            row.renderRow(poses, 0.0F, 0.0F, rowColor, hudSettings.shouldDrawTextShadow())
            if (easedVisibility != 0.0F) {
            rowY += (this.height * easedVisibility + 1) *
               this.getVerticalDirection() * scale
            }
            poses.popPose()
         }
      }
   }

   open fun getRowColor(rowPosition: Float, row: HudTextRow): Color {
      return this.widget
         .getHudColor(rowPosition)
   }

   fun getRows(): MutableList<out HudTextRow> {
      return this.rows
   }

   companion object {
      private val hudSettings: HUDModule get() = ClientContext.getModule(HUDModule::class.java)
   }
}
