package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.TickRateController
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathConstants.NINETY_DEGREES
import me.collective.client.utils.network.PacketSender
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3

class BoostElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {
   private val takeoffGraceTimer: ElapsedTimer = ElapsedTimer()
   private var boostPitchInitialized: Boolean = false
   private var targetPitch: Float = 0.0f
   private var movementYaw: Float = 0.0f

   private fun computeMovementYaw(): Float {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return 0.0f
      if (MovementPhysics.isLocalPlayerMoving()) {
         if (this.module.responsiveSetting.value) {
            val yaw: Float = player.getYRot()
            return MovementPhysics.getMovementYaw(yaw)
         }
      }
      return player.getYRot()
   }

   override fun handleMovement(event: LocalPlayerMoveEvent) {
      var limit: Float = this.module.limitSetting.value
      if (limit > 200.0f) {
         if (this.isEarlyNcpBoost()) {
            limit = 200.0f
         }
      }
      if (this.module.limitSetting.isAtMaximum()) {
         if (!this.isEarlyNcpBoost()) {
            return
         }
      }
      val velocity: Vec3 = event.velocity
      val velocityX: Double = velocity.x()
      val velocityZ: Double = velocity.z()
      val horizontalSpeed: Double = Math.hypot(velocityX, velocityZ)
      val blocksPerSecond: Double = horizontalSpeed * 20.0
      if (!(blocksPerSecond * 3.6 <= limit)) {
         val limitedVelocity: Vec3 = event.velocity
         var direction: Vec3 = limitedVelocity.normalize()
         val limitAsDouble: Double = limit.toDouble()
         val metersPerSecond: Double = limitAsDouble / 3.6
         val blocksPerTick: Double = metersPerSecond / 20.0
         direction = direction.scale(blocksPerTick)
         val limitedX: Double = direction.x()
         val limitedZ: Double = direction.z()
         event.setHorizontalVelocity(limitedX, limitedZ)
      }
   }

