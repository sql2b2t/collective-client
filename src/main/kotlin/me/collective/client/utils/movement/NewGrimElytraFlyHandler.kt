package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.FireworkRocketEntityTickEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.managers.TickRateController
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.combat.AutoMaceModule
import me.collective.client.modules.movement.ElytraFlyModule
import me.collective.client.settings.Setting
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathConstants.FORTY_FIVE_DEGREES
import me.collective.client.utils.math.MathConstants.NINETY_DEGREES
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.network.PacketSender
import net.minecraft.client.KeyMapping
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.ClientInput
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3

class NewGrimElytraFlyHandler(module: ElytraFlyModule) : ElytraFlyModeHandler(module) {
   private var movementStarted: Boolean = false
   private var negativeYawJitter: Boolean = false
   private var pitchJitterPhase: Boolean = false
   private var serverYaw: Float = 0.0f
   private var serverPitch: Float = 0.0f

   override fun handleFireworkTick(event: FireworkRocketEntityTickEvent) {
      if (autoMaceModule?.isToggled() == true && autoMaceModule?.isFlightControlActive() == true) {
         return
      }

      val yaw: Float = this.serverYaw
      event.setYaw(yaw)
      val pitch: Float = this.serverPitch
      event.setPitch(pitch)
   }

   override fun handleMovement(event: LocalPlayerMoveEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (EntityUtils.hasCollisionBelow(player)) {
         this.movementStarted = false
      }

      if (this.isIdleAirborne()) {
         if (this.hasDurabilityFlightCondition()) {
            val stopMotionSetting: Setting<ElytraFlyModule.StopMotionChoice> = this.module.stopMotionSetting
            if (stopMotionSetting.value == ElytraFlyModule.StopMotionChoice.STATIC) {
               if (autoMaceModule?.isToggled() == true && autoMaceModule?.isFlightControlActive() == true) {
                  return
               }

               val stoppedVelocity: Vec3 = Vec3.ZERO
               event.setVelocity(stoppedVelocity)
            }
         }
      }
   }

   override fun handleClientTick(event: MinecraftRunTickEvent) {
      val moduleForTimerRelease: ElytraFlyModule = this.module
      val timerControllerForRelease: TickRateController = ClientContext.tickRateController
      timerControllerForRelease.releaseMultiplier(moduleForTimerRelease)
      if (autoMaceModule?.isToggled() == true && autoMaceModule?.isFlightControlActive() == true) {
         return
      }

      if (!this.isWorldUnavailable()) {
         val stopMotionSetting: Setting<ElytraFlyModule.StopMotionChoice> = this.module.stopMotionSetting
         if (stopMotionSetting.value == ElytraFlyModule.StopMotionChoice.TIMER) {
            if (this.hasDurabilityFlightCondition()) {
               this.movementStarted = true

               if (this.isIdleAirborne()) {
                  val timerControllerForRequest: TickRateController = ClientContext.tickRateController
                  val moduleForTimerRequest: ElytraFlyModule = this.module

                  timerControllerForRequest.requestMultiplier(moduleForTimerRequest, 0.02f)
               }

               return
            }
         }
      }
   }

