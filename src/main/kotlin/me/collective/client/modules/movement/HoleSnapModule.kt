package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.combat.BlockRaycastClassifier
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.managers.HoleManager
import me.collective.client.utils.world.HoleInfo
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.HoleSnapPriorityChoice
import java.util.function.Predicate
import java.util.concurrent.TimeUnit
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction.Axis
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext.Block
import net.minecraft.world.level.ClipContext.Fluid
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class HoleSnapModule : Module(
   "HoleSnap", "Pushes you into holes as you go past them.", ModuleCategory.MOVEMENT,
   *Companion.createAliases()
) {
   protected val prioritySetting: Setting<HoleSnapPriorityChoice>
   private val heightSetting: Setting<Float>
   private val timeoutSetting: Setting<Float>
   private val rangeSetting: Setting<Float>
   private val speedSetting: Setting<Float>
   private val pitchSetting: Setting<Float>
   private val autoDisableSetting: Setting<Boolean>
   private val pauseStepSetting: Setting<Boolean>
   private val pauseInBlocksSetting: Setting<Boolean>
   protected val shiftSetting: Setting<Boolean>

   @JvmField
   val factorSetting: Setting<Int>

   @JvmField
   val chargeSetting: Setting<Int>
   private val directionalSetting: Setting<Boolean>
   private val softSetting: Setting<Boolean>
   protected val alignSetting: Setting<Boolean>
   protected val onlyWideSetting: Setting<Boolean>
   private val alignPitchSetting: Setting<Float>
   private val chargeController: HoleSnapChargeController
   private val stepPauseTimer: ElapsedTimer

   @JvmField
   val targetTimeoutTimer: ElapsedTimer
   private var alignmentTarget: SnapTarget? = null
   private var wasInHole: Boolean = false
   private var forwardOverrideActive: Boolean = false

   override fun onToggle() {
      this.targetTimeoutTimer.clear()
   }

   @EventHandler(priority = -100)
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      if (this.forwardOverrideActive && !this.shouldPauseSnapping() && !this.isWaitingForTarget() &&
         RotationManager.isMovementSyncEnabled()) {
         ClientContext.inputOverrides.requestForward(true)
         ClientContext.inputOverrides.requestForwardMovement(1.0f)
      }

      val playerBounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val playerY: Double = ClientAccess.collectiveMinecraft.player!!.y
      val feetBounds: AABB = playerBounds.setMaxY(playerY).inflate(0.05)
      if (!ClientAccess.collectiveMinecraft.player!!.isInWater) {
         return
      }
      val holeManager: HoleManager = ClientContext.holeManager
      val belowPlayer: BlockPos = ClientAccess.collectiveMinecraft.player!!.blockPosition().below()
      if (!holeManager.intersectsHole(belowPlayer) || BlockPos.betweenClosedStream(feetBounds).count() != 1L) {
         return
      }
      ClientContext.inputOverrides.requestSneak(true)
   }

   @EventHandler
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      if (this.shouldPauseSnapping()) {
         return
      }

      this.alignmentTarget = this.findAlignmentTarget()
      val holeManagerSnapshot: HoleManager = ClientContext.holeManager

      val playerBlock: BlockPos = PlayerStateUtils.getLocalRoundedBlockPosition()

      if (holeManagerSnapshot.intersectsHole(playerBlock)) {
         if (this.autoDisableSetting.value && (this.alignmentTarget == null || this.alignmentTarget!!.distance < 0.01)) {
            this.disable()
            this.forwardOverrideActive = false
            return
         }

         this.targetTimeoutTimer.reset()
      }

      if (this.isWaitingForTarget() || RotationManager.isMovementSyncEnabled()) {
         return
      }

      val target: SnapTarget? = this.getSnapTarget()
      if (target == null) {
         this.forwardOverrideActive = false
         return
      }

      if (this.softSetting.value) {
         if (this.directionalSetting.value) {
            val directionalDistance: Double = target.distance

            if (directionalDistance < 0.01) {
               return
            }
         }
      }

      val targetPosition: Vec3 = target.position
      val playerPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
      val direction: Vec3 = targetPosition.subtract(playerPosition).with(Axis.Y, 0.0).normalize()

      val movementSpeed: Double = MovementPhysics.getBaseMoveSpeed(
         true
      )
      val speedMultiplier: Float = this.speedSetting.value
      val scaledSpeed: Double = movementSpeed * speedMultiplier
      val targetDistance: Double = target.distance
      val stepSpeed: Double = Math.min(scaledSpeed, targetDistance)
      val movement: Vec3 = direction.scale(stepSpeed)
      val deltaZ: Double = movement.z
      val deltaX: Double = movement.x
      event.setHorizontalVelocity(deltaX, deltaZ)
   }

   protected fun hasBlockingRaycast(start: Vec3, end: Vec3): Boolean {
      val context: BlockRaycastContext = BlockRaycastContext(
         start,
         end,
         Block.COLLIDER,
         Fluid.NONE,
         ClientAccess.collectiveMinecraft.player!!,
         BlockRaycastClassifier.NO_OVERRIDE
      )
      val hit: BlockHitResult = RaycastUtils.raycast(context)
      return HitResult.Type.MISS != hit.type
   }

   @EventHandler
   private fun onRotationUpdate(event: RotationRequestEvent) {
      ClientContext.playerTickController.releaseMultiplier(this)

      this.alignmentTarget = this.findAlignmentTarget()

      val inHole: Boolean = ClientContext.holeManager.isInHole()
      if (ClientAccess.collectiveMinecraft.player!!.onGround() && inHole != this.wasInHole) {
         this.wasInHole = inHole

         this.stepPauseTimer.reset()
      }

      if (this.shouldPauseSnapping() || this.isWaitingForTarget()) {
         return
      }

      val target: SnapTarget? = this.getSnapTarget()
      if (target == null) {
         this.forwardOverrideActive = false
         return
      }

      if (RotationManager.isMovementSyncEnabled()) {
         ClientContext.rotationManager.forceRotationOverride()
         val targetRotationManagerSnapshot: RotationManager = ClientContext.rotationManager
         val targetPosition: Vec3 = target.position

         val angles: FloatArray = RotationManager.getRotationsToPosition(targetPosition)
         targetRotationManagerSnapshot.requestRotation(angles, 99999, true)
         this.forwardOverrideActive = true
      }

      if (this.shiftSetting.value) {
         this.chargeController.requestShift()
      }
   }

   private fun shouldPauseSnapping(): Boolean {
      val playerPitch: Float = ClientAccess.collectiveMinecraft.player!!.getXRot()
      val requiredPitch: Float = this.pitchSetting.value
      if (playerPitch < requiredPitch) {
         if (this.pitchSetting.value != 0.0f) {
            return true
         }
      }

      if (ClientContext.serverStateManager.setbackTimer.hasElapsed(250L)) {
         if (!ClientAccess.collectiveMinecraft.player!!.isSpectator) {
            if (!ClientAccess.collectiveMinecraft.player!!.isFallFlying) {
               return false
            }
         }
      }

      return true
   }

   private fun findAlignmentTarget(): SnapTarget? {
      if (!this.alignSetting.value) {
         return null
      }
      if (!ClientContext.holeManager.isInHole()) {
         return null
      }
      if (!ClientAccess.collectiveMinecraft.player!!.onGround() || MovementPhysics.isLocalPlayerMoving()) {
         return null
      }
      val pitch: Float = ClientAccess.collectiveMinecraft.player!!.getXRot()
      val minimumPitch: Float = this.alignPitchSetting.value
      if (pitch < minimumPitch) {
         if (!this.alignPitchSetting.isAtMinimum) {
            return null
         }
      }
      val alignedHoleManager: HoleManager = ClientContext.holeManager
      val playerBlock: BlockPos = PlayerStateUtils.getLocalRoundedBlockPosition()
      val hole: HoleInfo? = alignedHoleManager.findIntersectingHole(playerBlock)
      if (hole == null) {
         return null
      }
      val holeSize: Double = hole.bounds.size
      if (holeSize == 1.0) {
         if (this.onlyWideSetting.value) {
            return null
         }
      }
      val holeBounds: AABB = hole.bounds
      val holeCenter: Vec3 = BoundingBoxUtils.getBottomCenter(holeBounds)
      val distance: Double = ClientAccess.collectiveMinecraft.player!!.position().distanceTo(holeCenter)
      val minimumDistance: Double = if (RotationManager.isMovementSyncEnabled()) 0.1 else 0.0
      if (distance < minimumDistance) {
         return null
      }
      return SnapTarget(holeCenter, distance)
   }

   private fun isWaitingForTarget(): Boolean {
      if (this.alignmentTarget != null) {
         return false
      }

      val timeoutTimerSnapshot: ElapsedTimer = this.targetTimeoutTimer
      val timeoutSeconds: Float = this.timeoutSetting.value
      return !timeoutTimerSnapshot.hasElapsed(timeoutSeconds.toDouble(), TimeUnit.SECONDS)
   }

   fun getSnapTarget(): SnapTarget? {
      if (this.pauseInBlocksSetting.value && WallsVelocityHandler.hasRaisedPlayerCollision()) {
         return null
      }
      if (this.alignmentTarget != null) {
         return this.alignmentTarget
      }

      var bestDistance: Double = 9999.0
      var bestHole: HoleInfo? = null
      var bestPosition: Vec3? = null

      val footPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position().add(0.0, 0.01, 0.0)
      val heightOffsetY: Double = ClientAccess.collectiveMinecraft.player!!.getBbHeight().toDouble()
      val headPosition: Vec3 = footPosition.add(0.0, heightOffsetY, 0.0)

      for (holeEntry in ClientContext.holeManager.holes) {
         val hole: HoleInfo = holeEntry as HoleInfo
         var candidatePosition: Vec3 = hole.bounds.center
         var candidateDistance: Double = footPosition.distanceTo(candidatePosition)
         if (this.softSetting.value && this.directionalSetting.value) {
            val yAxis: Axis = Axis.Y
            val playerY: Double = footPosition.y
            val candidateY: Double = candidatePosition.y
            var candidateTop: Double = candidatePosition.y
            val heightSettingSnapshot: Setting<Float> = this.heightSetting
            val heightValue: Float = heightSettingSnapshot.value
            candidateTop += heightValue
            val maximumCandidateY: Double = candidateTop - 0.5
            val clampedY: Double = Mth.clamp(playerY, candidateY, maximumCandidateY)
            candidatePosition = candidatePosition.with(yAxis, clampedY)
         } else {
            candidatePosition = candidatePosition.add(0.0, 0.5, 0.0)
            val capturedPlayerPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
            candidateDistance = Vec3Utils.horizontalDistance(candidatePosition, capturedPlayerPosition).toDouble()
            val verticalDifference: Double = ClientAccess.collectiveMinecraft.player!!.y - candidatePosition.y
            val heightDifference: Double = Math.abs(verticalDifference)
            val heightSettingSnapshot: Setting<Float> = this.heightSetting
            val maximumHeight: Float = heightSettingSnapshot.value
            if (heightDifference > maximumHeight) {
               continue
            }
         }

         val holeY: Int = hole.pos.y
         if (holeY >= footPosition.y) {
            continue
         }
         if (hole.trapped) {
            continue
         }
         if (this.directionalSetting.value) {
            if (!MovementPhysics.isMovingToward(candidatePosition) && candidateDistance > 0.05) {
               continue
            }
         }
         val range: Float = this.rangeSetting.value
         if (candidateDistance > range) {
            continue
         }
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val collisionBounds: AABB = hole.bounds
         val playerMaxY: Double = ClientAccess.collectiveMinecraft.player!!.boundingBox.maxY
         val boundsForCollision: AABB = collisionBounds.setMaxY(playerMaxY)
         if (!level.noCollision(boundsForCollision)) {
            continue
         }
         val firstRayEnd: Vec3 = candidatePosition.with(Axis.Y, footPosition.y)
         if (this.hasBlockingRaycast(footPosition, firstRayEnd)) {
            continue
         }
         val secondRayEnd: Vec3 = candidatePosition.with(Axis.Y, headPosition.y)
         if (this.hasBlockingRaycast(headPosition, secondRayEnd)) {
            continue
         }
         val thirdRayEnd: Vec3 = candidatePosition.with(Axis.Y, headPosition.y)
         if (this.hasBlockingRaycast(candidatePosition, thirdRayEnd)) {
            continue
         }
         if (bestHole != null) {
            if ((this.prioritySetting.value).comparator.compare(hole, bestHole!!) >= 0) {
               continue
            }
         }
         bestHole = hole
         bestDistance = candidateDistance
         bestPosition = candidatePosition
      }

      if (bestHole == null) {
         return null
      }

      val finalRange: Float = this.rangeSetting.value
      return if (bestDistance > finalRange) null else SnapTarget(bestPosition!!, bestDistance)
   }

   fun shouldPauseStep(): Boolean {
      if (!this.stepPauseTimer.hasElapsed(400L)) {
         if (ClientContext.holeManager.isInHole()) {
            if (this.pauseStepSetting.value) {
               return true
            }
         }
      }

      return false
   }

   init {
      this.prioritySetting = this.add(EnumSetting<HoleSnapPriorityChoice>("Priority", HoleSnapPriorityChoice.DISTANCE))
      this.heightSetting = this.add(NumberSetting<Float>("Height", 2.0f, 1.0f, 6.0f).withUnit("m"))
      this.timeoutSetting = this.add(NumberSetting<Float>("Timeout", 0.5f, 0.0f, 2.0f).withUnit("s"))
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 1.0f, 0.1f, 3.0f).withUnit("m"))
      val speedVisibility: Predicate<Any> = Predicate { ignored -> !RotationManager.isMovementSyncEnabled() }
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.5f, 3.0f, speedVisibility))
      this.pitchSetting = this.add(NumberSetting<Float>("Pitch", 0.0f, 0.0f, 90.0f).withUnit("°"))
      this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", true))
      this.pauseStepSetting = this.add(BooleanSetting("PauseStep", false))
      this.pauseInBlocksSetting = this.add(BooleanSetting("PauseInBlocks", false))
      this.shiftSetting = this.add(BooleanSetting("Shift", false).markVisibilityDependency())
      this.factorSetting = this.add(NumberSetting<Int>("Factor", 2, 1, 6).dependsOn(this.shiftSetting))
      this.chargeSetting = this.add(NumberSetting<Int>("Charge", 3, 1, 16).dependsOn(this.shiftSetting))
      this.directionalSetting = this.add(BooleanSetting("Directional", false).markVisibilityDependency())
      this.softSetting = this.add(BooleanSetting("Soft", false).dependsOn(this.directionalSetting))
      this.alignSetting = this.add(BooleanSetting("Align", false).markVisibilityDependency())
      this.onlyWideSetting = this.add(BooleanSetting("OnlyWide", false).dependsOn(this.alignSetting))
      this.alignPitchSetting = this.add(NumberSetting<Float>("AlignPitch", 0.0f, 0.0f, 90.0f).withUnit("°").dependsOn(this.alignSetting))
      this.chargeController = HoleSnapChargeController(this)
      this.stepPauseTimer = ElapsedTimer()
      this.targetTimeoutTimer = ElapsedTimer()
      this.pitchSetting.setBoundaryLabel("None", SettingLabelCondition.MIN)
      this.alignPitchSetting.setBoundaryLabel("Any", SettingLabelCondition.MIN)
   }

   data class SnapTarget(
      val position: Vec3,
      val distance: Double
   )

   companion object {
      private fun createAliases(): Array<String> {
         val aliases: Array<String> = arrayOf(
            "holetp", "anchor"
         )
         return aliases
      }
   }
}
