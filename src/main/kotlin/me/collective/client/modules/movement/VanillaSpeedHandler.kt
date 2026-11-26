package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.utils.movement.MovementPhysics


class VanillaSpeedHandler(speedModule: SpeedModule) : SpeedModeHandler(speedModule) {
   override fun onMove(event: LocalPlayerMoveEvent) {
      if (speedModule.shouldPauseInBlocks() || speedModule.shouldPauseInLiquid() ||
         ClientAccess.collectiveMinecraft.player!!.isFallFlying || speedModule.shouldYieldToObstaclePasser()) {
         return
      }
      val speed = speedModule.speedSetting.value.toDouble()
      MovementPhysics.applyHorizontalMotion(event, MovementPhysics.applySpeedEffects(false, speed))
   }
}