   override fun handlePrePlayerTick(event: LocalPlayerPreTickEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (this.module.autoJumpSetting.value) {
         if (player.isFallFlying()) {
            ClientContext.inputOverrides.requestJump(true)
         }
      }
      this.movementYaw = this.computeMovementYaw()
      this.boostPitchInitialized = false
      var motionPlayer: LocalPlayer = player
      if (!PlayerStateUtils.isInWater(motionPlayer)) {
         if (antiCheatModule!!.is2b2tEnabled()) {
            if (player.isUsingItem()) {
               return
            }
         }
         val input: Input = player.input.keyPresses
         var takeoffRequested: Boolean = input.jump() &&
         this.module.takeoffSetting.value != ElytraFlyModule.TakeoffChoice.NONE
         if (this.module.takeoffSetting.value == ElytraFlyModule.TakeoffChoice.STRICT) {
            var strictTakeoffRequested: Boolean
            if (!player.onGround()) {
               val velocity: Vec3 = player.getDeltaMovement()
               val verticalSpeed: Double = velocity.y()
               strictTakeoffRequested = verticalSpeed < 0.1
            } else {
               strictTakeoffRequested = false
            }
            takeoffRequested = strictTakeoffRequested
         } else {
            val moduleForTickRate: ElytraFlyModule = this.module
            val tickRateController: TickRateController = ClientContext.tickRateController
            tickRateController.releaseMultiplier(moduleForTickRate)
         }
         if (player.isFallFlying()) {
            this.takeoffGraceTimer.reset()
         }
         run takeoffWindow@{
            if (!takeoffRequested) {
               if (this.takeoffGraceTimer.hasElapsed(1500L)) {
                  return@takeoffWindow
               }
            }
            if (!player.isFallFlying()) {
               if (!EntityUtils.hasCollisionBelow(player)) {
                  if (this.module.isElytraEquipped()) {
                     PacketSender.sendStartFallFlying()
                     player.startFallFlying()
                  }
               }
            }
         }
         if (this.module.isFallFlyingWithoutOverride()) {
            val movementYawRadians: Float = Math.toRadians(this.movementYaw.toDouble()).toFloat()
            val minimumBoost: Float = this.module.minBoostSetting.value
            var boostMagnitude: Float = minimumBoost * 0.1f
            if (this.module.minBoostSetting.isAtMaximum()) {
               if (antiCheatModule!!.is2b2tEnabled()) {
                  boostMagnitude = 0.0205f
                  if (this.targetPitch <= 0.0f) {
                     val setbackTimer: ElapsedTimer = ClientContext.serverStateManager.getSetbackTimer()
                     val setbackWindowSeconds: Double = 10.0
                     val seconds: TimeUnit = TimeUnit.SECONDS
                     if (!setbackTimer.hasElapsed(setbackWindowSeconds, seconds)) {
                        boostMagnitude = 0.008f
                     }
                  }
               }
            }
            if (this.isEarlyNcpBoost()) {
               boostMagnitude = 3.0f
            }
            if (this.module.nCPBoostSetting.value) {
               this.updateBoostPitch()
            }
            if (!this.boostPitchInitialized) {
               if (this.module.inLavaSetting.value) {
                  if (player.isInLava()) {
                     val lavaBoostScale: Float = 0.2f
                     val configuredSpeed: Float = this.module.speedSetting.value
                     boostMagnitude = lavaBoostScale * configuredSpeed
                  }
               }
               run forwardBoost@{
                  if (this.module.factorizeSetting.value) {
                     if (this.targetPitch > 0.0f) {
                        return@forwardBoost
                     }
                  } else {
                     if (ClientAccess.collectiveMinecraft.options.keyUp.isDown()) {
                        return@forwardBoost
                     }
                     if (this.module.alwaysSetting.value) {
                        return@forwardBoost
                     }
                  }
                  if (ClientAccess.collectiveMinecraft.options.keyDown.isDown()) {
                     motionPlayer = player
                     val backwardYawForSine: Double = movementYawRadians.toDouble()
                     val backwardX: Double = (Mth.sin(backwardYawForSine) * boostMagnitude).toDouble()
                     val backwardYawForCosine: Double = movementYawRadians.toDouble()
                     val backwardZ: Double = (Mth.cos(backwardYawForCosine) * -boostMagnitude).toDouble()
                     val backwardY: Double = 0.0
                     motionPlayer.push(backwardX, backwardY, backwardZ)
                  }
                  return
               }
               motionPlayer = player
               val forwardYawForSine: Double = movementYawRadians.toDouble()
               val forwardX: Double = (Mth.sin(forwardYawForSine) * -boostMagnitude).toDouble()
               val forwardYawForCosine: Double = movementYawRadians.toDouble()
               val forwardZ: Double = (Mth.cos(forwardYawForCosine) * boostMagnitude).toDouble()
               val forwardY: Double = 0.0
               motionPlayer.push(forwardX, forwardY, forwardZ)
            }
         }
      }
   }

   override fun handleClientTick(event: MinecraftRunTickEvent) {
      if (this.module.nCPBoostSetting.value) {
         return
      }
      if (!this.isWorldUnavailable()) {
         if (this.module.isFallFlyingWithoutOverride()) {
            if (event.hasPendingTicks) {
               this.updateBoostPitch()
            }
         }
      }
   }

