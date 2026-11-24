package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.WorldRenderEvent
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.utils.FloatAnimation
import me.collective.client.render.RenderUtils
import me.collective.client.render.ColorUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.settings.options.SpeedMineColorModeChoice
import me.collective.client.settings.options.SpeedMineRebreakChoice
import me.collective.client.settings.options.SpeedMineRenderModeChoice

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape

class SpeedMineBlockRenderState(val blockPos: BlockPos, initialProgress: Float) : ClientAccess {
   private val fadeAnimation: FloatAnimation = FloatAnimation()
   private var renderBounds: AABB? = null
   private var lastUpdateMillis: Long
   private var progress: Float
   private var previousProgress: Float

   init {
      this.lastUpdateMillis = -1L
      this.progress = initialProgress
      this.previousProgress = initialProgress
      this.fadeAnimation.setValue(1.0F)
   }

   fun render(event: WorldRenderEvent) {
      this.updateFade()
      val instantRebreak: Boolean = speedMineModule!!.rebreakSetting.value == SpeedMineRebreakChoice.INSTANT
      if (this.isEmpty() && !(instantRebreak && speedMineModule!!.airSetting.value) &&
         !speedMineModule!!.fadeSetting.value) return
      val state: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(this.blockPos)
      val shape: VoxelShape = state.getShape(ClientAccess.collectiveMinecraft.level!!, this.blockPos)
      if (!shape.isEmpty()) this.renderBounds = shape.bounds()
      val partialTick: Float = RenderUtils.getPartialTick()
      val currentProgress: Float = this.progress
      val previousProgress: Float = this.previousProgress
      val interpolated: Float = Mth.lerp(partialTick, previousProgress, currentProgress)
      val renderProgress: Float = Mth.clamp(interpolated, 0.0F, 1.0F)
      val opacity: Float = this.fadeAnimation.getValue()
      val colors = this.getColors(renderProgress)
      val bounds: AABB = this.getRenderBounds(shape, renderProgress)
      WorldBoxRenderer.fillBox(event.poseStack!!, bounds, this.withOpacity(colors!![0], opacity))
      val outlinePoses: PoseStack = event.poseStack!!
      val outline: Color = this.withOpacity(colors[1], opacity)
      val lineWidth: Float = speedMineModule!!.lineWidthSetting.value
      WorldBoxRenderer.outlineBox(outlinePoses, bounds, outline, lineWidth)
   }

   fun snapshotProgress() {
      this.previousProgress = this.progress
   }

   fun setProgress(newProgress: Float) {
      val clampedNewProgress: Float = Mth.clamp(newProgress, 0.0F, 1.0F)
      this.lastUpdateMillis = System.currentTimeMillis()
      this.previousProgress = Math.min(this.progress, clampedNewProgress)
      this.progress = clampedNewProgress
   }

   fun isFadedOut(): Boolean {
      return this.fadeAnimation.getValue() == 0.0F
   }

   private fun updateFade() {
      if (System.currentTimeMillis() - this.lastUpdateMillis <= 100L) {
         this.fadeAnimation.setValue(1.0F)
      } else if (!speedMineModule!!.fadeSetting.value) {
         this.fadeAnimation.setValue(0.0F)
      } else {
         this.fadeAnimation.setTarget(0.0F, 500L)
      }
   }

   private fun withOpacity(color: Color, opacity: Float): Color {
      return ColorUtils.withAlpha(
         color, (color.getAlpha() * opacity).toInt().toFloat()
      )
   }

   private fun getColors(renderProgress: Float): Array<Color>? {
      val colorMode: SpeedMineColorModeChoice = speedMineModule!!.colorModeSetting.value
      val moduleForColors: SpeedMineModule? = speedMineModule
      return colorMode.getColors(moduleForColors!!, renderProgress)
   }

   private fun getRenderBounds(shape: VoxelShape, renderProgress: Float): AABB {
      var bounds = AABB(BlockPos.ZERO)
      if (WorldUtils.isAir(this.blockPos)) {
         if (this.renderBounds != null) bounds = this.renderBounds!!
         return bounds.move(this.blockPos)
      }
      if (!shape.isEmpty()) bounds = shape.bounds()
      bounds = bounds.move(this.blockPos)
      val mode: SpeedMineRenderModeChoice = speedMineModule!!.renderModeSetting.value
      return mode.getRenderBounds(bounds, renderProgress)
   }

   private fun isEmpty(): Boolean {
      val state: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(this.blockPos)
      val shape: VoxelShape = state.getShape(ClientAccess.collectiveMinecraft.level!!, this.blockPos)
      return shape.isEmpty() || !WorldUtils.canMineBlock(this.blockPos)
   }

   companion object {
      private val speedMineModule: SpeedMineModule? get() = ClientContext.getModule(SpeedMineModule::class.java)
   }

}
