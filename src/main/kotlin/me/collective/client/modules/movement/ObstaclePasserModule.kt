package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.combat.AutoArmorModule
import me.collective.client.modules.player.RotationLockModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.mixin.BlockBehaviourAccessor
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Position
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.core.Vec3i
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class ObstaclePasserModule : Module(
   "ObstaclePasser", Companion.createDescription(), ModuleCategory.MOVEMENT
) {
   private val correctionWindow: ObstacleCorrectionWindow
   val alignSetting: Setting<Boolean>
   private val onlyLoadedSetting: Setting<Boolean>
   val ySetting: Setting<Int>
   val maxTravelSetting: Setting<Int>
   private val loadedChunksTimer: ElapsedTimer
   private var travelYaw: Float = 0.0f
   private var anchorPosition: BlockPos? = null
   private var pathWasActive: Boolean = false
   private var elytraPassActive: Boolean = false
   private var passCooldownTicks: Int = 0
   private var alignmentDirection: ObstacleAlignmentDirection? = null

   private fun hasNearbyCollision(): Boolean {
      var bounds: AABB = ClientAccess.collectiveMinecraft.player!!.boundingBox
      val horizontalInflation: Double = BoundingBoxUtils.blockCornerInset
      bounds = bounds.inflate(horizontalInflation, 0.0, horizontalInflation)
      return !ClientAccess.collectiveMinecraft.level!!.noBlockCollision(
         ClientAccess.collectiveMinecraft.player!!, bounds
      )
   }

   private fun shouldAdjustBounceYaw(): Boolean {
      val integerYaw: Int = this.travelYaw.toInt()

      if (integerYaw % MathConstants.NINETY_DEGREES !=
         0
      ) {
         return false
      }

      if (elytraFly!!.isToggled()) {
         if (elytraFly!!.modeSetting.value == ElytraFlyModule.ModeChoice.BOUNCE) {
            return elytraFly!!.packetSetting.value
         }
      }

      return false
   }

   private fun findYawAdjustment(): Float {
      var closestOffset: Int = 1000
      var yawAdjustment: Float = 0.0f
      val yawSteps: Float = this.travelYaw /
         MathConstants.NINETY_DEGREES
      val roundedYaw: Int = Math.round(yawSteps) *
         MathConstants.NINETY_DEGREES
      val scanYaw: Float = (roundedYaw + MathConstants.NINETY_DEGREES).toFloat()
      val direction: Vec3 = Vec3.directionFromRotation(0.0f, scanYaw)
      val playerPosition: BlockPos = ClientAccess.collectiveMinecraft.player!!.blockPosition()
      val directionX: Double = direction.x
      val offsetX: Double = Math.ceil(directionX)
      val directionZ: Double = direction.z
      val offsetZ: Double = Math.ceil(directionZ)
      val blockDirection: BlockPos = BlockPos.containing(offsetX, 0.0, offsetZ)

      var scanOffset: Int = -6

      while (true) {
         if (scanOffset > 6) {
            return yawAdjustment
         }

         val absoluteOffset: Int = Math.abs(scanOffset)
         val scaledDirection: Vec3i = blockDirection.multiply(scanOffset)
         val positionToCheck: BlockPos = playerPosition.offset(scaledDirection)
         if (WorldUtils.isPlacementOccupied(positionToCheck) && absoluteOffset < closestOffset) {
            closestOffset = absoluteOffset
            val adjustment: Float
            if (scanOffset > 0) {
               adjustment = 3.0f
            } else {
               adjustment = -3.0f
            }

            yawAdjustment = adjustment
         }

         scanOffset++
      }
   }

   private fun captureTravelYaw() {
      val playerYaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()

      val yawSteps: Float = (playerYaw + 1.0f) /
         MathConstants.FORTY_FIVE_DEGREES
      this.travelYaw = (Math.round(yawSteps) *
         MathConstants.FORTY_FIVE_DEGREES).toFloat()
   }

   private fun canUseLoadedChunks(): Boolean {
      val chunkSource: ClientChunkCache = ClientAccess.collectiveMinecraft.level!!.chunkSource
      val capturedChunk: ChunkPos = ClientAccess.collectiveMinecraft.player!!.chunkPosition()

      if (!ChunkPos.rangeClosed(capturedChunk, 2).allMatch { chunk: ChunkPos ->
            val chunkZ: Int = chunk.z
            val chunkX: Int = chunk.x
            chunkSource.hasChunk(chunkX, chunkZ)
         }) {
         if (this.onlyLoadedSetting.value) {
            return false
         }
      }

      return true
   }

   fun isElytraPassActive(): Boolean {
      return this.elytraPassActive && elytraFly!!.isToggled()
   }

   fun isPathActive(): Boolean {
      return ClientContext.baritoneBridge.isPathingOrCalculating()
   }

   private fun updateObstacleTarget() {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         val activeTarget: BlockPos? = ClientContext.baritoneBridge.getGoalPosition()
         if (activeTarget != null) {
            val targetCenter: Vec3 = Vec3.atBottomCenterOf(activeTarget)
            val yawOrigin: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
            val targetYaw: Float = RotationManager.calculateRotations(yawOrigin, targetCenter)[0]
            val currentTravelYaw: Float = this.getTravelYaw()
            val yawDifference: Float = Mth.degreesDifferenceAbs(targetYaw, currentTravelYaw)
            if (yawDifference > 100.0f) {
               ClientContext.baritoneBridge.cancelPathing()
               return
            }

            val rangeOrigin: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
            val targetDistance: Float = Vec3Utils.horizontalDistance(rangeOrigin, targetCenter)
            val maximumTravel: Int = this.maxTravelSetting.value
            if (targetDistance > maximumTravel + 2) {
               ClientContext.baritoneBridge.cancelPathing()
               return
            }

            val arrivalOrigin: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
            val arrivalDistance: Float = Vec3Utils.horizontalDistance(arrivalOrigin, targetCenter)
            if (arrivalDistance <= 1.0f) {
               ClientContext.baritoneBridge.cancelPathing()
               this.passCooldownTicks = 10
               if (elytraFly!!.isToggled()) {
                  this.elytraPassActive = true
               }
            }
         }
      }

      if (this.shouldStartPass() && !ClientContext.baritoneBridge.isPathingOrCalculating()) {
         this.correctionWindow.clear()
         val configuredHeight: Int = this.ySetting.value
         val travelHeight: Int
         if (configuredHeight == -1) {
            travelHeight = this.anchorPosition!!.y
         } else {
            val customHeight: Int = this.ySetting.value
            travelHeight = customHeight
         }

         if (this.alignmentDirection != null) {
            if (this.alignSetting.value) {
               var alignedTarget: BlockPos? = null
               var alignedSearchOffset: Int = 5

               while (true) {
                  val alignedTravelLimit: Int = this.maxTravelSetting.value
                  if (alignedSearchOffset >= alignedTravelLimit) {
                     break
                  }

                  val offsetPosition: BlockPos = this.alignmentDirection!!.positionAtOffset(alignedSearchOffset)
                  alignedTarget = offsetPosition.atY(travelHeight)
                  val groundBelow: BlockPos = alignedTarget.below()
                  if ((WorldUtils.getBlock(groundBelow) as BlockBehaviourAccessor).isCollidable()) {
                     break
                  }

                  alignedSearchOffset++
               }

               ClientContext.baritoneBridge.pathTo(alignedTarget!!)
               return
            }
         }

         val anchorCenter: Vec3 = Vec3.atBottomCenterOf(this.anchorPosition!!)
         val projectionYaw: Float = this.getTravelYaw()
         val horizontalDirection: Vec3 = this.createHorizontalDirection(projectionYaw)
         val playerPosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
         val anchorDistance: Double = this.horizontalDistance(anchorCenter, playerPosition)
         val projectionOffset: Vec3 = horizontalDirection.scale(anchorDistance)
         val projectedPosition: BlockPos = BlockPos.containing(anchorCenter.add(projectionOffset) as Position)
         val projectedDistanceTarget: Vec3 = Vec3(projectedPosition)
         val projectedDistance: Double = this.horizontalDistance(playerPosition, projectedDistanceTarget)

         if (projectedDistance > 32.0) {
            this.anchorPosition = ClientAccess.collectiveMinecraft.player!!.blockPosition()
            this.captureTravelYaw()
            return
         }

         var searchPosition: BlockPos? = null
         var searchOffset: Int = 5

         while (true) {
            val freeTravelLimit: Int = this.maxTravelSetting.value
            if (searchOffset >= freeTravelLimit) {
               break
            }

            val freeOffset: Double = searchOffset.toDouble()
            val freePositionOffset: Vec3i = BlockPos.containing(horizontalDirection.scale(freeOffset) as Position)
            searchPosition = projectedPosition.offset(freePositionOffset)
            val freeGroundBelow: BlockPos = searchPosition.below()
            if ((WorldUtils.getBlock(freeGroundBelow) as BlockBehaviourAccessor).isCollidable()) {
               break
            }

            searchOffset++
         }

         ClientContext.baritoneBridge.pathTo(searchPosition!!.atY(travelHeight))
      }
   }

   private fun shouldStartPass(): Boolean {
      if (this.passCooldownTicks > 0) {
         return false
      }

      var startPass: Boolean = ClientAccess.collectiveMinecraft.player!!.horizontalCollision

      if (this.shouldAdjustBounceYaw()) {
         val trackedMotion: Double = ClientContext.movementSpeedTracker.displaySpeed

         if (trackedMotion > 15.0) {
            startPass = false
         }
      }

      if (!startPass) {
         if (!this.correctionWindow.exceedsLimit()) {
            if (!PlayerStateUtils.isFallFlying()) {
               val movementState: Int = ClientContext.playerStateTracker.getGroundedTicks()

               if (movementState > 3) {
                  if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
                     if (elytraFly!!.isToggled()) {
                        return true
                     }
                  }
               }
            }

            return false
         }
      }

      return true
   }

   private fun updatePathTransition() {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (!this.pathWasActive) {
            this.pathWasActive = true
            ClientContext.playerStateTracker.setSneaking(true)
            ClientContext.playerStateTracker.setSneaking(false)
         }
      } else if (this.pathWasActive) {
         this.pathWasActive = false
      }
   }

   override fun onEnable() {
      if (this.isWorldUnavailable()) {
         this.toggle()
      } else {
         this.pathWasActive = false
         this.alignmentDirection = null
         this.passCooldownTicks = 0
         this.anchorPosition = ClientAccess.collectiveMinecraft.player!!.blockPosition()
         this.correctionWindow.clear()

         this.captureTravelYaw()
      }
   }

   override fun onDisable() {
      ClientContext.baritoneBridge.cancelPathing()
      // Раньше здесь безусловно выставлялся autoArmor.elytraSetting = true: модуль его
      // не менял, но портил (и сохранял в конфиг) пользовательскую настройку AutoArmor.
   }

   fun isPassingObstacles(): Boolean {
      return this.isToggled() && this.isPathActive()
   }

   private fun getTravelYaw(): Float {
      if (rotationLock!!.isToggled()) {
         if (rotationLock!!.yawSetting.value) {
            val customYaw: Boolean = rotationLock!!.customYawSetting.value
            val yaw: Float
            if (customYaw) {
               val yawValue: Float = rotationLock!!.yawValueSetting.value
               yaw = yawValue
            } else {
               val yawLock: RotationLockModule = rotationLock!!
               val baseYaw: Float = this.travelYaw
               yaw = yawLock.calculateYaw(baseYaw)
            }

            return yaw
         }
      }

      return this.travelYaw
   }

   @EventHandler(priority = -100)
   private fun updatePassState(event: LocalPlayerPreTickEvent) {
      if (this.canUseLoadedChunks()) {
         this.loadedChunksTimer.reset()
      }

      this.updatePathTransition()
      if (this.alignmentDirection == null) {
         val yaw: Float = this.getTravelYaw()
         this.alignmentDirection = ObstacleAlignmentDirection.fromYaw(
            yaw
         )
      }

      if (this.passCooldownTicks > 0) {
         this.passCooldownTicks--
      }

      this.correctionWindow.pruneExpired()

      this.updateObstacleTarget()
      if (this.onlyLoadedSetting.value) {
         if (this.loadedChunksTimer.hasElapsed(750L)) {
            return
         }
      }

      if (!this.isPathActive()) {
         fun flightInput() {
            if (!this.isElytraPassActive()) {
               if (ClientAccess.collectiveMinecraft.player!!.isFallFlying) {
                  return
               }

               if (!elytraFly!!.isToggled()) {
                  return
               }
            }

            ClientContext.inputOverrides.requestJump(true)
         }

         flightInput()

         ClientContext.inputOverrides.requestMovement(1.0f, 0.0f)
      }
   }

   @EventHandler
   private fun applyTravelYaw(event: LocalPlayerTickEvent) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         var yaw: Float = this.getTravelYaw()

         if (this.shouldAdjustBounceYaw()) {
            if (!this.hasNearbyCollision()) {
               yaw += this.findYawAdjustment()
            }
         }

         ClientAccess.collectiveMinecraft.player!!.setYRot(yaw)
      }
   }

   private fun createHorizontalDirection(yaw: Float): Vec3 {
      val direction: Vec3 = ClientAccess.collectiveMinecraft.player!!.calculateViewVector(0.0f, yaw)
      val directionX: Double = direction.x
      val absoluteX: Double = Math.abs(directionX)

      if (absoluteX < 0.01) {
         val preservedYForX: Double = direction.y
         val preservedZ: Double = direction.z
         Vec3Utils.setComponents(direction, 0.0, preservedYForX, preservedZ)
      }

      val directionZ: Double = direction.z
      val absoluteZ: Double = Math.abs(directionZ)

      if (absoluteZ < 0.01) {
         val preservedX: Double = direction.x
         val preservedYForZ: Double = direction.y
         Vec3Utils.setComponents(direction, preservedX, preservedYForZ, 0.0)
      }

      val horizontalX: Double = direction.x
      val horizontalZ: Double = direction.z
      Vec3Utils.setComponents(direction, horizontalX, 0.0, horizontalZ)
      return direction
   }

   private fun horizontalDistance(first: Vec3, second: Vec3): Double {
      val firstFlat: Vec3 = first.multiply(1.0, 0.0, 1.0)
      val secondFlat: Vec3 = second.multiply(1.0, 0.0, 1.0)
      return firstFlat.distanceTo(secondFlat)
   }

   @EventHandler
   private fun onPositionCorrection(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.correctionWindow.recordCorrection()
      }
   }

   companion object {
      private var rotationLock: RotationLockModule? = null
      private var autoArmor: AutoArmorModule? = null
      private var elytraFly: ElytraFlyModule? = null

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         rotationLock = modules.getModule(RotationLockModule::class.java) as RotationLockModule
         autoArmor = modules.getModule(AutoArmorModule::class.java) as AutoArmorModule
         elytraFly = modules.getModule(ElytraFlyModule::class.java) as ElytraFlyModule
      }

      private fun createDescription(): String {
         val warningColor: String = ChatFormatting.YELLOW.toString()
         return "Passes obstacles in your way. \n" + warningColor + "Requires either ElytraFly or Speed enabled."
      }
   }

   init {
      this.correctionWindow = ObstacleCorrectionWindow(
         3
      )
      this.alignSetting = this.add(BooleanSetting("Align", true))
      this.onlyLoadedSetting = this.add(BooleanSetting("OnlyLoaded", true))
      this.ySetting = this.add(NumberSetting<Int>("Y", -1, -1, 128))
      this.maxTravelSetting = this.add(NumberSetting<Int>("MaxTravel", 12, 6, 15))
      this.loadedChunksTimer = ElapsedTimer()
      this.ySetting.setBoundaryLabel("~", SettingLabelCondition.MIN)
      this.alignSetting.setChangeCallback { this.alignmentDirection = null }
   }
}
