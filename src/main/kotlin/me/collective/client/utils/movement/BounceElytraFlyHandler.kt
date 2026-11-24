package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventStage
import me.collective.client.events.LivingEntityRotationEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.modules.movement.ObstaclePasserModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathConstants.FORTY_FIVE_DEGREES
import me.collective.client.utils.math.MathConstants.NINETY_DEGREES
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.network.PacketSender
import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import java.util.ArrayDeque
import java.util.Queue
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Plane
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.util.Mth
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class BounceElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {

   private val normalFlightRetryTimer: ElapsedTimer = ElapsedTimer()
   private val queuedPackets: Queue<Packet<*>> = ArrayDeque()
   private var bounceCooldownTicks: Int = 0
   private var bounceInputTicks: Int = 0
   private var durabilityBypassAtTickStart: Boolean = false
   private var fallFlyingAtTickStart: Boolean = false
   private var diagonalPacketPending: Boolean = false
   private var automaticPitch: Float = 90.0f

   private fun flushQueuedPackets() {
      if (this.isWorldUnavailable()) {
         this.queuedPackets.clear()
      } else {
         while (!this.queuedPackets.isEmpty()) {
            val packet: Packet<*>? = this.queuedPackets.poll()
            if (packet == null) {
               break
            }
            PacketSender.sendDirect(packet)
         }
      }
   }

   override fun onEnable() {
      this.bounceInputTicks = 0
      this.diagonalPacketPending = this.module.diagonalSetting.value &&
         this.module.packetSetting.value
   }

   override fun onDisable() {
      ClientAccess.collectiveMinecraft.options.keyJump.setDown(false)
      val player: LocalPlayer? = ClientAccess.collectiveMinecraft.player!!
      player?.setSprinting(false)
      this.flushQueuedPackets()
   }

   private fun canApplyBounce(): Boolean {
      return this.durabilityBypassAtTickStart || this.module.isElytraEquipped()
   }

   override fun handlePrePlayerTick(event: LocalPlayerPreTickEvent) {
      if (this.diagonalPacketPending) {
         return
      }
      this.fallFlyingAtTickStart = PlayerStateUtils.isFallFlying()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!this.isObstaclePasserActive() && this.canApplyBounce() &&
         !this.module.isItemUseSlowingFlight()) {
         ClientContext.inputOverrides.requestJump(this.bounceInputTicks != 2)
         ClientContext.inputOverrides.requestForward(true)
         ClientContext.inputOverrides.requestSprint(true)
         ClientContext.inputOverrides.requestForwardMovement(1.0f)
         player.setSprinting(true)
         this.bounceInputTicks++
      } else {
         this.bounceInputTicks = 0
      }

      this.durabilityBypassAtTickStart = this.module.isDurabilityBypassActive()
      if (player.isFallFlying()) {
         val groundedTicks: Int = ClientContext.playerStateTracker.getGroundedTicks()
         if (groundedTicks > 5) {
            player.stopFallFlying()
            return
         }
      }

      val bounceAllowed: Boolean = this.canApplyBounce()
      if (PlayerStateUtils.isInLiquid(player)) {
         return
      }
      if (PlayerStateUtils.isInCobweb(player) || this.module.isItemUseSlowingFlight()) {
         return
      }
      if (this.isObstaclePasserActive() || !bounceAllowed) {
         return
      }
      if (!PlayerStateUtils.isFallFlying()) {
         val takeoffGroundedTicks: Int = ClientContext.playerStateTracker.getGroundedTicks()
         if (takeoffGroundedTicks < 3 && !this.durabilityBypassAtTickStart) {
            player.stopFallFlying()
            PacketSender.sendStartFallFlying()
         }
      }
      if (!player.horizontalCollision && MovementPhysics.isLocalPlayerMoving()) {
         player.setSprinting(true)
         if (player.isFallFlying()) {
            player.setShiftKeyDown(false)
         }
      }
      val pitchLocked: Boolean = this.module.pitchLockSetting.value
      if (pitchLocked) {
         val silent: Boolean = this.module.silentSetting.value
         if (!silent) {
            val pitchPlayer: LocalPlayer = player
            val pitchValue: Float = this.getPitch()
            val configuredPitch: Float = pitchValue
            pitchPlayer.setXRot(configuredPitch)
         }
      }
   }

   override fun handleOutgoingPacket(event: PacketEvent.Send) {
      if (this.isObstaclePasserActive() || this.diagonalPacketPending ||
         !this.fallFlyingAtTickStart || !this.canApplyBounce()) {
         return
      }
      if (event.packet is ServerboundMovePlayerPacketAccessor) {
         val packetAccess = event.packet as ServerboundMovePlayerPacketAccessor
         val silent: Boolean = this.module.silentSetting.value
         if (silent) {
            val pitchLocked: Boolean = this.module.pitchLockSetting.value
            if (pitchLocked && !this.module.isItemUseSlowingFlight()) {
               val configuredPitch: Float = this.getPitch()
               packetAccess.setPitch(configuredPitch)
            }
         }
      }
      val compensate: Boolean = this.module.compensateSetting.value
      if (compensate && !this.module.isItemUseSlowingFlight()) {
         if (event.packet !is ServerboundPlayerCommandPacket &&
         event.packet !is ServerboundPongPacket) {
            val packetQueue: Queue<Packet<*>> = this.queuedPackets
            val packetToQueue: Packet<*> = event.packet
            packetQueue.add(packetToQueue)
            event.cancel()
         }
      }
   }

   override fun handleMovement(event: LocalPlayerMoveEvent) {
      val packetMode: Boolean = this.module.packetSetting.value
      if (packetMode) {
         if (this.diagonalPacketPending) {
            val bounceTarget: Vec3 = this.getBounceTarget()
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
            val currentPosition: Vec3 = player.position()

            val targetDistance: Float = Vec3Utils.horizontalDistance(bounceTarget, currentPosition)

            val clampedDistance: Float = Mth.clamp(targetDistance, 0.0f, 0.1f)
            val distanceAsDouble: Double = clampedDistance.toDouble()

            if (distanceAsDouble < 0.1) {
               this.diagonalPacketPending = false
               event.setHorizontalVelocity(0.0, 0.0)
               val playerToPosition: LocalPlayer = player
               val targetX: Double = bounceTarget.x
               val currentHeight: Double = player.getY()
               val targetZ: Double = bounceTarget.z
               playerToPosition.setPos(targetX, currentHeight, targetZ)
            } else {
               val playerPosition: Vec3 = player.position()
               val targetDelta: Vec3 = bounceTarget.subtract(playerPosition)
               val targetDirection: Vec3 = targetDelta.normalize()
               val step: Vec3 = targetDirection.scale(0.11)
               event.setHorizontalVelocity(0.0, 0.0)
               val stepZ: Double = step.z
               val stepX: Double = step.x
               event.setHorizontalVelocity(stepX, stepZ)
            }
         } else {
            if (ClientContext.serverStateManager.hasElapsedSinceSetback(200L)) {
               if (!PlayerStateUtils.isHungerLimited()) {
                  if (PlayerStateUtils.isFallFlying()) {
                     val displaySpeed: Double = ClientContext.movementSpeedTracker.displaySpeed
                     if (ClientAccess.collectiveMinecraft.player?.onGround() == true) {
                        val maximumBoost: Int = this.module.maxBoostSetting.value
                        if (displaySpeed < maximumBoost) {
                           if (displaySpeed >= 97.0) {
                              event.setY(0.0)
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   override fun handlePlayerTick(event: LocalPlayerTickEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val canProcessPackets: Boolean = !this.isObstaclePasserActive() || this.queuedPackets.isEmpty()
      if (canProcessPackets && !this.diagonalPacketPending) {

         if (!this.module.isItemUseSlowingFlight()) {
            val packetMode: Boolean = this.module.packetSetting.value
            if (packetMode) {
               val diagonal: Boolean = this.module.diagonalSetting.value
               if (diagonal) {
                  val playerYaw: Float = player.getYRot()

                  val yawSteps: Float = (playerYaw + 1.0f) / FORTY_FIVE_DEGREES

                  var diagonalYaw: Float = (Math.round(yawSteps) * FORTY_FIVE_DEGREES).toFloat()

                  if (diagonalYaw % NINETY_DEGREES == 0.0f) {
                     diagonalYaw += FORTY_FIVE_DEGREES
                  }

                  player.setYRot(diagonalYaw)
               }
            }

            if (player.isFallFlying()) {
               player.avatarState().walkDist = 0.0f
            }

            if (!PlayerStateUtils.isInLiquid(player)) {
               if (!PlayerStateUtils.isInCobweb(player)) {
                  val compensate: Boolean = this.module.compensateSetting.value
                  if (compensate) {
                     if (this.bounceCooldownTicks == 0) {
                        this.flushQueuedPackets()
                     }
                  } else {
                     if (!this.queuedPackets.isEmpty()) {
                        this.flushQueuedPackets()
                     }
                  }

                  return
               }
            }

            this.flushQueuedPackets()
         }
      } else {
         this.flushQueuedPackets()
      }
   }

   override fun handleClientTick(event: MinecraftRunTickEvent) {
      if (!this.isWorldUnavailable()) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
         if (!player.isFallFlying()) {
            if (this.normalFlightRetryTimer.hasElapsedAndReset(10L) && this.bounceInputTicks >= 4) {
               this.module.tryStartNormalFlight()
            }
         }
      }
   }

   override fun handleTravelRotation(event: LivingEntityRotationEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (this.canApplyBounce()) {
         if (!this.isObstaclePasserActive() &&
         !this.diagonalPacketPending) {
            val pitchLocked: Boolean = this.module.pitchLockSetting.value
            if (pitchLocked) {

               if (!this.module.isItemUseSlowingFlight()) {
                  if (this.module.autoPitchSetting.value && !event.isJump) {
                     this.automaticPitch = if (player.getDeltaMovement().y > -0.2) 90.0f else 2.0f
                  }
                  val pitchValue: Float = this.getPitch()
                  val configuredPitch: Float = pitchValue
                  event.pitch = configuredPitch
                  event.cancel()
               }
            }

            return
         }
      }
   }

   override fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
      if (this.diagonalPacketPending) {
         if (event.stage != EventStage.Post) {
            val bounceTarget: Vec3 = this.getBounceTarget()
            val targetRotations: FloatArray = RotationManager.getRotationsToPosition(bounceTarget)
            event.setRotation(targetRotations)
         }
      } else {
         run cooldownUpdate@{
            if (event.stage == EventStage.Pre) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return@cooldownUpdate
               val deltaX: Double = player.getX() - player.xo

               val deltaY: Double = player.getY() - player.yo

               val deltaZ: Double = player.getZ() - player.zo

               val playerPosition: Vec3 = player.position()
               val projectedPosition: Vec3 = playerPosition.add(deltaX, deltaY, deltaZ)
               val collisionLevel: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return@cooldownUpdate
               val collisionPlayer: LocalPlayer = player

               val collisionBox: AABB = AABB.ofSize(projectedPosition, 0.2, 0.2, 0.2)
               val collisionFree: Boolean = collisionLevel.noBlockCollision(collisionPlayer, collisionBox)
               if (collisionFree) {
                  if (!player.onGround()) {
                     if (this.bounceCooldownTicks > 0) {
                        this.bounceCooldownTicks--
                     }
                     return@cooldownUpdate
                  }
               }

               this.bounceCooldownTicks = 2
            }
         }

         if (!this.module.isItemUseSlowingFlight()) {
            if (this.canApplyBounce()) {
               if (!this.isObstaclePasserActive()) {
                  if (event.stage != EventStage.Post) {
                     val pitchLock: Boolean = this.module.pitchLockSetting.value
                     if (pitchLock) {
                        val silent: Boolean = this.module.silentSetting.value
                        if (silent) {
                           val configuredPitch: Float = this.getPitch()
                           val pitch: Float = configuredPitch
                           event.pitch = pitch
                        }
                     }

                     return
                  }
               }
            }
         }
      }
   }

   override fun handleIncomingPacket(event: PacketEvent.Receive) {
      if (event.packet !is ClientboundPlayerCombatKillPacket) {
         if (event.packet !is ClientboundPlayerPositionPacket) {
            return
         }
      }

      val minecraft = ClientAccess.collectiveMinecraft
      val flushTask = Runnable { this.flushQueuedPackets() }
      minecraft.execute(flushTask)
   }

   private fun isObstaclePasserActive(): Boolean {
      return obstaclePasserModule?.isPassingObstacles() == true
   }

   private fun getPitch(): Float {
      return if (this.module.autoPitchSetting.value) this.automaticPitch else this.module.pitchSetting.value
   }

   fun getBounceTarget(): Vec3 {
      val openSide: Direction? = this.getOpenHorizontalSide()
      val localPosition: BlockPos = PlayerStateUtils.getLocalRoundedBlockPosition()
      if (openSide == null) {
         return localPosition.getBottomCenter()
      }
      return localPosition.relative(openSide).getBottomCenter()
   }

   fun getOpenHorizontalSide(): Direction? {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return null
      val playerPosition: BlockPos = player.blockPosition()
      var blockedSides: Int = 0
      var lastOpenSide: Direction? = null
      val horizontal: Plane = Plane.HORIZONTAL
      val sides: Iterator<Direction> = horizontal.iterator()
      while (sides.hasNext()) {
         val side: Direction = sides.next()
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return null
         val adjacentPosition: BlockPos = playerPosition.relative(side)
         val adjacentState: BlockState = level.getBlockState(adjacentPosition)
         if (!adjacentState.canBeReplaced()) {
            blockedSides++
         } else {
            lastOpenSide = side
         }
      }
      return if (blockedSides == 0) null else lastOpenSide
   }

   fun isDiagonalPacketPending(): Boolean {
      return this.diagonalPacketPending
   }

   companion object {
      private var obstaclePasserModule: ObstaclePasserModule? = null

      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
         obstaclePasserModule = registry.getModule(ObstaclePasserModule::class.java) as? ObstaclePasserModule
      }
   }
}
