package me.collective.client.utils.movement

import me.collective.client.render.RenderUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.Vec3Utils

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.movement.SpeedModeHandler
import me.collective.client.modules.movement.LongJumpModule
import me.collective.client.modules.movement.SpeedModule

import net.minecraft.client.player.LocalPlayer

open class StrafeSpeedHandler(speedModule: SpeedModule) : SpeedModeHandler(speedModule) {
   init {
      movementStage = 4
   }

   override fun onMove(event: LocalPlayerMoveEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (speedModule.shouldPauseInBlocks() || speedModule.shouldPauseInLiquid() ||
         PlayerStateUtils.isHungerLimited() || player.isFallFlying || player.isSpectator ||
         longJumpModule!!.isToggled()
      ) {
         return
      }

      var motionY = event.y
      if (speedModule.useTimerSetting.value) {
         if (ClientContext.serverStateManager.setbackTimer.hasElapsed(250L)) {
            ClientContext.tickRateController.requestMultiplier(speedModule, 1.0888F)
         } else {
            ClientContext.tickRateController.releaseMultiplier(speedModule)
         }
      }
      val speedMultiplier = if (!speedModule.explosionBoostTimer.hasElapsed(1000L) &&
         speedModule.boostSetting.value) 1.2F else 1.0F
      val configuredSpeed = 0.2873 * speedModule.speedSetting.value * speedMultiplier
      if (movementStage == 1 && MovementPhysics.isLocalPlayerMoving()) {
         moveSpeed = 1.35 * MovementPhysics.applySpeedEffects(false, configuredSpeed) - 0.01
      } else if (movementStage == 2 && MovementPhysics.isLocalPlayerMoving() && player.verticalCollisionBelow) {
         motionY = 0.42 + MovementPhysics.getJumpBoost()
         moveSpeed *= 1.535
      } else if (movementStage == 3) {
         moveSpeed = lastHorizontalDistance - 0.6600000262260437 *
            (lastHorizontalDistance - MovementPhysics.applySpeedEffects(true, configuredSpeed))
      } else {
         if ((hasVerticalMotionCollision() || player.verticalCollision) && movementStage > 0) {
            movementStage = if (MovementPhysics.isLocalPlayerMoving()) 1 else 0
         }
         moveSpeed = lastHorizontalDistance - lastHorizontalDistance / 159.0
      }
      moveSpeed = Math.max(moveSpeed, MovementPhysics.applySpeedEffects(false, configuredSpeed))
      var motionX = 0.0
      var motionZ = 0.0
      if (MovementPhysics.isLocalPlayerMoving()) {
         val motion = MovementPhysics.getHorizontalMotion(
            player.getViewYRot(RenderUtils.getPartialTick()), player.input, moveSpeed
         )
         motionX = motion[0]
         motionZ = motion[1]
      }
      Vec3Utils.setComponents(event.velocity, motionX, motionY, motionZ)
      if (MovementPhysics.isLocalPlayerMoving()) {
         movementStage++
      }
   }

   companion object {
      private var longJumpModule: LongJumpModule? = null

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         longJumpModule = modules.getModule(LongJumpModule::class.java) as LongJumpModule
      }
   }
}
