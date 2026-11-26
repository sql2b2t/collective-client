package me.collective.client.modules.player


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.managers.RotationManager
import me.collective.client.render.RenderUtils
import me.collective.client.managers.SocialRegistry
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.render.FreeLookModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.Comparator
import java.util.Optional
import java.util.function.Predicate
import java.util.stream.Stream
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.Vec3

class RotationLockModule : Module("RotationLock", "Locks your rotation.", ModuleCategory.PLAYER) {
   private val modeSetting: Setting<ModeChoice>
   private val silentSetting: Setting<Boolean>
   @JvmField
   val yawSetting: Setting<Boolean>
   @JvmField
   val customYawSetting: Setting<Boolean>
   @JvmField
   val yawValueSetting: Setting<Float>
   val pitchSetting: Setting<Boolean>
   val pitchValueSetting: Setting<Float>
   private val rangeSetting: Setting<Float>
   private val disableRangeSetting: Setting<Float>
   private val plainRotationProvider: PlainRotationProvider
   private val targetRotationProvider: TargetRotationProvider
   private var targetName: String? = null

   override fun onEnable() {
      if (this.modeSetting.value != ModeChoice.PLAIN) {
         this.selectTarget()
      }
   }

   fun calculateYaw(currentYaw: Float): Float {
      val useCustomYaw: Boolean = this.customYawSetting.value
      return if (!useCustomYaw) {
         val quotient: Float = (currentYaw + 1.0f) /
         MathConstants.FORTY_FIVE_DEGREES
         (Math.round(quotient) * MathConstants.FORTY_FIVE_DEGREES).toFloat()
      } else {
         this.yawValueSetting.value
      }
   }

   private fun selectTarget() {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level ?: return
      val candidates: Stream<AbstractClientPlayer> = level.players().stream()
         .filter { candidate: AbstractClientPlayer ->
            if (ClientAccess.collectiveMinecraft.player!! !== candidate) {
               val friends: SocialRegistry = ClientContext.socialRegistry
               !friends.isFriendPlayer(candidate)
            } else {
               false
            }
         }
         .filter { candidate: AbstractClientPlayer ->
            if (!candidate.isDeadOrDying) {
               candidate.isAlive
            } else {
               false
            }
         }
         .filter { candidate: AbstractClientPlayer ->
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val eyePosition: Vec3 = player.eyePosition
            val candidatePosition: Vec3 = candidate.position()
            val distance: Double = eyePosition.distanceTo(candidatePosition)
            val maximumDistance: Float = this.rangeSetting.value
            distance <= maximumDistance.toDouble()
         }
      val target: AbstractClientPlayer? = candidates
         .min(Comparator.comparingDouble<AbstractClientPlayer> { candidate: AbstractClientPlayer -> this.getTargetYawDistance(candidate).toDouble() })
         .orElse(null)
      val selectedName: String? = if (target == null) {
         null
      } else {
         target.gameProfile.name()
      }
      this.targetName = selectedName
   }

   private fun getInterpolatedEyePosition(entity: Entity): Vec3 {
      val partialTick: Float = RenderUtils.getPartialTick()
      val position: Vec3 = entity.getPosition(partialTick)
      val pose: Pose = entity.pose
      val eyeHeight: Double = entity.getEyeHeight(pose).toDouble()
      return position.add(0.0, eyeHeight, 0.0)
   }

   @EventHandler
   private fun onSilentRotation(event: RotationRequestEvent) {
      if (this.silentSetting.value) {
         val rotation: LockedRotation = this.getLockedRotation()
         if (rotation.hasLockedAxis()) {
            ClientContext.rotationManager.forceRotationOverride()
            val applyManager: RotationManager = ClientContext.rotationManager
            val angles = FloatArray(2)
            angles[0] = rotation.resolvedYaw
            angles[1] = rotation.resolvedPitch
            applyManager.requestRotation(angles, 1200, true)
         }
      }
   }

