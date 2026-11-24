package me.collective.client.utils.math

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random
import java.util.concurrent.ThreadLocalRandom
import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.render.RenderUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Axis
import net.minecraft.core.Direction.AxisDirection
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

class MathUtils private constructor() {
   companion object {
      @JvmStatic
      fun distanceToBlockCenter(blockPosition: BlockPos): Double {
         val blockCenter = blockPosition.center
         return distanceToCamera(blockCenter)
      }

      @JvmStatic
      fun getPositionDelta(entity: Entity): Vec3 {
         val deltaX = entity.x - entity.xo
         val deltaY = entity.y - entity.yo
         val deltaZ = entity.z - entity.zo
         return Vec3(deltaX, deltaY, deltaZ)
      }

      @JvmStatic
      fun cameraRotationDifference(targetPosition: Vec3): Float {
         val renderer = ClientAccess.collectiveMinecraft.gameRenderer

         val camera = renderer.mainCamera
         val targetRotations = RotationManager.getRotationsToPosition(targetPosition)
         val cameraYaw = camera.yaw()
         val targetYaw = targetRotations[0]
         val yawDifference = Mth.degreesDifferenceAbs(cameraYaw, targetYaw)
         val cameraPitch = camera.xRot()
         val targetPitch = targetRotations[1]
         val pitchDifference = Mth.degreesDifferenceAbs(cameraPitch, targetPitch)
         return Math.hypot(yawDifference.toDouble(), pitchDifference.toDouble()).toFloat()
      }

      @JvmStatic
      fun toRadians(angle: Float): Float {
         return angle * MathConstants.RADIANS_PER_DEGREE
      }

      @JvmStatic
      fun passesRandomThreshold(threshold: Int): Boolean {
         return threshold != 0 && ThreadLocalRandom.current().nextInt(100) < threshold
      }

      @JvmStatic
      fun floorToDecimals(value: Double, decimalPlaces: Int): Double {
         if (decimalPlaces < 0) {
            throw IllegalArgumentException()
         }

         return BigDecimal.valueOf(value).setScale(decimalPlaces, RoundingMode.FLOOR).toDouble()
      }

      @JvmStatic
      fun randomFloat(minimum: Float, maximum: Float): Float {
         val random = Random()
         return minimum + random.nextFloat() * (maximum - minimum)
      }

      @JvmStatic
      fun rangeProgress(value: Float, lowerBound: Float, upperBound: Float): Float {
         var lower = lowerBound
         var upper = upperBound

         if (lower > upper) {
            val previousUpperBound = upper
            upper = lower
            lower = previousUpperBound
         }

         if (lower == upper) {
            return if (value >= upper) 1.0f else 0.0f
         }
         return when {
            value < lower -> 0.0f
            value > upper -> 1.0f
            else -> (value - lower) / (upper - lower)
         }
      }

      @JvmStatic
      fun interpolateFrame(start: Double, end: Double): Double {
         val frameDelta = RenderUtils.getPartialTick().toDouble()
         return Mth.lerp(frameDelta, start, end)
      }

      @JvmStatic
      fun getDistanceScale(origin: Vec3, targetPosition: Vec3, scale: Double): Double {
         val distance = origin.distanceTo(targetPosition)
         val clientOptions = ClientAccess.collectiveMinecraft.options

         val fovOption = clientOptions.fov()

         var scaleWorkValue = fovOption.get().toDouble()
         var fovScale = scaleWorkValue / 110.0
         val cappedScale = Math.min(1.0, scale)
         if (fovScale < 1.0) {
            fovScale = 1.0
         }

         if (distance <= 10.0 * (if (scale > 1.0) 1.0 / scale else scale)) {
            return cappedScale * fovScale
         }

         scaleWorkValue = distance * scale * fovScale
         return scaleWorkValue * 0.1 / cappedScale
      }

      private fun faceCorner(
         x: Double, y: Double, z: Double, firstAxis: Axis, secondAxis: Axis, firstOffset: Int, secondOffset: Int
      ): Vec3 {
         return Vec3(
            x + (if (firstAxis == Axis.X) firstOffset.toDouble() else 0.0) + (if (secondAxis == Axis.X) secondOffset.toDouble() else 0.0),
            y + (if (firstAxis == Axis.Y) firstOffset.toDouble() else 0.0) + (if (secondAxis == Axis.Y) secondOffset.toDouble() else 0.0),
            z + (if (firstAxis == Axis.Z) firstOffset.toDouble() else 0.0) + (if (secondAxis == Axis.Z) secondOffset.toDouble() else 0.0)
         )
      }

      @JvmStatic
      fun lerp(start: Double, end: Double, delta: Double): Double {
         return start + (end - start) * delta
      }

      @JvmStatic
      fun floorToDecimals(value: Float, decimalPlaces: Int): Float {
         if (decimalPlaces < 0) {
            throw IllegalArgumentException()
         }

         return BigDecimal.valueOf(value.toDouble()).setScale(decimalPlaces, RoundingMode.FLOOR).toFloat()
      }

      @JvmStatic
      fun getDistanceScale(targetPosition: Vec3, scale: Double): Double {
         val renderer = ClientAccess.collectiveMinecraft.gameRenderer

         val camera = renderer.mainCamera
         val cameraPosition = camera.position()
         return getDistanceScale(cameraPosition, targetPosition, scale)
      }

      @JvmStatic
      fun randomInt(minimum: Int, maximum: Int): Int {
         val minimumAsFloat = minimum.toFloat()
         val random = Random()
         return (minimumAsFloat + random.nextFloat() * (maximum - minimum)).toInt()
      }

      @JvmStatic
      fun nearestBound(value: Double, lowerBound: Double, upperBound: Double): Double {
         val distanceFromLower = value - lowerBound
         val distanceFromUpper = upperBound - value
         return if (distanceFromUpper > distanceFromLower) lowerBound else upperBound
      }

      @JvmStatic
      fun directionFromPitchYaw(pitch: Float, yaw: Float): Vec3 {
         val scaledPitch = toRadians(pitch)
         val negatedYaw = -yaw
         val scaledNegatedYaw = toRadians(negatedYaw)
         val cosineYaw = Mth.cos(scaledNegatedYaw.toDouble())
         val sineYaw = Mth.sin(scaledNegatedYaw.toDouble())
         val cosinePitch = Mth.cos(scaledPitch.toDouble())
         val sinePitch = Mth.sin(scaledPitch.toDouble())
         return Vec3((sineYaw * cosinePitch).toDouble(), (-sinePitch).toDouble(), (cosineYaw * cosinePitch).toDouble())
      }

      @JvmStatic
      fun distanceToCamera(targetPosition: Vec3): Double {
         val renderer = ClientAccess.collectiveMinecraft.gameRenderer

         val camera = renderer.mainCamera
         val cameraPosition = camera.position()
         return cameraPosition.distanceTo(targetPosition)
      }

      @JvmStatic
      fun faceCorners(blockPosition: BlockPos, direction: Direction): List<Vec3> {
         val faceAxis = direction.axis

         var x = blockPosition.x.toDouble()
         var y = blockPosition.y.toDouble()
         var z = blockPosition.z.toDouble()
         val faceOffset = if (direction.axisDirection == AxisDirection.POSITIVE) 1.0 else 0.0

         if (faceAxis == Axis.X) {
            x += faceOffset
         }

         if (faceAxis == Axis.Y) {
            y += faceOffset
         }

         if (faceAxis == Axis.Z) {
            z += faceOffset
         }

         val firstAxis = if (faceAxis == Axis.X) Axis.Y else Axis.X
         val secondAxis = if (faceAxis == Axis.Z) Axis.Y else Axis.Z
         val firstCorner = faceCorner(x, y, z, firstAxis, secondAxis, 0, 0)
         val secondCorner = faceCorner(x, y, z, firstAxis, secondAxis, 1, 0)
         val thirdCorner = faceCorner(x, y, z, firstAxis, secondAxis, 1, 1)
         val fourthCorner = faceCorner(x, y, z, firstAxis, secondAxis, 0, 1)
         return listOf(firstCorner, secondCorner, thirdCorner, fourthCorner)
      }

      @JvmStatic
      fun interpolateFrame(start: Float, end: Float): Float {
         val frameDelta = RenderUtils.getPartialTick()
         return Mth.lerp(frameDelta, start, end)
      }

      @JvmStatic
      fun yawDifferenceToPlayer(targetPlayer: Player): Float {
         val targetRotations = RotationManager.getRotationsToPosition(targetPlayer.position())

         val localYaw = ClientAccess.collectiveMinecraft.player!!.yRot
         val targetYaw = targetRotations[0]
         return Mth.degreesDifferenceAbs(localYaw, targetYaw)
      }

      @JvmStatic
      fun wrapAngle(angle: Float): Float {
         val wrapped = angle % MathConstants.FULL_TURN_DEGREES
         return if (wrapped < 0.0f) wrapped + MathConstants.FULL_TURN_DEGREES else wrapped
      }
   }
}
