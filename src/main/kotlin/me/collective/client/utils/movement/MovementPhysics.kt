package me.collective.client.utils.movement

import me.collective.client.render.RenderUtils

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.events.LocalPlayerMoveEvent

import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape

open class MovementPhysics : ClientAccess {
   companion object {
      @JvmField
      var baseMoveSpeed: Float = 0.2873F

      @JvmField
      var scaledBaseMoveSpeed: Float = baseMoveSpeed * 0.2F

      @JvmStatic
      fun getMovementYaw(): Float {
         val player = ClientAccess.collectiveMinecraft.player ?: return 0.0f
         return getMovementYaw(player.yRot)
      }

      @JvmStatic
      fun getJumpBoost(): Double {
         val player = ClientAccess.collectiveMinecraft.player ?: return 0.0
         if (!player.hasEffect(MobEffects.JUMP_BOOST)) {
            return 0.0
         }
         return (player.getEffect(MobEffects.JUMP_BOOST)!!.amplifier + 1) * 0.1
      }

      @JvmStatic
      fun getMovementYaw(yaw: Float): Float {
         val player = ClientAccess.collectiveMinecraft.player ?: return yaw
         val motion = getHorizontalMotion(yaw, player.input, 1.0)
         return (Math.toDegrees(Math.atan2(motion[1], motion[0])) - MathConstants.NINETY_DEGREES).toFloat()
      }

      @JvmStatic
      fun isMovingToward(player: Player, target: Vec3): Boolean {
         val velocity = player.deltaMovement
         if (!isMoving(player)) {
            return false
         }
         val forward = player.position().add(velocity.x * 10000.0, 0.0, velocity.z * 10000.0)
         val backward = player.position().add(velocity.x * -10000.0, 0.0, velocity.z * -10000.0)
         return forward.distanceToSqr(target) < backward.distanceToSqr(target)
      }

      @JvmStatic
      fun applySpeedEffects(includeSlowness: Boolean, baseSpeed: Double): Double {
         val player = ClientAccess.collectiveMinecraft.player ?: return baseSpeed
         var speed = baseSpeed
         if (player.hasEffect(MobEffects.SPEED)) {
            speed *= 1.0 + 0.2 * (player.getEffect(MobEffects.SPEED)!!.amplifier + 1)
         }
         if (includeSlowness && player.hasEffect(MobEffects.SLOWNESS)) {
            speed /= 1.0 + 0.2 * (player.getEffect(MobEffects.SLOWNESS)!!.amplifier + 1)
         }
         return speed
      }

      @JvmStatic
      fun getHorizontalMotion(yaw: Float, input: ClientInput, speed: Double): DoubleArray {
         var yaw = yaw
         var forward = input.moveVector.y
         var strafe = input.moveVector.x
         if (forward != 0.0F) {
            if (strafe > 0.0F) {
               yaw += if (forward > 0.0F) -MathConstants.FORTY_FIVE_DEGREES else MathConstants.FORTY_FIVE_DEGREES
            } else if (strafe < 0.0F) {
               yaw += if (forward > 0.0F) MathConstants.FORTY_FIVE_DEGREES else -MathConstants.FORTY_FIVE_DEGREES
            }
            strafe = 0.0F
            if (forward > 0.0F) {
               forward = 1.0F
            } else if (forward < 0.0F) {
               forward = -1.0F
            }
         }

         val radians = Math.toRadians(yaw.toDouble())
         val x = forward * speed * -Math.sin(radians) + strafe * speed * Math.cos(radians)
         val z = forward * speed * Math.cos(radians) - strafe * speed * -Math.sin(radians)
         return doubleArrayOf(x, z)
      }

      @JvmStatic
      fun accelerateTo(
         target: Double, initial: Double, durationSeconds: Double, startedAtMillis: Long
      ): Double {
         if (initial >= target) {
            return target
         }

         var elapsedFraction = (System.currentTimeMillis() - startedAtMillis).toDouble()
         elapsedFraction /= durationSeconds * 1000.0
         val progress = Mth.clamp(elapsedFraction, 0.0, 1.0)
         val value = initial + (target - initial) * progress
         return Math.min(value, target)
      }

      @JvmStatic
      fun applyHorizontalMotion(event: LocalPlayerMoveEvent, speed: Double): DoubleArray {
         if (!isLocalPlayerMoving()) {
            event.setHorizontalVelocity(0.0, 0.0)
            return doubleArrayOf(0.0, 0.0)
         }

         val player = ClientAccess.collectiveMinecraft.player!!
         if (player == null) {
            event.setHorizontalVelocity(0.0, 0.0)
            return doubleArrayOf(0.0, 0.0)
         }
         val yaw = player.getViewYRot(RenderUtils.getPartialTick())
         val motion = getHorizontalMotion(yaw, player.input, speed)
         event.setHorizontalVelocity(motion[0], motion[1])
         return motion
      }

      @JvmStatic
      fun getHorizontalSpeedKmh(entity: Entity): Double {
         val x = entity.x - entity.xo
         val z = entity.z - entity.zo
         var speed = Math.hypot(x, z)
         speed *= 20.0
         return speed * 3.6
      }

      @JvmStatic
      fun findStepHeight(maximumHeight: Double): Double {
         val player = ClientAccess.collectiveMinecraft.player ?: return 0.0
         if (!player.onGround() || !player.horizontalCollision) {
            return 0.0
         }
         var probeBounds = player.boundingBox.move(0.0, 0.05, 0.0).inflate(0.05)
         probeBounds = probeBounds.setMaxY(probeBounds.maxY + maximumHeight)
         var height = -1.0
         val level = ClientAccess.collectiveMinecraft.level ?: return 0.0
         for (shape in level.getCollisions(player, probeBounds)) {
            for (collisionBox in shape.toAabbs()) {
               if (collisionBox.maxY > height) {
                  height = collisionBox.maxY
               }
            }
         }
         height -= player.y
         return if (height > 0.0 && height <= maximumHeight) height else 0.0
      }

      @JvmStatic
      fun isLocalPlayerMoving(): Boolean {
         return isMoving(ClientAccess.collectiveMinecraft.player!!)
      }

      @JvmStatic
      fun isMoving(player: Player?): Boolean {
         if (player == null) {
            return false
         }
         if (player is LocalPlayer) {
            val movement = player.input.moveVector
            return movement.y != 0.0F || movement.x != 0.0F
         }
         return player.zza != 0.0F || player.xxa != 0.0F
      }

      @JvmStatic
      fun isMovingToward(target: Vec3): Boolean {
         val player = ClientAccess.collectiveMinecraft.player ?: return false
         return isMovingToward(player, target)
      }

      @JvmStatic
      fun getBaseMoveSpeed(includeSlowness: Boolean): Double {
         return applySpeedEffects(includeSlowness, baseMoveSpeed.toDouble())
      }
   }

}
