package me.collective.client.utils.combat

import me.collective.client.ClientAccess
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.settings.Setting
import me.collective.client.utils.math.MathConstants

import java.util.ArrayList
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class BoundingBoxUtils private constructor() : ClientAccess {
   companion object {
      @JvmField
      var blockCornerInset: Double = 0.0625

      @JvmStatic
      fun getBlockShapeBounds(position: BlockPos): AABB {
         val level = ClientAccess.collectiveMinecraft.level ?: return AABB(position)
         val state = level.getBlockState(position)
         if (state.isAir) {
            return AABB(position)
         }

         if (state.`is`(Blocks.AIR)) {
            return AABB(position)
         }

         var shape = state.getShape(level, position)

         if (shape.isEmpty) {
            shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
         }

         val bounds = shape.bounds()
         return bounds.move(position)
      }

      @JvmStatic
      fun hasUnitDimensions(bounds: AABB): Boolean {
         return bounds.zsize == 1.0 &&
         bounds.ysize == 1.0 &&
         bounds.xsize == 1.0
      }

      @JvmStatic
      fun getCornersByDistance(bounds: AABB, y: Double): List<Vec3> {
         val corners = ArrayList(
            listOf(
               Vec3(bounds.minX, y, bounds.minZ),
               Vec3(bounds.maxX, y, bounds.minZ),
               Vec3(bounds.minX, y, bounds.maxZ),
               Vec3(bounds.maxX, y, bounds.maxZ)
            )
         )
         val player = ClientAccess.collectiveMinecraft.player ?: return corners
         corners.sortBy { corner ->
            player.eyePosition.distanceToSqr(corner)
         }
         return corners
      }

      @JvmStatic
      fun getPredictionTicks(ticksSetting: Setting<Int>): Int {
         return ticksSetting.value
      }

      @JvmStatic
      fun recenter(bounds: AABB, center: Vec3): AABB {
         val halfX = bounds.xsize * MathConstants.ONE_HALF
         val halfY = bounds.ysize * MathConstants.ONE_HALF
         val halfZ = bounds.zsize * MathConstants.ONE_HALF
         return AABB(
            center.x - halfX, center.y - halfY, center.z - halfZ, center.x + halfX, center.y + halfY, center.z + halfZ
         )
      }

      @JvmStatic
      fun getBlockCollisionBounds(position: BlockPos, block: net.minecraft.world.level.block.Block): AABB {
         val collisionState = block.defaultBlockState()
         val level = ClientAccess.collectiveMinecraft.level ?: return AABB(position)

         var shape = collisionState.getCollisionShape(level, position)

         if (shape.isEmpty) {
            shape = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
         }

         val bounds = shape.bounds()
         return bounds.move(position)
      }

      @JvmStatic
      fun placeAtBlock(bounds: AABB, position: BlockPos): AABB {
         val center = Vec3(
            position.x + MathConstants.ONE_HALF,
            position.y + bounds.ysize * MathConstants.ONE_HALF,
            position.z + MathConstants.ONE_HALF
         )
         return recenter(bounds, center)
      }

      @JvmStatic
      fun getBottomCenter(bounds: AABB): Vec3 {
         return Vec3(
            Mth.lerp(MathConstants.ONE_HALF, bounds.minX, bounds.maxX), bounds.minY, Mth.lerp(MathConstants.ONE_HALF, bounds.minZ, bounds.maxZ)
         )
      }

      @JvmStatic
      fun hasUnitDimensions(shape: VoxelShape): Boolean {
         if (shape.isEmpty) {
            return false
         }

         val boundsSnapshot = shape.bounds()
         return hasUnitDimensions(boundsSnapshot)
      }

      @JvmStatic
      fun getRenderBounds(entity: Entity, partialTick: Float): AABB {
         val fraction = partialTick.toDouble()
         val previousX = entity.xOld

         val interpolatedX = Mth.lerp(fraction, previousX, entity.x)
         val offsetX = interpolatedX - entity.x
         val previousY = entity.yOld
         val interpolatedY = Mth.lerp(fraction, previousY, entity.y)
         val offsetY = interpolatedY - entity.y
         val previousZ = entity.zOld
         val interpolatedZ = Mth.lerp(fraction, previousZ, entity.z)
         val offsetZ = interpolatedZ - entity.z

         return entity.boundingBox.move(offsetX, offsetY, offsetZ)
      }

      @JvmStatic
      fun hasVolumeOverlap(first: AABB, second: AABB): Boolean {
         if (!first.intersects(second)) {
            return false
         }

         val intersection = first.intersect(second)
         val width = intersection.xsize
         val height = intersection.ysize
         val depth = intersection.zsize
         val minimumHeightDepth = Math.min(height, depth)
         val minimumDimension = Math.min(width, minimumHeightDepth).toFloat().toDouble()
         return minimumDimension > 1e-07
      }

      @JvmStatic
      fun getLowerQuarterCenter(bounds: AABB): Vec3 {
         val bottomCenter = getBottomCenter(bounds)
         val height = bounds.ysize
         val quarterHeight = height * 0.25
         return bottomCenter.add(0.0, quarterHeight, 0.0)
      }

      @JvmStatic
      fun clampToBounds(point: Vec3, bounds: AABB): Vec3 {
         return Vec3(
            Mth.clamp(point.x(), bounds.minX, bounds.maxX),
            Mth.clamp(point.y(), bounds.minY, bounds.maxY),
            Mth.clamp(point.z(), bounds.minZ, bounds.maxZ)
         )
      }

      @JvmStatic
      fun intersectsBlock(bounds: AABB, position: BlockPos): Boolean {
         val minX = position.x.toDouble()
         val minY = position.y.toDouble()
         val minZ = position.z.toDouble()
         val maxX = (position.x + 1).toDouble()
         val maxY = (position.y + 1).toDouble()
         val maxZ = (position.z + 1).toDouble()
         return bounds.intersects(minX, minY, minZ, maxX, maxY, maxZ)
      }

      @JvmStatic
      fun getTopCenter(bounds: AABB): Vec3 {
         return Vec3(
            Mth.lerp(MathConstants.ONE_HALF, bounds.minX, bounds.maxX), bounds.maxY, Mth.lerp(MathConstants.ONE_HALF, bounds.minZ, bounds.maxZ)
         )
      }

      @JvmStatic
      fun getInterpolationBounds(entity: LivingEntity): AABB {
         if (entity is FakeplayerCommand.SimulatedPlayer) {
            return entity.boundingBox
         }

         val bounds = entity.boundingBox

         val lerpX = entity.lerpTargetX()
         val lerpY = entity.lerpTargetY()
         val lerpZ = entity.lerpTargetZ()
         if (lerpX == entity.x && lerpY == entity.y && lerpZ == entity.z) {
            return entity.boundingBox
         }
         val width = bounds.xsize
         val halfX = width / 2.0
         val halfZ = bounds.zsize / 2.0
         val minX = -halfX
         val minZ = -halfZ
         val centeredBounds = AABB(minX, 0.0, minZ, halfX, bounds.ysize, halfZ)
         return centeredBounds.move(lerpX, lerpY, lerpZ)
      }
   }
}
