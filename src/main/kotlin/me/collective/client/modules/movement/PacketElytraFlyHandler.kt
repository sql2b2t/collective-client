package me.collective.client.modules.movement

import me.collective.client.utils.math.MathConstants.FULL_TURN_DEGREES

import me.collective.client.ClientAccess
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.movement.ElytraFlyModeHandler

import net.minecraft.core.Direction.Axis


class PacketElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {
   private var accelerationStartTimeMillis: Long = 0
   private var flightSpeed: Float = 0.0f
   private val idleMovementTimer = ElapsedTimer()

   override fun onEnable() {
      this.flightSpeed = 0.0f
      this.accelerationStartTimeMillis = System.currentTimeMillis()
   }

   override fun handleMovement(event: LocalPlayerMoveEvent) {
      if (!this.module.isPacketFlightActive()) {
         return
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
      }
      this.flightSpeed = if (this.module.accelerateSetting.value) {
         MovementPhysics.accelerateTo(
            this.module.flightSpeedSetting.value.toDouble(), this.module.accelMinSetting.value.toDouble(),
            this.module.accelTimeSetting.value.toDouble(), this.accelerationStartTimeMillis
         ).toFloat()
      } else {
         this.module.flightSpeedSetting.value
      }
      val horizontalMotion = MovementPhysics.applyHorizontalMotion(event, this.flightSpeed.toDouble())
      val glideMotion: Float = -this.module.glideSetting.value
      ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(horizontalMotion[0], glideMotion.toDouble(), horizontalMotion[1])
      event.setY(glideMotion.toDouble())
      if (ClientAccess.collectiveMinecraft.player!!.input.keyPresses.shift()) {
         event.setY(-0.5)
         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(
            ClientAccess.collectiveMinecraft.player!!.deltaMovement.with(Axis.Y, -0.5)
         )
      }
      PacketSender.sendStartFallFlying()
      if (this.module.antiAfkSetting.value && event.x == 0.0 && event.z == 0.0 &&
         this.idleMovementTimer.hasElapsed(2500L)
      ) {
         val radians = Math.toRadians((ClientAccess.collectiveMinecraft.player!!.tickCount % FULL_TURN_DEGREES).toDouble())
         event.setHorizontalVelocity(Math.sin(radians) * 0.03, Math.cos(radians) * 0.03)
         return
      }
      if (MovementPhysics.isLocalPlayerMoving()) {
         this.idleMovementTimer.reset()
      }
   }
}
