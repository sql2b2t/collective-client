package me.collective.client.utils.entity

import me.collective.client.integrations.ProtocolCompatibility

import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.math.MathUtils
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractClientPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.SneakStateChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.bus.EventBus

import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3

class PlayerStateTracker : ClientAccess {
   @Volatile @JvmField var packetX: Double = 0.0
   @Volatile @JvmField var packetY: Double = 0.0
   @Volatile @JvmField var packetZ: Double = 0.0
   @Volatile @JvmField var packetOnGround: Boolean = false
   @Volatile @JvmField var sneaking: Boolean = false
   @Volatile @JvmField var lastSentInput: Input? = null
   @Volatile @JvmField var lastGroundedY: Double = 0.0
   @Volatile @JvmField var currentTickY: Double = 0.0
   @Volatile @JvmField var previousTickY: Double = 0.0
   @Volatile @JvmField var groundedTicks: Int = 0
   @Volatile @JvmField var airborneTicks: Int = 0
   @Volatile @JvmField var fallDistance: Float = 0.0F
   @Volatile @JvmField var packetRotations: FloatArray = FloatArray(2)
   @Volatile @JvmField var tickDisplacement: Vec3? = null
   @JvmField val fallFlyingTimer: ElapsedTimer = ElapsedTimer()

   fun isPacketOnGround(): Boolean {
      return this.packetOnGround
   }

   fun getPreviousTickY(): Double {
      return this.previousTickY
   }

   fun getPacketRotations(): FloatArray {
      return this.packetRotations
   }

   fun getLastGroundedY(): Double {
      return this.lastGroundedY
   }

   fun isSneaking(): Boolean {
      return this.sneaking
   }

   fun getLastSentInput(): Input? {
      return this.lastSentInput
   }

   fun getFallFlyingTimer(): ElapsedTimer {
      return this.fallFlyingTimer
   }

   fun getCurrentTickY(): Double {
      return this.currentTickY
   }

   fun getTickDisplacement(): Vec3 {
      return this.tickDisplacement
         ?: ClientAccess.collectiveMinecraft.player?.getDeltaMovement()
         ?: Vec3.ZERO
   }

   fun getPacketPosition(): Vec3 {
      return Vec3(
         this.packetX,
         this.packetY,
         this.packetZ
      )
   }

   fun getGroundedTicks(): Int {
      return this.groundedTicks
   }

   fun getFallDistance(): Float {
      return this.fallDistance
   }

   fun getAirborneTicks(): Int {
      return this.airborneTicks
   }

   init {
      this.packetRotations = FloatArray(2)
      ClientAccess.eventBus.subscribe(this)
   }

   fun setFallDistance(distance: Float) {
      this.fallDistance = distance
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val heightPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return

      if (PlayerStateUtils.isFallFlying()) {
         this.fallFlyingTimer.reset()
      }

      this.previousTickY = this.currentTickY
      this.currentTickY = heightPlayer.getY()
      this.tickDisplacement = MathUtils.getPositionDelta(heightPlayer)
      val flightPlayer: LocalPlayer = heightPlayer

      flightPlayer.isFallFlying()
      val groundPlayer: LocalPlayer = flightPlayer
      if (!groundPlayer.onGround() && !this.packetOnGround) {
         this.groundedTicks = 0
         this.airborneTicks++
      } else {
         this.airborneTicks = 0
         this.groundedTicks++
         val groundedHeightPlayer: LocalPlayer = flightPlayer
         this.lastGroundedY = groundedHeightPlayer.getY()
      }
   }

   fun runSneaking(action: Runnable) {
      val tracker = ClientContext.playerStateTracker
      if (!tracker.isSneaking()) {
         this.setSneaking(true)
         action.run()
         this.setSneaking(false)
      } else {
         action.run()
      }
   }

   fun setSneaking(requestedSneaking: Boolean) {
      val eventBus: EventBus = ClientAccess.eventBus
      val event = SneakStateChangeEvent(requestedSneaking)
      val postedEvent = eventBus.post(event) as SneakStateChangeEvent
      if (!postedEvent.isCancelled()) {
         ProtocolCompatibility.setSneaking(requestedSneaking)
      }
   }

   fun runWithoutSneaking(action: Runnable) {
      val tracker = ClientContext.playerStateTracker
      if (tracker.isSneaking()) {
         this.setSneaking(false)
         action.run()
         this.setSneaking(true)
      } else {
         action.run()
      }
   }

   @EventHandler(priority = -9999)
   private fun onConnectionDoSendPacketEvent(
      event: PacketEvent.PostSend
   ) {
      if (event.isCancelled()) {
         return
      }
      if (event.packet is ServerboundMovePlayerPacket) {
         val movementPacket = event.packet as ServerboundMovePlayerPacket
         this.packetOnGround = movementPacket.isOnGround()
         if (movementPacket.hasPosition()) {
            this.packetX = movementPacket.getX(0.0)
            this.packetY = movementPacket.getY(0.0)
            this.packetZ = movementPacket.getZ(0.0)
         }

         if (movementPacket.hasRotation()) {

            val rotations = FloatArray(2)
            rotations[0] = movementPacket.getYRot(0.0F)
            rotations[1] = movementPacket.getXRot(0.0F)
            this.packetRotations = rotations
         }
      }

      if (event.packet is ServerboundPlayerInputPacket) {
         val inputPacket = event.packet as ServerboundPlayerInputPacket
         val decodedInput: Input
         try {
            decodedInput = inputPacket.input()
         } catch (failure: Throwable) {
            throw MatchException(failure.toString(), failure)
         }

         this.sneaking = decodedInput.shift()
         this.lastSentInput = decodedInput
      }

   }

   @EventHandler
   private fun onAbstractClientPlayerTickEvent(event: AbstractClientPlayerTickEvent) {
      val heightPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val previousY = heightPlayer.yo
      val downwardDistance = previousY - heightPlayer.getY()
      val groundPlayer: LocalPlayer = heightPlayer
      if (!groundPlayer.onGround()) {
         val liquidPlayer: LocalPlayer = heightPlayer
         if (!PlayerStateUtils.isInLiquid(liquidPlayer)) {
            if (downwardDistance > 0.0) {
               this.fallDistance += downwardDistance.toFloat()
            }

            return
         }
      }

      this.fallDistance = 0.0F
   }
}

val PlayerStateTracker.lastGroundedY: Double
   get() = getLastGroundedY()

val PlayerStateTracker.groundedTicks: Int
   get() = getGroundedTicks()

val PlayerStateTracker.packetRotations: FloatArray
   get() = getPacketRotations()

val PlayerStateTracker.tickDisplacement: Vec3
   get() = getTickDisplacement()

val PlayerStateTracker.fallFlyingTimer: ElapsedTimer
   get() = getFallFlyingTimer()
