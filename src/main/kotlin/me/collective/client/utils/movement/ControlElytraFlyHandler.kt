package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventStage
import me.collective.client.events.LivingEntityRotationEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.combat.AutoMaceModule
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.modules.movement.ObstaclePasserModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.InputUtils
import me.collective.client.utils.math.MathConstants.FORTY_FIVE_DEGREES
import me.collective.client.utils.math.MathConstants.NINETY_DEGREES
import me.collective.client.utils.math.MathUtils
import me.collective.mixin.ServerboundMovePlayerPacketAccessor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Direction.Axis
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3

class ControlElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {
   private var savedVelocity: Vec3 = Vec3.ZERO
   private var verticalInputTicks: Int = 0
   private var flightSpeed: Double = 0.0
   private var accelerationStartTimeMillis: Long = 0
   private var verticalAccelerationStartTimeMillis: Long = 0
   private var verticalVelocityApplied: Boolean = false
   private var verticalInputOverride: Boolean = false
   private var verticalInputDirection: Int = 0

   override fun onEnable() {
      this.flightSpeed = 0.0
      val accelerationStart = System.currentTimeMillis()
      this.verticalAccelerationStartTimeMillis = accelerationStart
      this.accelerationStartTimeMillis = accelerationStart
   }

   fun setVerticalInputOverride(direction: Int) {
      this.verticalInputOverride = true
      this.verticalInputDirection = direction
   }

   private fun computeFlightPitch(): Float {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return 0.0f
      var pitch: Float = player.getXRot()
      if (this.isVerticalInputRequested()) {
         pitch = NINETY_DEGREES.toFloat()
         if (MovementPhysics.isLocalPlayerMoving()) {
            pitch = FORTY_FIVE_DEGREES.toFloat()
         }
         if (player.input.keyPresses.jump()) {
            pitch = -pitch
         }
      }
      return pitch
   }

