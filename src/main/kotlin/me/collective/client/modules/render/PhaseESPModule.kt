package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.render.ColorUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import com.mojang.blaze3d.vertex.PoseStack
import java.awt.Color
import java.util.HashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class PhaseESPModule : Module("PhaseESP", "Highlights safe blocks to phase into.", ModuleCategory.RENDER) {
   private val crawlingSetting: Setting<Boolean>
   private val inBlocksSetting: Setting<Boolean>
   private val insideSetting: Setting<Boolean>
   private val safeSetting: Setting<Color>
   private val semiSafeSetting: Setting<Color>
   private val unsafeSetting: Setting<Color>
   private val outlineSetting: Setting<Boolean>
   private val alphaSetting: Setting<Float>
   private val blockAnimations: HashMap<BlockPos, BlockAnimation>

   private fun isNotBedrock(position: BlockPos): Boolean {

      return BlockInteractionUtils.getBlock(position) !==
         Blocks.BEDROCK
   }

   private fun shouldHighlightPosition(position: BlockPos): Boolean {
      val allowInBlocks = this.inBlocksSetting.getValue()
      if (allowInBlocks) {
         val allowInside = this.insideSetting.getValue()
         if (allowInside) {
            return true
         }
      }

      var blockBounds = AABB(position)
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val shapeState: BlockState = level.getBlockState(position)
      val shapeLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val collisionShape: VoxelShape = shapeState.getCollisionShape(shapeLevel, position)
      if (!collisionShape.isEmpty) {
         val localBounds = collisionShape.bounds()
         blockBounds = localBounds.move(position)
      }

      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val playerBounds = player.boundingBox

      val insetBounds = blockBounds.inflate(-0.05)
      return !playerBounds.intersects(insetBounds)
   }

   private fun isPhaseCandidate(position: BlockPos): Boolean {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val state: BlockState = level.getBlockState(position)
      val block = state.block
      val explosionResistance = block.explosionResistance

      if (explosionResistance >= 600.0f) {

         if (BlockInteractionUtils.isCollidable(position)) {
            if (!state.canBeReplaced()) {
               return true
            }
         }
      }

      return false
   }

   private fun classifySafetyForRender(position: BlockPos): Safety {
      return this.classifySafety(position)
   }

   private fun classifySafety(position: BlockPos): Safety {

      val block = WorldUtils.getBlock(position)
      var explosionResistance = block.explosionResistance

      val resistantBlock = explosionResistance >= 600.0f
      val collisionFloorPosition = position.below()

      if (BlockInteractionUtils.isCollidable(collisionFloorPosition) && resistantBlock) {
         val resistanceFloorPosition = position.below()

         val floorBlock = WorldUtils.getBlock(resistanceFloorPosition)
         explosionResistance = floorBlock.explosionResistance

         if (!(explosionResistance < 600.0f)) {

            if (!this.isNotBedrock(position)) {
               val bedrockFloorPosition = position.below()

               if (!this.isNotBedrock(bedrockFloorPosition)) {
                  return Safety.SAFE
               }
            }

            return Safety.SEMI
         }
      }

      return Safety.UNSAFE
   }

   private fun refreshPosition(position: BlockPos) {
      val animations = this.blockAnimations
      val animation = animations.getOrDefault(position, BlockAnimation.create())
      val refreshedAnimations = this.blockAnimations
      refreshedAnimations[position] = animation.refresh()
   }

   private fun getBlockBounds(position: BlockPos): AABB {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val shapeState: BlockState = level.getBlockState(position)
      val shapeLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      var shape: VoxelShape = shapeState.getShape(shapeLevel, position)
      if (shape.isEmpty) {
         shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
      }

      val localBounds = shape.bounds()
      return localBounds.move(position)
   }

   private fun removeExpiredPositions() {
      this.blockAnimations.entries.removeIf { entry ->
         val nowMillis = System.currentTimeMillis()
         val animation = entry.value
         nowMillis > animation.expiresAtMillis
      }
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      this.removeExpiredPositions()
      val playerMinY = ClientAccess.collectiveMinecraft.player!!.boundingBox.minY
      val roundedPlayerY = Math.round(playerMinY).toFloat()
      val playerBounds = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val minimumYBounds = playerBounds.setMinY(roundedPlayerY.toDouble())
      val flatBounds = minimumYBounds.setMaxY(roundedPlayerY.toDouble())
      val searchBounds = flatBounds.inflate(0.01, 0.0, 0.01)

      val crawlingBlocked = PlayerStateUtils.isLocalHeightBelowOne() &&
         !this.crawlingSetting.getValue()
      val inBlockBlocked = isPlayerInCollidableBlock() &&
         !this.inBlocksSetting.getValue()
      if (!inBlockBlocked && !crawlingBlocked) {
         BlockPos.betweenClosedStream(searchBounds)
            .map { it.immutable() }
            .filter { this.isPhaseCandidate(it) }
            .filter { this.shouldHighlightPosition(it) }
            .forEach { this.refreshPosition(it) }
      }

      for (entry in this.blockAnimations.entries) {
         val position = entry.key
         val animation = entry.value

         val safety = classifySafetyForRender(position)
         val fillColor = safety.getFadedColor(this, animation)

         val blockBounds = this.getBlockBounds(position)
         val fillPoses: PoseStack = event.poseStack!!
         val fillMaxY = position.y.toDouble()
         WorldBoxRenderer.fillBox(
            fillPoses, blockBounds.setMaxY(fillMaxY), fillColor
         )
         val drawOutline = this.outlineSetting.getValue()
         if (drawOutline) {
            val animationAlpha = animation.getAlpha()
            val configuredAlpha = this.alphaSetting.getValue()
            val outlineAlpha = animationAlpha * configuredAlpha
            val outlinePoses: PoseStack = event.poseStack!!
            val outlineMaxY = position.y.toDouble()
            val outlineBounds = blockBounds.setMaxY(outlineMaxY)
            val outlineColor = ColorUtils.withAlpha(fillColor, outlineAlpha)

            WorldBoxRenderer.outlineBox(
               outlinePoses, outlineBounds, outlineColor, 1.0f
            )
         }
      }
   }

   init {
      this.crawlingSetting = this.add(BooleanSetting("Crawling", false))
      this.inBlocksSetting = this.add(BooleanSetting("InBlocks", false))
      this.insideSetting = this.add(BooleanSetting("Inside", false).dependsOn(this.inBlocksSetting))
      this.safeSetting = this.add(ColorSetting("Safe", Color(540373280, true)))
      this.semiSafeSetting = this.add(ColorSetting("SemiSafe", Color(637525248, true)))
      this.unsafeSetting = this.add(ColorSetting("Unsafe", Color(634257408, true)))
      this.outlineSetting = this.add(BooleanSetting("Outline", false).markVisibilityDependency())
      this.alphaSetting = this.add(NumberSetting<Float>("Alpha", 1.0f, 0.0f, 1.0f).dependsOn(this.outlineSetting))
      this.blockAnimations = HashMap()
   }

   private enum class Safety {
      SAFE,
      SEMI,
      UNSAFE;

      fun getFadedColor(
         module: PhaseESPModule, animation: BlockAnimation
      ): Color {
         val colorSetting: Setting<Color> = when (this) {
            SAFE -> module.safeSetting
            SEMI -> module.semiSafeSetting
            UNSAFE -> module.unsafeSetting
         }
         val baseColor = colorSetting.getValue()
         val baseAlpha = baseColor.alpha.toFloat()
         return ColorUtils.withAlpha(
            baseColor, (baseAlpha * animation.getAlpha()).toInt()
         )
      }

   }

   private class BlockAnimation(
      val createdAtMillis: Long,
      val expiresAtMillis: Long
   ) {

      fun refresh(): BlockAnimation {
         return BlockAnimation(this.createdAtMillis, System.currentTimeMillis() + 200L)
      }

      fun getAlpha(): Float {
         val ageMillis = System.currentTimeMillis() - this.createdAtMillis
         var alphaValue = ageMillis.toFloat()
         if (alphaValue <= 75.0f) {
            alphaValue = ageMillis.toFloat()
            return alphaValue / 75.0f
         }

         alphaValue = ageMillis.toFloat()
         val lifetimeMillis = (this.expiresAtMillis - this.createdAtMillis).toFloat()
         if (alphaValue >= lifetimeMillis - 75.0f) {
            alphaValue = (this.expiresAtMillis - this.createdAtMillis - ageMillis).toFloat()
            alphaValue /= 75.0f
            return Mth.clamp(alphaValue, 0.0f, 1.0f)
         }

         return 1.0f
      }

      companion object {
         fun create(): BlockAnimation {
            val animation = BlockAnimation(System.currentTimeMillis(), 0L)
            return animation.refresh()
         }
      }

   }

   companion object {
      private fun isPlayerInCollidableBlock(): Boolean {
         val playerPosition = PlayerStateUtils.getLocalRoundedBlockPosition()
         return BlockInteractionUtils.isCollidable(playerPosition)
      }
   }

}
