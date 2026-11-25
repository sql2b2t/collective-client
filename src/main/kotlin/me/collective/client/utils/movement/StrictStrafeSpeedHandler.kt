package me.collective.client.utils.movement

import me.collective.client.utils.entity.PlayerStateUtils

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientContext
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.movement.SpeedModeHandler
import me.collective.client.modules.movement.LongJumpModule
import me.collective.client.modules.movement.SpeedModule

import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

class StrictStrafeSpeedHandler(speedModule: SpeedModule) : SpeedModeHandler(speedModule) {
   override fun onRotationUpdate() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (shouldPauseStrictStrafe() || !MovementPhysics.isLocalPlayerMoving() ||
         !player.verticalCollisionBelow
      ) {
         return
      }
      val motion = MovementPhysics.getHorizontalMotion(player.yRot, player.input, 1.0)
      val movementYaw = (Math.toDegrees(Math.atan2(motion[1], motion[0])) - MathConstants.NINETY_DEGREES).toFloat()
      val radians = Math.toRadians(movementYaw.toDouble()).toFloat()
      val velocity = player.deltaMovement
      player.setDeltaMovement(velocity.x, 0.4 + MovementPhysics.getJumpBoost(), velocity.z)
      player.addDeltaMovement(
         Vec3(
            -Mth.sin(radians) * 0.2, 0.0,
            Mth.cos(radians) * 0.2
         )
      )
      player.isSprinting = true
   }

   override fun onMove(event: LocalPlayerMoveEvent) {
      if (shouldPauseStrictStrafe()) {
         return
      }
      if (speedModule.useTimerSetting.value &&
         ClientContext.serverStateManager.setbackTimer.hasElapsed(250L)
      ) {
         ClientContext.tickRateController.requestMultiplier(speedModule, 1.0888F)
      } else {
         ClientContext.tickRateController.releaseMultiplier(speedModule)
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         event.setHorizontalVelocity(0.0, 0.0)
         return
      }
      var speed = MovementPhysics.getBaseMoveSpeed(false)
      if (ClientContext.serverStateManager.setbackTimer.hasElapsed(500L)) {
         speed = Math.max(speed, Math.hypot(event.x, event.z))
      }
      MovementPhysics.applyHorizontalMotion(event, speed)
   }

   private fun shouldPauseStrictStrafe(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return true
      return speedModule.shouldPauseInBlocks() || speedModule.shouldPauseInLiquid() ||
         PlayerStateUtils.isHungerLimited() || player.isFallFlying ||
         player.isSpectator || longJumpModule!!.isToggled()
   }

   companion object {
      private var longJumpModule: LongJumpModule? = null

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         longJumpModule = modules.getModule(LongJumpModule::class.java) as LongJumpModule
      }
   }

}
