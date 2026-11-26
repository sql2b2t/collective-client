package me.collective.client.modules.movement

import me.collective.client.modules.movement.ElytraFlyModule.TakeoffChoice

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.movement.ElytraFlyModeHandler
import me.collective.client.managers.TickRateController
import me.collective.client.events.EventStage
import me.collective.client.settings.Setting

import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3


class StrictElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {
   private var accelerationStartTimeMillis: Long = 0
   private var takeoffPending: Boolean = false
   private var awaitingFlight: Boolean = false
   private var fireworkLaunchPending: Boolean = false

   override fun onEnable() {
      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
         if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
            this.takeoffPending = true
            ClientAccess.collectiveMinecraft.player!!.stopFallFlying()
         }
      }

      this.awaitingFlight = false
      this.fireworkLaunchPending = false
      this.accelerationStartTimeMillis = System.currentTimeMillis()
   }

   override fun handleOutgoingPacket(event: PacketEvent.Send) {
      if (event.packet is ServerboundMovePlayerPacket) {
         if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
            if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
               val velocity: Vec3 = ClientAccess.collectiveMinecraft.player!!.getDeltaMovement()
               val verticalMotion: Double = velocity.y

               if (verticalMotion < 0.1 && MovementPhysics.isLocalPlayerMoving()) {
                  val packetAccess: ServerboundMovePlayerPacketAccessor = event.packet as ServerboundMovePlayerPacketAccessor
                  packetAccess.setPitch(-5.0f)
               }
            }
         }
      }
   }

   override fun handlePlayerTick(event: LocalPlayerTickEvent) {
      if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
         this.takeoffPending = false
      } else {
         if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
            this.takeoffPending = true
         }
      }

      val input: Input = ClientAccess.collectiveMinecraft.player!!.input.keyPresses
      var requestTakeoff: Boolean = input.jump()
      val takeoffSetting: Setting<TakeoffChoice> = this.module.takeoffSetting
      if (takeoffSetting.value == ElytraFlyModule.TakeoffChoice.STRICT) {
         val strictGroundPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         requestTakeoff = false
         if (!strictGroundPlayer.onGround()) {
            val velocity: Vec3 = ClientAccess.collectiveMinecraft.player!!.getDeltaMovement()
            val verticalMotion: Double = velocity.y
            requestTakeoff = verticalMotion < 0.1
         }
      } else {
         val timerOwner: ElytraFlyModule = this.module
         val timerController: TickRateController = ClientContext.tickRateController
         timerController.releaseMultiplier(timerOwner)
      }

      if (requestTakeoff || this.awaitingFlight) {
         this.module.updateTakeoff()
         val playerAfterTakeoff: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         this.awaitingFlight = !playerAfterTakeoff.isFallFlying()
      }
   }

   override fun handleMovement(event: LocalPlayerMoveEvent) {
      if (!ClientAccess.collectiveMinecraft.player!!.isFallFlying() &&
         this.takeoffPending &&
         !ClientAccess.collectiveMinecraft.player!!.horizontalCollision) {
         val fallingVelocity: Vec3 = ClientAccess.collectiveMinecraft.player!!.getDeltaMovement()
         if (fallingVelocity.y < 0.0) {
            this.awaitingFlight = true
         }
      }

      if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
         if (!MovementPhysics.isLocalPlayerMoving()) {
            this.accelerationStartTimeMillis = System.currentTimeMillis()
         }

         val maximumSpeed: Float = this.module.strictSpeedSetting.value
         val startSpeed: Float = this.module.startSetting.value
         val elapsedMillis: Float = (System.currentTimeMillis() - this.accelerationStartTimeMillis).toFloat()

         val progress: Float = Mth.clamp(elapsedMillis / 500.0f, 0.0f, 1.0f)
         val speed: Float = Mth.lerp(progress, startSpeed, maximumSpeed)
         val horizontalMotion: DoubleArray = MovementPhysics.applyHorizontalMotion(event, speed.toDouble())
         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(horizontalMotion[0], 0.0, horizontalMotion[1])
         event.setY(0.0)
         ClientAccess.collectiveMinecraft.player!!.setDeltaMovement(
            ClientAccess.collectiveMinecraft.player!!.getDeltaMovement().with(Axis.Y, 0.0)
         )
      }
   }

   override fun handleIncomingPacket(event: PacketEvent.Receive) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         if (ClientAccess.collectiveMinecraft.player!!.isFallFlying()) {
            if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {
               this.accelerationStartTimeMillis = System.currentTimeMillis()
               ClientAccess.collectiveMinecraft.player!!.stopFallFlying()
            }
         }
      }
   }

   override fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
      if (event.stage == EventStage.Pre) {
         var adjustRotation: Boolean = false
         if (this.module.isElytraEquipped()) {
            if (!event.onGround) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               if (!player.isFallFlying()) {
                  adjustRotation = true
                  this.fireworkLaunchPending = false
               } else if (!this.fireworkLaunchPending) {
                  if (!MovementPhysics.isLocalPlayerMoving()) {
                     adjustRotation = true
                  } else {
                     this.fireworkLaunchPending = true
                  }
               }
            }
         }

         if (adjustRotation) {
            val originalYaw: Float = event.yaw
            val yawTick: Int = ClientAccess.collectiveMinecraft.player!!.tickCount

            val yawOffset: Int = if (yawTick % 2 == 0) -2 else 2
            val yaw: Float = originalYaw + yawOffset
            event.yaw = yaw
            val originalPitch: Float = event.pitch
            val pitchTick: Int = ClientAccess.collectiveMinecraft.player!!.tickCount
            val pitchOffset: Int = if (pitchTick % 2 == 0) -2 else 2
            val pitch: Float = originalPitch + pitchOffset
            event.pitch = pitch
         }
      }
   }
}