   override fun handleMovement(event: LocalPlayerMoveEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!this.module.isFallFlyingWithoutOverride()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
         return
      }
      if (this.module.isManualVerticalControlActive()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
         return
      }
      if (!this.isFireworkMovementAllowed()) {
         return
      }
      if (this.isObstaclePasserPathActive()) {
         return
      }
      if (!MovementPhysics.isLocalPlayerMoving()) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
      }
      var verticalSpeed: Float = this.module.vSpeedSetting.value
      if (this.module.accelerateSetting.value) {
         val targetHorizontalSpeed: Double = this.module.flightSpeedSetting.value.toDouble()
         val minimumHorizontalSpeed: Double = this.module.accelMinSetting.value.toDouble()
         val horizontalAccelerationDuration: Double = this.module.accelTimeSetting.value.toDouble()
         val horizontalAccelerationStart: Long = this.accelerationStartTimeMillis
         this.flightSpeed = MovementPhysics.accelerateTo(
            targetHorizontalSpeed, minimumHorizontalSpeed, horizontalAccelerationDuration, horizontalAccelerationStart
         ).toFloat().toDouble()
         val targetVerticalSpeed: Double = verticalSpeed.toDouble()
         val minimumVerticalSpeed: Double = this.module.verAccelMinSetting.value.toDouble()
         val verticalAccelerationDuration: Double = this.module.accelTimeSetting.value.toDouble()
         val verticalAccelerationStart: Long = this.verticalAccelerationStartTimeMillis
         verticalSpeed = MovementPhysics.accelerateTo(
            targetVerticalSpeed, minimumVerticalSpeed, verticalAccelerationDuration, verticalAccelerationStart
         ).toFloat()
      } else {
         this.flightSpeed = this.module.flightSpeedSetting.value.toDouble()
      }
      this.flightSpeed += fireworksModule!!.getAdditionalBoost(false)
      if (this.verticalInputTicks == 0) {
         if (player.input.keyPresses.jump()) {
            if (this.module.verticalSetting.value == ElytraFlyModule.VerticalChoice.STRICT) {
               val speedBeforeLimit: Double = this.flightSpeed
               if (speedBeforeLimit >= 2.0) {
                  this.flightSpeed = 2.0
               }
            }
         }
         val horizontalSpeed: Double = this.flightSpeed
         var horizontalMotion: DoubleArray = MovementPhysics.applyHorizontalMotion(
            event, horizontalSpeed
         )
         if (autoMaceModule!!.isToggled()) {
            if (autoMaceModule!!.isFlightControlActive()) {
               val maceRotation: FloatArray = autoMaceModule!!.getFlightRotation()
               val maceSpeedMultiplier: Float
               if (maceRotation[1] >= 0.0f) {
                  maceSpeedMultiplier = 1.0f
               } else {
                  maceSpeedMultiplier = 1.0f - (-maceRotation[1]) / 90.0f
               }
               val maceYaw: Float = maceRotation[0]
               val maceMovementInput = InputUtils.createInput()
               val maceRequestedSpeed: Double = this.flightSpeed * maceSpeedMultiplier
               val maceSpeedCap: Double = 1.0
               val maceLimitedSpeed: Double = Math.min(maceRequestedSpeed, maceSpeedCap)
               horizontalMotion = MovementPhysics.getHorizontalMotion(
                  maceYaw, maceMovementInput, maceLimitedSpeed
               )
               val maceMotionX: Double = horizontalMotion[0]
               val maceMotionZ: Double = horizontalMotion[1]
               event.setHorizontalVelocity(maceMotionX, maceMotionZ)
            }
         }
         player.setDeltaMovement(
            horizontalMotion[0], player.getDeltaMovement().y, horizontalMotion[1]
         )
         this.savedVelocity = player.getDeltaMovement()
         val playerTickCount: Int = player.tickCount
         if (playerTickCount % 4 == 0) {
            val motionBeforeDescent: Double = event.y
            val descendingMotionY: Double = motionBeforeDescent - 0.0001
            event.setY(descendingMotionY)
            player.setDeltaMovement(
               player.getDeltaMovement().add(0.0, -0.0001, 0.0)
            )
         }
      }
      var jumping: Boolean = player.input.keyPresses.jump()
      var sneaking: Boolean = player.input.keyPresses.shift()
      if (this.verticalInputOverride) {
         jumping = this.verticalInputDirection > 0
         sneaking = this.verticalInputDirection < 0
         this.verticalInputOverride = false
      }
      if (jumping) {
         if (this.module.isFallFlyingWithoutOverride()) {
            if (this.module.verticalSetting.value == ElytraFlyModule.VerticalChoice.STRICT) {
               this.verticalInputTicks++
            }
            if (this.module.verticalSetting.value == ElytraFlyModule.VerticalChoice.PLAIN) {
               val ascendingMotionY: Double = verticalSpeed.toDouble()
               event.setY(ascendingMotionY)
               player.setDeltaMovement(
                  player.getDeltaMovement().with(Axis.Y, verticalSpeed.toDouble())
               )
            } else {
               if (this.module.verticalSetting.value == ElytraFlyModule.VerticalChoice.STRICT) {
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     this.applyVerticalPitchVelocity()
                  }
                  return
               }
            }
            return
         }
      }
      if (sneaking) {
         this.verticalInputTicks = 0
         val descendingMotionY: Double = (-verticalSpeed).toDouble()
         event.setY(descendingMotionY)
         player.setDeltaMovement(
            player.getDeltaMovement().with(Axis.Y, (-verticalSpeed).toDouble())
         )
      } else {
         this.verticalInputTicks = 0
         val zeroMotionY: Double = 0.0
         event.setY(zeroMotionY)
         player.setDeltaMovement(
            player.getDeltaMovement().with(Axis.Y, 0.0)
         )
         if (!player.verticalCollision) {
            val configuredGlide: Float = this.module.glideSetting.value
            if (configuredGlide != 0.0f) {
               player.setDeltaMovement(
                  player.getDeltaMovement().add(0.0, (-this.module.glideSetting.value).toDouble(), 0.0)
               )
               val glidingMotionY: Double = player.getDeltaMovement().y
               event.setY(glidingMotionY)
            }
         }
      }
      return
   }

   override fun handleOutgoingPacket(
      event: PacketEvent.Send
   ) {
      if (!this.isObstaclePasserPathActive()) {
         if (event.packet is ServerboundMovePlayerPacket) {
            val movementPacket = event.packet as ServerboundMovePlayerPacket
            if (this.module.isFallFlyingWithoutOverride()) {
               if (this.module.isManualVerticalControlActive()) {
                  return
               }
               if (!this.isFireworkMovementAllowed()) {
                  return
               }
               if (this.isVerticalInputRequested()) {
                  val verticalPacket = movementPacket as ServerboundMovePlayerPacketAccessor
                  verticalPacket.setPitch(this.computeFlightPitch())
                  return
               }
               if (!MovementPhysics.isLocalPlayerMoving()) {
                  return
               }
               if (this.module.spoofPitchSetting.value) {
                  val spoofedPacket = movementPacket as ServerboundMovePlayerPacketAccessor
                  val spoofedPitch: Float = this.module.spoofedPitchSetting.value
                  spoofedPacket.setPitch(spoofedPitch)
               }
               if (this.verticalInputTicks > 0) {
                  val ascendingPacket = movementPacket as ServerboundMovePlayerPacketAccessor
                  val ascendingPitch: Float = this.module.vPitchSetting.value
                  ascendingPacket.setPitch(-ascendingPitch)
               }
            }
         }
      }
   }

   override fun handlePrePlayerTick(event: LocalPlayerPreTickEvent) {
      if (!this.isObstaclePasserPathActive()) {
         if (this.module.isManualVerticalControlActive()) {
            ClientContext.inputOverrides.requestJump(true)
            ClientContext.inputOverrides.requestForward(true)
            ClientContext.inputOverrides.requestForwardMovement(1.0f)
            return
         }
      }
   }

   override fun handlePlayerTick(event: LocalPlayerTickEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!this.module.isFallFlyingWithoutOverride()) {
         this.verticalAccelerationStartTimeMillis = System.currentTimeMillis()
      }
      if (!player.input.keyPresses.jump()) {
         if (!player.input.keyPresses.shift()) {
            this.verticalAccelerationStartTimeMillis = System.currentTimeMillis()
         }
      }
      if (player.input.keyPresses.jump()) {
         if (this.module.isFallFlyingWithoutOverride()) {
            if (!this.verticalVelocityApplied) {
               if (this.module.verticalSetting.value == ElytraFlyModule.VerticalChoice.MANUAL) {
                  val yaw: Float = player.getYRot()
                  val forwardInput = InputUtils.createInput()
                  val horizontalSpeed: Double = this.flightSpeed
                  val motion: DoubleArray = MovementPhysics.getHorizontalMotion(
                     yaw, forwardInput, horizontalSpeed
                  )
                  player.push(motion[0], 0.0, motion[1])
               }
            }
            this.verticalVelocityApplied = true
            return
         }
      }
      this.verticalVelocityApplied = false
   }

   override fun handleTravelRotation(event: LivingEntityRotationEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (this.module.isFallFlyingWithoutOverride()) {
         if (!this.module.isManualVerticalControlActive()) {
            if (this.isFireworkMovementAllowed()) {
               if (this.isObstaclePasserPathActive()) {
                  return
               }
               if (this.isVerticalInputRequested()) {
                  val pitch: Float = this.computeFlightPitch()
                  event.pitch = pitch
               }
               if (!MovementPhysics.isLocalPlayerMoving()) {
                  return
               }
               val yaw: Float = MovementPhysics.getMovementYaw(player.getYRot())
               event.yaw = yaw
               return
            }
         }
      }
   }

   override fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (this.module.isFallFlyingWithoutOverride()) {
         if (!this.module.isManualVerticalControlActive()) {
            if (this.isFireworkMovementAllowed()) {
               if (event.stage == EventStage.Pre) {
                  if (this.isObstaclePasserPathActive()) {
                     return
                  }
                  if (this.isVerticalInputRequested()) {
                     val pitch: Float = this.computeFlightPitch()
                     event.pitch = pitch
                     return
                  }
                  if (!MovementPhysics.isLocalPlayerMoving()) {
                     return
                  }
                  val yaw: Float = MovementPhysics.getMovementYaw(player.getYRot())
                  event.yaw = yaw
                  if (this.module.spoofPitchSetting.value) {
                     val spoofedPitch: Float = this.module.spoofedPitchSetting.value
                     event.pitch = spoofedPitch
                  }
                  if (this.verticalInputTicks > 0) {
                     val configuredVerticalPitch: Float = this.module.vPitchSetting.value
                     val verticalPitch: Float = -configuredVerticalPitch
                     event.pitch = verticalPitch
                  }
                  return
               }
            }
         }
      }
   }

   override fun handleIncomingPacket(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.accelerationStartTimeMillis = System.currentTimeMillis()
      }
   }

   private fun isFireworkMovementAllowed(): Boolean {
      if (this.module.isFallFlyingWithoutOverride()) {
         if (fireworksModule!!.autoLaunchSetting.value) {
            if (fireworksModule!!.isToggled()) {
               val rocketUseTimer: ElapsedTimer = fireworksModule!!.rocketUseTimer
               if (!rocketUseTimer.hasElapsed(500L)) {
                  return true
               }
               return fireworksModule!!.isRocketBoostActive()
            }
         }
      }
      return true
   }

   private fun isObstaclePasserPathActive(): Boolean {
      if (obstaclePasserModule == null) {
         return false
      }
      return obstaclePasserModule!!.isPathActive()
   }

   private fun isVerticalInputRequested(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
      if (player.input.keyPresses.jump()) {
         return true
      }
      return player.input.keyPresses.shift() &&
         this.module.verticalSetting.value == ElytraFlyModule.VerticalChoice.PLAIN &&
         RotationManager.isMovementSyncEnabled()
   }

   private fun applyVerticalPitchVelocity() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      val anglePitch: Float = this.module.vPitchSetting.value
      val negativeAnglePitch: Float = -anglePitch
      val pitchRadians: Double = MathUtils.toRadians(negativeAnglePitch).toDouble()
      var velocity: Vec3 = this.savedVelocity
      val directionPitch: Float = this.module.vPitchSetting.value
      val negativeDirectionPitch: Float = -directionPitch
      val yaw: Float = player.getYRot()
      val lookDirection: Vec3 = MathUtils.directionFromPitchYaw(negativeDirectionPitch, yaw)
      val horizontalSpeed: Double = velocity.horizontalDistance()
      val horizontalLookLength: Double = lookDirection.horizontalDistance()
      val lookLength: Double = lookDirection.length()
      val pitchCosine: Double = Math.cos(pitchRadians)
      val squaredPitchCosine: Double = pitchCosine * pitchCosine
      val lookLengthScale: Double = lookLength / 0.4
      val lift: Double = squaredPitchCosine * Math.min(1.0, lookLengthScale)
      val gravity: Double = 0.08 * (-1.0 + lift * 0.75)
      velocity = velocity.add(0.0, gravity, 0.0)
      if (pitchRadians < 0.0 && horizontalLookLength > 0.0) {
         val upwardProjection: Double = horizontalSpeed * -Math.sin(pitchRadians)
         val climbAcceleration: Double = upwardProjection * 0.04
         val accelerationX: Double = -lookDirection.x * climbAcceleration / horizontalLookLength
         val accelerationY: Double = climbAcceleration * 3.2
         val accelerationZ: Double = -lookDirection.z * climbAcceleration / horizontalLookLength
         velocity = velocity.add(accelerationX, accelerationY, accelerationZ)
      }
      val playerForVelocity: LocalPlayer = player
      val horizontalDrag: Double = 0.99
      val verticalDrag: Double = 0.98
      val depthDrag: Double = 0.99
      val slowedVelocity: Vec3 = velocity.multiply(horizontalDrag, verticalDrag, depthDrag)
      playerForVelocity.setDeltaMovement(slowedVelocity)
      this.savedVelocity = player.getDeltaMovement()
   }

   companion object {
      private var obstaclePasserModule: ObstaclePasserModule? = null
      private var fireworksModule: FireworksModule? = null
      private var autoMaceModule: AutoMaceModule? = null

      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
         obstaclePasserModule = registry.getModule(
            ObstaclePasserModule::class.java
         ) as ObstaclePasserModule
         fireworksModule = registry.getModule(
            FireworksModule::class.java
         ) as FireworksModule
         autoMaceModule = registry.getModule(
            AutoMaceModule::class.java
         ) as AutoMaceModule
      }
   }
}
