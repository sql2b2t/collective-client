package me.collective.client.utils.movement

import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.EntityUtils

import me.collective.client.ClientAccess

import java.util.ArrayList
import java.util.Collections
import net.minecraft.core.Direction.Axis
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import org.joml.Vector3d

class PlayerMotionPredictor(player: Player) : ClientAccess {
   private val player: Player = player
   private val motionHistory: MotionHistory = MotionHistory(10)
   private val predictedBoxes: MutableList<AABB> = ArrayList()
   private val motion: Vector3d = Vector3d(0.0)
   private val lastObservedPosition: Vector3d = Vector3d(0.0)

   fun updatePredictions() {
      synchronized(predictedBoxes) {
         motion.set(player.x, player.y, player.z)
         if (lastObservedPosition.equals(motion)) {
            return
         }
         lastObservedPosition.set(motion)
         predictedBoxes.clear()
         motion.sub(player.xo, player.yo, player.zo)
         val previous = motionHistory.getLatest()
         val accelerationX = if (previous == null) 0.0 else motion.x - previous.motionX()
         val accelerationZ = if (previous == null) 0.0 else motion.z - previous.motionZ()
         val verticalMotion = motion.y
         motionHistory.append(
            MotionSample(
               motion.x, motion.y, motion.z, accelerationX, accelerationZ,
               EntityUtils.hasCollisionBelow(player), player.isShiftKeyDown, player.isFallFlying
            )
         )
         motion.set(
            motionHistory.weightedAverage { sample -> sample.motionX() }, verticalMotion,
            motionHistory.weightedAverage { sample -> sample.motionZ() }
         )
         val speedLimit = if (player.isFallFlying) 1.8F.toDouble() else MAXIMUM_HORIZONTAL_SPEED.toDouble()
         val minimumSpeed = if (player.isShiftKeyDown) SNEAKING_MINIMUM_SPEED.toDouble() else BASE_MOVEMENT_SPEED.toDouble()
         val horizontalSpeed = Math.hypot(motion.x, motion.z)
         if (horizontalSpeed > 0.0001) {
            val speed = Mth.clamp(horizontalSpeed, minimumSpeed, speedLimit)
            motion.set(motion.x / horizontalSpeed * speed, motion.y, motion.z / horizontalSpeed * speed)
         } else {
            motion.set(0.0, motion.y, 0.0)
         }
         if (EntityUtils.hasCollisionBelow(player) && motion.y > 0.0) {
            motion.y = (-0.0784F).toDouble()
         }

         var bounds = BoundingBoxUtils.getInterpolationBounds(player)
         if (horizontalSpeed < 0.0001 && Math.abs(motion.y) < 0.01) {
            predictedBoxes.addAll(Collections.nCopies(PREDICTION_TICKS, bounds))
            return
         }
         val averageAccelerationX = motionHistory.average { sample -> sample.accelerationX() }
         val averageAccelerationZ = motionHistory.average { sample -> sample.accelerationZ() }
         for (tick in 0 until PREDICTION_TICKS) {
            val grounded = hasBlockCollision(player, bounds.move(0.0, -0.01, 0.0))
            val drag = if (grounded) 0.54 else 0.98
            val accelerationWeight = Math.max(0.0, 1.0 - tick * 0.15)
            motion.set(
               (motion.x + averageAccelerationX * accelerationWeight) * drag,
               motion.y,
               (motion.z + averageAccelerationZ * accelerationWeight) * drag
            )
            val predictedSpeed = Math.hypot(motion.x, motion.z)
            if (predictedSpeed > speedLimit) {
               motion.set(motion.x / predictedSpeed * speedLimit, motion.y, motion.z / predictedSpeed * speedLimit)
            }
            motion.y = if (grounded) (-0.0784F).toDouble() else applyGravityAndDrag(motion.y)
            bounds = moveWithCollisions(bounds, motion.x, motion.y, motion.z)
            predictedBoxes.add(bounds)
         }
      }
   }

   @Synchronized
   fun getPredictedBoxes(): List<AABB> {
      return predictedBoxes
   }

   fun reset() {
      motionHistory.clear()
   }

   private fun hasBlockCollision(player: Player, bounds: AABB): Boolean {
      val level = ClientAccess.collectiveMinecraft.level ?: return false
      return level.getBlockCollisions(player, bounds).iterator().hasNext()
   }

   private fun moveWithCollisions(
      bounds: AABB, x: Double, y: Double, z: Double
   ): AABB {
      val level = ClientAccess.collectiveMinecraft.level ?: return bounds
      val collisions = ArrayList<VoxelShape>(
         Entity.collectAllColliders(
            player, level, bounds.expandTowards(x, y, z)
         )
      )
      return resolveMovement(bounds, collisions, x, y, z)
   }

   private fun applyGravityAndDrag(verticalMotion: Double): Double {
      return if (player.isFallFlying) verticalMotion else (verticalMotion - 0.08) * 0.98
   }

   companion object {
      private const val PREDICTION_TICKS: Int = 20
      private val BASE_MOVEMENT_SPEED: Float = MovementPhysics.baseMoveSpeed
      private val SNEAKING_MINIMUM_SPEED: Float = BASE_MOVEMENT_SPEED * 0.3F
      private val MAXIMUM_HORIZONTAL_SPEED: Float = BASE_MOVEMENT_SPEED * 1.4F + 0.2F

      private fun resolveMovement(
         bounds: AABB, collisions: List<VoxelShape>, x: Double, y: Double, z: Double
      ): AABB {
         if (collisions.isEmpty()) {
            return bounds.move(x, y, z)
         }

         val originalBounds = bounds
         var bounds = bounds
         var y = y
         if (y != 0.0) {
            y = Shapes.collide(Axis.Y, bounds, collisions, y)
            if (y != 0.0) {
               bounds = bounds.move(0.0, y, 0.0)
            }
         }

         var z = z
         val zFirst = Math.abs(x) < Math.abs(z)
         if (zFirst && z != 0.0) {
            z = Shapes.collide(Axis.Z, bounds, collisions, z)
            if (z != 0.0) {
               bounds = bounds.move(0.0, 0.0, z)
            }
         }

         var x = x
         if (x != 0.0) {
            x = Shapes.collide(Axis.X, bounds, collisions, x)
            if (!zFirst && x != 0.0) {
               bounds = bounds.move(x, 0.0, 0.0)
            }
         }

         if (!zFirst && z != 0.0) {
            z = Shapes.collide(Axis.Z, bounds, collisions, z)
         }

         return originalBounds.move(x, y, z)
      }
   }

}