   private fun isEarlyNcpBoost(): Boolean {
      if (this.module.nCPBoostSetting.value) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         val fallFlyingTicks: Int = player.getFallFlyingTicks()
         if (fallFlyingTicks <= 5) {
            return true
         }
      }
      return false
   }

   private fun updateBoostPitch() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val random: ThreadLocalRandom = ThreadLocalRandom.current()
      val randomSample: Int = random.nextInt(6)
      val pitchJitter: Float
      if (RotationManager.isMovementSyncEnabled()) {
         val jitterStep: Float = AntiCheatModule.ROTATION_JITTER_STEP
         pitchJitter = jitterStep * (-3 + randomSample)
      } else {
         pitchJitter = 0.0f
      }
      if (MovementPhysics.isLocalPlayerMoving()) {
         val rotationForMovement: RotationManager = ClientContext.rotationManager
         val movementRotation = FloatArray(2)
         movementRotation[0] = this.movementYaw
         val playerForMovementPitch: LocalPlayer = player
         movementRotation[1] = playerForMovementPitch.getXRot()
         rotationForMovement.requestRotation(movementRotation, 10000, true)
         ClientContext.rotationManager.forceRotationOverride()
      }
      if (!RotationManager.isMovementSyncEnabled()) {
         this.targetPitch = player.getXRot()
      } else {
         val previousTargetPitch: Float = this.targetPitch
         val playerForPitchDifference: LocalPlayer = player
         val pitchDifference: Float = previousTargetPitch - playerForPitchDifference.getXRot()
         val absolutePitchDifference: Float = Math.abs(pitchDifference)
         if (absolutePitchDifference < 15.0f) {
            this.targetPitch = player.getXRot()
         } else {
            val targetPitchForDirection: Float = this.targetPitch
            val playerForPitchDirection: LocalPlayer = player
            if (targetPitchForDirection > playerForPitchDirection.getXRot()) {
               val targetBeforeDecrease: Float = this.targetPitch
               this.targetPitch = targetBeforeDecrease - 10.0f
            } else {
               val targetBeforeIncrease: Float = this.targetPitch
               this.targetPitch = targetBeforeIncrease + 10.0f
            }
         }
      }
      if (this.module.responsiveSetting.value) {
         var desiredPitch: Float = this.targetPitch + pitchJitter
         if (player.input.keyPresses.jump()) {
            val jumpPitch: Float
            if (RotationManager.isMovementSyncEnabled()) {
               jumpPitch = -45.0f
            } else {
               jumpPitch = -60.0f
            }
            desiredPitch = jumpPitch + pitchJitter
         } else {
            if (player.input.keyPresses.shift()) {
               desiredPitch = 60.0f + pitchJitter
            }
         }
         desiredPitch = Mth.clamp(
            desiredPitch,
            (-NINETY_DEGREES).toFloat(),
            NINETY_DEGREES.toFloat()
         )
         if (player.getXRot() != desiredPitch) {
            val rotationForPitch: RotationManager = ClientContext.rotationManager
            val pitchRotation = FloatArray(2)
            pitchRotation[0] = this.movementYaw
            pitchRotation[1] = desiredPitch
            rotationForPitch.requestRotation(pitchRotation, 1000001, true)
            ClientContext.rotationManager.forceRotationOverride()
            this.targetPitch = desiredPitch
         }
      }
      run boostWindow@{
         if (this.targetPitch < 0.0f) {
            if (this.module.nCPBoostSetting.value) {
               var flightTicks: Int = player.getFallFlyingTicks()
               if (this.module.isDurabilityBypassActive()) {
                  flightTicks = player.tickCount
               }
               val boostPhase: Int = flightTicks % 6
               if (boostPhase > 2) {
                  return@boostWindow
               }
               if (flightTicks >= 1) {
                  if (flightTicks < 20) {
                     return@boostWindow
                  }
               }
               val rotationForBoost: RotationManager = ClientContext.rotationManager
               val boostRotation = FloatArray(2)
               boostRotation[0] = this.movementYaw
               boostRotation[1] = 0.0f
               rotationForBoost.requestRotation(boostRotation, 100002, true)
               ClientContext.rotationManager.forceRotationOverride()
            }
         }
         return
      }
      this.boostPitchInitialized = true
   }

   override fun handleIncomingPacket(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundSetHealthPacket) {
         val healthPacket = event.packet as ClientboundSetHealthPacket
         var healthAndSaturation: Float = healthPacket.getHealth()
         healthAndSaturation += healthPacket.getSaturation()
         if (healthAndSaturation < EntityUtils.getPlayerHealthWithAbsorption()) {
            this.onDisable()
            this.onEnable()
         }
      }
   }

   companion object {
      private var antiCheatModule: AntiCheatModule? = null

      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
         antiCheatModule = registry.getModule(
            AntiCheatModule::class.java
         ) as AntiCheatModule
      }
   }
}
