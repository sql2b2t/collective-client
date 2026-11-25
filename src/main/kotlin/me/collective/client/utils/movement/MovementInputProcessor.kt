package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.integrations.ProtocolCompatibility
import me.collective.client.integrations.ProtocolVersionAccess

import me.collective.mixin.LocalPlayerAccessor

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2

class MovementInputProcessor : ClientAccess {
   fun applyInputPreservingBobbing() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val previousYawBob = player.yBobO
      val previousPitchBob = player.xBobO
      val pitchBob = player.xBob
      val yawBob = player.yBob

      player.input.tick()
      player.yBobO = previousYawBob
      player.xBobO = previousPitchBob
      player.xBob = pitchBob
      player.yBob = yawBob
   }

   fun normalizeInput(input: Vec2): Vec2 {
      val targetVersion: ProtocolVersionAccess = ProtocolCompatibility.getTargetVersion()
      if (targetVersion.olderEq_1_21_4()) {
         return applyMovementSpeedFactors(ClientAccess.collectiveMinecraft.player!!, input)
      }

      return input.normalized()
   }

   companion object {
      @JvmStatic
      fun applyMovementSpeedFactors(player: net.minecraft.client.player.LocalPlayer, input: Vec2): Vec2 {
         var modified = input
         if (player.isUsingItem && !player.isPassenger) {
            modified = modified.scale(net.minecraft.client.player.LocalPlayer.USING_ITEM_SPEED_FACTOR)
         }
         if (player.isMovingSlowly) {
            val sneakSpeed = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SNEAKING_SPEED).toFloat()
            modified = modified.scale(sneakSpeed)
         }
         return modified
      }
   }

   fun getRelativeDirection(movementYaw: Float, rotationYaw: Float): MovementDirection {
      val difference = (Mth.degreesDifference(movementYaw, rotationYaw) + 360.0) % 360.0
      val directions = MovementDirection.values()
      return directions[Math.round(difference / 45.0).toInt() % directions.size]
   }
}