   @EventHandler
   private fun onPlayerRotation(event: WorldRenderEvent.Main) {
      if (this.modeSetting.value == ModeChoice.TARGET) {
         if (!this.disableRangeSetting.isAtMinimum()) {
            val targetPosition: Vec3? = this.findTargetEyePosition()
            if (targetPosition != null) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               val playerEyePosition: Vec3 = player.eyePosition
               val distance: Double = targetPosition.distanceTo(playerEyePosition)
               val threshold: Float = this.disableRangeSetting.value
               if (distance <= threshold.toDouble()) {
                  this.disable()
                  return
               }
            }
         }
      }
      val useSilentRotation: Boolean = this.silentSetting.value
      if (!useSilentRotation) {
         val rotation: LockedRotation = this.getLockedRotation()
         if (rotation.yawLocked) {
            val yawDestination: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val yawInterpolation: Float = event.partialTick
            val yawSource: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val yawPartialTick: Float = RenderUtils.getPartialTick()
            val currentYaw: Float = yawSource.getYRot(yawPartialTick)
            val lockedYaw: Float = rotation.resolvedYaw
            val interpolatedYaw: Float = Mth.lerp(yawInterpolation, currentYaw, lockedYaw)
            yawDestination.setYRot(interpolatedYaw)
         }
         if (rotation.pitchLocked) {
            val pitchDestination: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val pitchInterpolation: Float = event.partialTick
            val pitchSource: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val pitchPartialTick: Float = RenderUtils.getPartialTick()
            val currentPitch: Float = pitchSource.getXRot(pitchPartialTick)
            val lockedPitch: Float = rotation.resolvedPitch
            val interpolatedPitch: Float = Mth.lerp(pitchInterpolation, currentPitch, lockedPitch)
            pitchDestination.setXRot(interpolatedPitch)
         }
      }
   }

   private fun getTargetYawDistance(target: Entity): Float {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val playerYaw: Float = player.yRot
      val targetYaw: Float = RotationManager.getRotationsToEntity(target)[0]
      return Mth.degreesDifferenceAbs(playerYaw, targetYaw)
   }

   private fun getLockedRotation(): LockedRotation {
      val choice: ModeChoice = this.modeSetting.value
      return when (choice) {
         ModeChoice.PLAIN -> this.plainRotationProvider.getRotation()
         ModeChoice.TARGET -> this.targetRotationProvider.getRotation()
      }
   }

   private fun findTargetEyePosition(): Vec3? {
      val name = this.targetName ?: return null
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val targetPosition: Optional<Vec3> = level.players().stream()
         .filter { candidate: AbstractClientPlayer ->
            candidate.gameProfile.name() == name
         }
         .findFirst()
         .map { candidate: AbstractClientPlayer -> this.getInterpolatedEyePosition(candidate) }
      return targetPosition.orElse(null)
   }

   init {
      this.modeSetting = this.add(EnumSetting("Mode", ModeChoice.PLAIN))
      this.silentSetting = this.add(BooleanSetting("Silent", false))
      this.yawSetting = this.add(BooleanSetting("Yaw", true).markVisibilityDependency())
      this.customYawSetting = this.add(BooleanSetting("Custom", false).dependsOn(this.yawSetting))
      this.yawValueSetting = this.add(NumberSetting<Float>("Value", 0.0f, -180.0f, 180.0f).withUnit("°")
         .dependsOn(this.yawSetting, this.customYawSetting))
      this.pitchSetting = this.add(BooleanSetting("Pitch", false).markVisibilityDependency())
      this.pitchValueSetting = this.add(NumberSetting<Float>("Value", 0.0f, -90.0f, 90.0f).withUnit("°").dependsOn(this.pitchSetting))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 8.0f, 4.0f, 64.0f).withUnit("m"))
      this.disableRangeSetting = this.add(NumberSetting<Float>("DisableRange", 0.0f, 0.0f, 32.0f).withUnit("m"))
      this.plainRotationProvider = PlainRotationProvider()
      this.targetRotationProvider = TargetRotationProvider()
      this.modeSetting.setChangeCallback { this.selectTarget() }
      val plainMode = Predicate<Any> { ignored: Any ->
         val selectedMode = this.modeSetting
         selectedMode.value == ModeChoice.PLAIN
      }
      this.yawSetting.addVisibilityPredicate(plainMode)
      this.customYawSetting.addVisibilityPredicate(plainMode)
      this.yawValueSetting.addVisibilityPredicate(plainMode)
      this.pitchSetting.addVisibilityPredicate(plainMode)
      this.pitchValueSetting.addVisibilityPredicate(plainMode)
      this.rangeSetting.addVisibilityPredicate { ignored: Float ->
         val selectedMode = this.modeSetting
         selectedMode.value == ModeChoice.TARGET
      }
      val minimumLabelSetting: Setting<Float> = this.disableRangeSetting
      val minimumEndpoint: SettingLabelCondition = SettingLabelCondition.MIN
      minimumLabelSetting.setBoundaryLabel("None", minimumEndpoint)
      this.yawValueSetting.setConfigName("YawValue")
      this.pitchValueSetting.setConfigName("PitchValue")
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      PLAIN("Plain"),
      TARGET("Target");

      override fun getName(): String {
         return this.displayName
      }
   }

   private inner class TargetRotationProvider {
      fun getRotation(): LockedRotation {
         val targetPosition: Vec3? = this@RotationLockModule.findTargetEyePosition()
         if (targetPosition == null) {
            return LockedRotation()
         }
         val eyePositionOwner = this@RotationLockModule
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val playerPosition: Vec3 = eyePositionOwner.getInterpolatedEyePosition(player)
         val angles: FloatArray = RotationManager.calculateRotations(playerPosition, targetPosition)
         if (PlayerStateUtils.isFallFlying()) {
            return this.avoidObstacles(angles)
         }
         return LockedRotation(angles[0], angles[1])
      }

      private fun avoidObstacles(angles: FloatArray): LockedRotation {
         val movementYaw: Float = this.getMovementYaw()
         val clearPitch: Optional<Float> = this.findClearPitch(angles[0], angles[1])
         if (clearPitch.isPresent) {
            return LockedRotation(angles[0], clearPitch.get())
         }
         return LockedRotation(this.findClearYaw(movementYaw, angles[0], angles[1]), angles[1])
      }

      private fun getMovementYaw(): Float {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val motion: Vec3 = MathUtils.getPositionDelta(player)
         return if (motion.horizontalDistance() < 0.05) {
            player.yRot
         } else {
            Math.toDegrees(Math.atan2(-motion.x, motion.z)).toFloat()
         }
      }

      private fun findClearPitch(yaw: Float, pitch: Float): Optional<Float> {
         if (this.isDirectionClear(yaw, pitch)) {
            return Optional.of(pitch)
         }
         var offset: Float = 15.0f
         while (offset <= 75.0f) {
            val candidate: Float = Math.min(pitch + offset, 90.0f)
            if (this.isDirectionClear(yaw, candidate)) {
               return Optional.of(candidate)
            }
            offset += 15.0f
         }
         return Optional.empty()
      }

      private fun findClearYaw(movementYaw: Float, yaw: Float, pitch: Float): Float {
         val difference: Float = Mth.clamp(Mth.wrapDegrees(yaw - movementYaw), -50.0f, 50.0f)
         var candidate: Float = Mth.wrapDegrees(movementYaw + difference)
         if (this.isDirectionClear(candidate, pitch)) {
            return candidate
         }
         var offset: Float = 5.0f
         while (offset <= 50.0f) {
            if (difference + offset <= 50.0f) {
               candidate = Mth.wrapDegrees(movementYaw + difference + offset)
               if (this.isDirectionClear(candidate, pitch)) {
                  return candidate
               }
            }
            if (difference - offset >= -50.0f) {
               candidate = Mth.wrapDegrees(movementYaw + difference - offset)
               if (this.isDirectionClear(candidate, pitch)) {
                  return candidate
               }
            }
            offset += 5.0f
         }
         return movementYaw
      }

      private fun isDirectionClear(yaw: Float, pitch: Float): Boolean {
         val eyes: Vec3 = ClientAccess.collectiveMinecraft.player!!.eyePosition
         val destination: Vec3 = eyes.add(Vec3.directionFromRotation(pitch, yaw).scale(6.0))
         return RaycastUtils.isLineVisible(eyes, destination)
      }
   }

   private inner class PlainRotationProvider {
      fun getRotation(): LockedRotation {
         return LockedRotation(
            this.getYawForLock(),
            this@RotationLockModule.pitchValueSetting.value,
            this@RotationLockModule.yawSetting.value,
            this@RotationLockModule.pitchSetting.value
         )
      }

      private fun getYawForLock(): Float {
         val yawOwner = this@RotationLockModule
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val currentYaw: Float = player.yRot
         return yawOwner.calculateYaw(currentYaw)
      }
   }

   private class LockedRotation(
      val yaw: Float,
      val pitch: Float,
      val yawLocked: Boolean,
      val pitchLocked: Boolean
   ) {
      constructor(yaw: Float, pitch: Float) : this(yaw, pitch, true, true)

      constructor() : this(0.0f, 0.0f, false, false)

      val resolvedYaw: Float
         get() {
            if (!this.yawLocked) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               return player.yRot
            }
            return this.yaw
         }

      val resolvedPitch: Float
         get() {
            if (!this.pitchLocked) {
               val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               return player.xRot
            }
            return this.pitch
         }

      fun hasLockedAxis(): Boolean {
         return this.yawLocked || this.pitchLocked
      }
   }
}