   override fun handleRotationManagerEvent(event: RotationRequestEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      if (!EntityUtils.hasCollisionBelow(player)) {
         if (autoMaceModule?.isToggled() == true && autoMaceModule?.isFlightControlActive() == true) {
            return
         }
         if (PlayerStateUtils.isLocalWearingElytra()) {
            val durabilityChoice: ElytraFlyModule.InfDurabilityChoice = this.module.infDurabilitySetting.value
            if (durabilityChoice.isDisabled()) {
               if (!PlayerStateUtils.isFallFlying()) {
                  player.startFallFlying()

                  PacketSender.sendStartFallFlying()
               }
            }
         }
         if (this.hasDurabilityFlightCondition()) {
            val playerYaw: Float = player.getYRot()
            val movementInput: ClientInput = player.input
            val horizontalMotion: DoubleArray = MovementPhysics.getHorizontalMotion(
               playerYaw, movementInput, 1.0
            )
            val directionZ: Double = horizontalMotion[1]
            val directionX: Double = horizontalMotion[0]
            val directionRadians: Double = Math.atan2(directionZ, directionX)
            var yaw: Float = (Math.toDegrees(directionRadians)
               - NINETY_DEGREES).toFloat()

            if (!MovementPhysics.isLocalPlayerMoving()) {
               yaw = playerYaw
            }
            yaw += (if (this.negativeYawJitter) -1 else 1) * AntiCheatModule.ROTATION_JITTER_STEP

            this.negativeYawJitter = !this.negativeYawJitter

            var pitch: Float = -1.0f
            if (player.input.keyPresses.jump()) {
               pitch = if (MovementPhysics.isLocalPlayerMoving())
                  (-FORTY_FIVE_DEGREES).toFloat()
               else
                  (-NINETY_DEGREES).toFloat()

               if (!MovementPhysics.isLocalPlayerMoving()) {
                  val stationaryJumpYaw: Float = yaw + 180.0f
                  yaw = Mth.wrapDegrees(stationaryJumpYaw)
               }
            }

            if (player.input.keyPresses.shift()) {
               val responsiveDescend: Boolean = this.module.responsiveDescendSetting.value
               if (responsiveDescend) {
                  val responsiveDescendPitch: Float
                  if (MovementPhysics.isLocalPlayerMoving()) {
                     responsiveDescendPitch = player.getXRot()
                  } else {
                     responsiveDescendPitch = NINETY_DEGREES.toFloat()
                  }

                  pitch = responsiveDescendPitch
               } else {
                  pitch = if (MovementPhysics.isLocalPlayerMoving())
                     FORTY_FIVE_DEGREES.toFloat()
                  else
                     NINETY_DEGREES.toFloat()
               }
            }

            val stopMotionSetting: Setting<ElytraFlyModule.StopMotionChoice> = this.module.stopMotionSetting
            if (stopMotionSetting.value == ElytraFlyModule.StopMotionChoice.MOTION) {
               if (this.isIdleAirborne()) {
                  if (this.pitchJitterPhase) {
                     val jitteredYaw: Float = yaw + 180.0f
                     yaw = Mth.wrapDegrees(jitteredYaw)
                  }

                  this.pitchJitterPhase = !this.pitchJitterPhase
               }
            }

            this.serverYaw = yaw
            this.serverPitch = pitch
            val rotationManagerForRequest: RotationManager = ClientContext.rotationManager

            val rotation: FloatArray = floatArrayOf(yaw, pitch)

            rotationManagerForRequest.requestRotation(rotation, 999999, true)
            ClientContext.rotationManager.forceRotationOverride()
         }
      }
   }

   private fun isIdleAirborne(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
      val movementInput: Input = player.input.keyPresses
      val inputRequested: Boolean = MovementPhysics.isLocalPlayerMoving() ||
         movementInput.jump() || movementInput.shift() || this.isMovementKeyDown()
      return !EntityUtils.hasCollisionBelow(player) && !inputRequested
   }

   fun shouldFallWithChestplate(): Boolean {
      if (!this.module.isToggled() || !this.movementStarted) {
         return false
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
      if (!player.input.keyPresses.shift()) {
         return false
      }
      val fallChestplateEnabled: Boolean = this.module.fallChestplateSetting.value
      if (!fallChestplateEnabled) {
         return false
      }
      var nearestPlayerDistance: Float = 999.0f
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return false
      val players: Iterator<AbstractClientPlayer> = level.players().iterator()
      while (players.hasNext()) {
         val otherPlayer: AbstractClientPlayer = players.next()
         if (otherPlayer === player) {
            continue
         }
         val socialRegistry: SocialRegistry = ClientContext.socialRegistry
         if (socialRegistry.isFriendPlayer(otherPlayer)) {
            continue
         }
         var otherPlayerY: Double = otherPlayer.getY()
         val playerForHeightCheck: LocalPlayer = player
         if (otherPlayerY > playerForHeightCheck.getY()) {
            continue
         }
         otherPlayerY = otherPlayer.getY()
         val playerForHeightDifference: LocalPlayer = player
         val heightDifference: Double = otherPlayerY - playerForHeightDifference.getY()
         val verticalDistance: Double = Math.abs(heightDifference)
         if (verticalDistance > 15.0) {
            continue
         }
         val otherPosition: Vec3 = otherPlayer.position()
         val localPosition: Vec3 = player.position()
         val horizontalDistance: Float = Vec3Utils.horizontalDistance(otherPosition, localPosition)
         nearestPlayerDistance = Math.min(nearestPlayerDistance, horizontalDistance)
      }

      return nearestPlayerDistance < 5.0f
   }

   private fun hasDurabilityFlightCondition(): Boolean {
      val durabilityChoice: ElytraFlyModule.InfDurabilityChoice = this.module.infDurabilitySetting.value
      if (durabilityChoice.isDisabled()) {
         return PlayerStateUtils.isFallFlying()
      } else {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return false
         return !EntityUtils.hasCollisionBelow(player)
      }
   }

   private fun isMovementKeyDown(): Boolean {
      val forwardKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyUp
      val backwardKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyDown
      val rightKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyRight
      val leftKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyLeft
      val sneakKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyShift
      val jumpKey: KeyMapping = ClientAccess.collectiveMinecraft.options.keyJump
      return forwardKey.isDown() || backwardKey.isDown() || rightKey.isDown() ||
         leftKey.isDown() || sneakKey.isDown() || jumpKey.isDown()
   }

   companion object {
      private var autoMaceModule: AutoMaceModule? = null

      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
         autoMaceModule = registry.getModule(AutoMaceModule::class.java) as AutoMaceModule
      }
   }
}
