package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.CancellableEvent
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerAiStepEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.PlayerAttackEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.managers.RotationManager
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.access.LocalPlayerTickAccess
import me.collective.client.render.FontRenderer
import me.collective.client.utils.combat.RotationRequest
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.events.EventStage
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AntiCheatRotationsChoice
import me.collective.mixin.EntityAccessor
import me.collective.mixin.LocalPlayerAccessor
import java.util.function.Predicate
import java.util.stream.Stream
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3

class SprintModule : Module("Sprint", "Sprints automatically.", ModuleCategory.MOVEMENT) {
   companion object {
      @JvmField
      var forceSprintKeyDown: Boolean = false

      private var elytraFlyModule: ElytraFlyModule? = null
      private var longJumpModule: LongJumpModule? = null
      private var fastFallModule: FastFallModule? = null
      private var speedModule: SpeedModule? = null
      private var fastWebModule: FastWebModule? = null
      private var antiCheatModule: AntiCheatModule? = null
      private var noSlowModule: NoSlowModule? = null
      private var flightModule: FlightModule? = null

      @JvmStatic
      fun bindReferencedModules(registry: ModuleRegistry) {
         elytraFlyModule = registry.getModule(ElytraFlyModule::class.java) as ElytraFlyModule
         longJumpModule = registry.getModule(LongJumpModule::class.java) as LongJumpModule
         fastFallModule = registry.getModule(FastFallModule::class.java) as FastFallModule
         speedModule = registry.getModule(SpeedModule::class.java) as SpeedModule
         fastWebModule = registry.getModule(FastWebModule::class.java) as FastWebModule
         antiCheatModule = registry.getModule(AntiCheatModule::class.java) as AntiCheatModule
         noSlowModule = registry.getModule(NoSlowModule::class.java) as NoSlowModule
         flightModule = registry.getModule(FlightModule::class.java) as FlightModule
      }
   }

   val modeSetting: Setting<ModeChoice>
   private val stopInLiquidsSetting: Setting<Boolean>
   private val inBlocksSetting: Setting<Boolean>
   @JvmField
   val directionSpoofSetting: Setting<Boolean>
   @JvmField
   val keepSetting: Setting<Boolean>
   private val vanillaSlowdownSetting: Setting<Boolean>
   private val adjustSetting: Setting<Boolean>
   private val factorSetting: Setting<Float>
   val boostSetting: Setting<Boolean>
   private val boostSpeedSetting: Setting<Float>
   private val explosionBoostTimer: ElapsedTimer
   private var previousMoveVector: Vec2? = null
   private var explosionBoostSpeed: Float = 0.0f
   private var wasSprintingBeforeAttack: Boolean = false
   private var fallFlyingSinceGround: Boolean = false

   override fun getInfo(): String? {
      return FontRenderer.capitalize(this.modeSetting.value)
   }

   private fun updateDirectionSpoof(event: CancellableEvent) {
      if (!this.directionSpoofSetting.value || ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return
      }
      if (this.isWorldUnavailable()) {
         return
      }

      if (this.isSprintSuppressed()) {
         return
      }
      if (this.shouldDeferToVanillaSprinting()) {
         return
      }

      if (!MovementPhysics.isLocalPlayerMoving()) {
         return
      }
      if (PlayerStateUtils.isFallFlying()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.input.hasForwardImpulse()) {
         return
      }
      if (flightModule!!.isToggled()) {
         return
      }

      if (event is LocalPlayerSendPositionEvent) {
         val yaw: Float = MovementPhysics.getMovementYaw()
         event.yaw = yaw
      } else {
         val rotationManager: RotationManager = ClientContext.rotationManager
         val rotations = FloatArray(2)
         rotations[0] = MovementPhysics.getMovementYaw()
         rotations[1] = ClientAccess.collectiveMinecraft.player!!.getXRot()
         rotationManager.requestRotation(rotations, 0, true)

         ClientContext.rotationManager.useRequestedYawForPriority(0)
      }
   }

   @EventHandler(priority = 100)
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      if (this.modeSetting.value == SprintModule.ModeChoice.INSTANT) {
         this.updateDirectionSpoof(event)
      }
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return
      }
      if (!antiCheatModule!!.movementSyncSetting.value) {
         return
      }
      if (antiCheatModule!!.rotationsSetting.value != AntiCheatRotationsChoice.SILENT) {
         return
      }

      val rotationDifference = this.getRotationDifference()
      var hasLargeRotation = false
      if (rotationDifference >= 5.0f) {
         if (this.modeSetting.value != SprintModule.ModeChoice.LEGIT) {
            hasLargeRotation = true
         }
      }

      if (this.shouldDeferToVanillaSprinting() && !hasLargeRotation) {
         return
      }

      val request: RotationRequest? = ClientContext.rotationManager.getRequest()
      val hasForwardMovement: Boolean = ClientAccess.collectiveMinecraft.player!!.input.hasForwardImpulse() || hasLargeRotation
      val hasHigherPriority: Boolean = request != null && request.priority > 0
      if (!this.directionSpoofSetting.value || hasHigherPriority || !hasForwardMovement) {
         return
      }

      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val hitPosition: BlockPos = event.hitResult.blockPos
      if (level.getBlockEntity(hitPosition) == null) {
         val hand: InteractionHand = event.hand
         if (this.hasFoodComponentInHand(hand)) {
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val useHand: InteractionHand = event.hand
            gameMode.useItem(player, useHand)
            event.cancel()
            return
         }
      }

      val serverRotations: LocalPlayerTickAccess = ClientAccess.collectiveMinecraft.player!! as LocalPlayerTickAccess
      val x = ClientAccess.collectiveMinecraft.player!!.getX()
      val y = ClientAccess.collectiveMinecraft.player!!.getY()
      val z = ClientAccess.collectiveMinecraft.player!!.getZ()
      val yaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
      val pitch: Float = ClientAccess.collectiveMinecraft.player!!.getXRot()
      val onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()
      PacketSender.sendPositionRotation(x, y, z, yaw, pitch, onGround)
      val serverYaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
      serverRotations.`collective$updateServer`(serverYaw, ClientAccess.collectiveMinecraft.player!!.getXRot())
   }

   @EventHandler
   private fun onClientPacketListenerHandleExplosionEvent(event: ClientPacketListenerHandleExplosionEvent) {
      if (ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return
      }
      val explosionPosition = Vec3(event.centerX, event.centerY, event.centerZ)
      if (!MovementPhysics.isLocalPlayerMoving()) {
         return
      }
      if (!ClientContext.serverStateManager.getSetbackTimer().hasElapsed(750L)) {
         return
      }
      if (!this.boostSetting.value) {
         return
      }
      val squaredDistance: Double = ClientAccess.collectiveMinecraft.player!!.distanceToSqr(explosionPosition)
      val distance: Double = Math.sqrt(squaredDistance)
      if (distance > 6.0) {
         return
      }
      this.explosionBoostTimer.reset()
      val impulseX: Double = event.velocityX.toDouble()
      val impulseZ: Double = event.velocityZ.toDouble()
      val horizontalImpulse: Float = Math.hypot(impulseX, impulseZ).toFloat()
      val boostMultiplier: Float = this.boostSpeedSetting.value
      this.explosionBoostSpeed = horizontalImpulse * boostMultiplier
   }

   @EventHandler(priority = 1000)
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      if (PlayerStateUtils.isFallFlying()) {
         this.fallFlyingSinceGround = true
      } else {
         if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
            this.fallFlyingSinceGround = false
         }
      }
      if (ClientContext.baritoneBridge.isPathingOrCalculating() || this.isWorldUnavailable()) {
         return
      }
      if (this.isSprintSuppressed()) {
         return
      }
      if (this.modeSetting.value != SprintModule.ModeChoice.INSTANT) {
         return
      }
      if (PlayerStateUtils.isHungerLimited()) {
         return
      }
      if (PlayerStateUtils.isFallFlying()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.getAbilities().flying) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isInWater()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isInLava()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isShiftKeyDown()) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.isVisuallySwimming() || fastFallModule!!.suppressSprint) {
         return
      }
      if (longJumpModule!!.isToggled()) {
         return
      }
      if (speedModule!!.isToggled()) {
         return
      }
      if (!ClientAccess.collectiveMinecraft.player!!.onGround() && this.fallFlyingSinceGround) {
         return
      }

      val slowdownEnabled: Boolean = this.vanillaSlowdownSetting.value
      var movementSpeed: Double = MovementPhysics.getBaseMoveSpeed(slowdownEnabled)
      if (this.adjustSetting.value) {
         val multiplier: Float = this.factorSetting.value
         movementSpeed *= multiplier.toDouble()
      }

      if (ClientContext.serverStateManager.getSetbackTimer().hasElapsed(500L)) {
         val currentX: Double = event.x
         val currentZ: Double = event.z
         val currentSpeed: Double = Math.hypot(currentX, currentZ)
         movementSpeed = Math.max(movementSpeed, currentSpeed)
      }
      if (this.vanillaSlowdownSetting.value) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val velocityPosition: BlockPos = (ClientAccess.collectiveMinecraft.player!! as EntityAccessor).`collective$getVelocityAffectingPos`()
         val velocityBlock: Block = level.getBlockState(velocityPosition).block
         if (velocityBlock == Blocks.SLIME_BLOCK) {
            if (!noSlowModule!!.isSlimeSlowdownDisabled()) {
               movementSpeed *= 0.7
            }
         }
         movementSpeed = movementSpeed * velocityBlock.speedFactor.toDouble()
      }
      if (this.boostSetting.value) {
         if (!this.explosionBoostTimer.hasElapsed(500L)) {
            movementSpeed += this.explosionBoostSpeed.toDouble()
         }
      }
      MovementPhysics.applyHorizontalMotion(event, movementSpeed)
   }

   @EventHandler
   private fun onLocalPlayerAiStepEvent(event: LocalPlayerAiStepEvent) {
      if (ClientContext.baritoneBridge.isPathingOrCalculating() || this.isWorldUnavailable()) {
         return
      }
      if (this.isSprintSuppressed()) {
         return
      }
      if (this.shouldDeferToVanillaSprinting()) {
         return
      }
      if (RotationManager.isMovementSyncEnabled()) {
         if (this.directionSpoofSetting.value) {
            if (this.consumeMoveVectorChange() || ClientAccess.collectiveMinecraft.player!!.zza <= 0.0f) {
               this.setPlayerSprinting(false)
               return
            }
         }
      }
      val sprinting = this.canStartSprinting()
      this.setPlayerSprinting(sprinting)
   }

   @EventHandler
   private fun onPlayerAttackEvent(event: PlayerAttackEvent) {
      if (!ClientContext.baritoneBridge.isPathingOrCalculating()) {
         if (this.keepSetting.value) {
            if (event.stage == EventStage.Pre) {
               val sprintPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               this.wasSprintingBeforeAttack = sprintPlayer.isSprinting()
               if (this.wasSprintingBeforeAttack) {
                  val packetPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                  PacketSender.sendPlayerCommand(packetPlayer, Action.STOP_SPRINTING)
               }
            } else if (this.wasSprintingBeforeAttack) {
               val sprintPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               sprintPlayer.setSprinting(true)
               val packetPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               PacketSender.sendPlayerCommand(packetPlayer, Action.START_SPRINTING)
            }
         }
      }
   }

   private fun hasWaterBelowWhileAirborne(): Boolean {
      if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
         return false
      }

      val eyeLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.getEyePosition()
      val eyeBlockPosition: BlockPos = BlockPos.containing(eyePosition)
      if (eyeLevel.isWaterAt(eyeBlockPosition)) {
         return false
      }

      val expandedBounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox().expandTowards(0.0, -0.1, 0.0)
      val positions: Stream<BlockPos> = BlockPos.betweenClosedStream(expandedBounds)
      val isWater = Predicate<BlockPos> { position: BlockPos -> ClientAccess.collectiveMinecraft.level!!.isWaterAt(position) }
      return positions.anyMatch(isWater)
   }

   private fun isSprintSuppressed(): Boolean {
      if (ClientContext.inputOverrides.isSprintBlocked()) {
         return true
      }
      if (noSlowModule!!.isUsingItemInConstantiamMode()) {
         return true
      }
      if (ClientAccess.collectiveMinecraft.player!!.isShiftKeyDown()) {
         return true
      }
      if (ClientContext.baritoneBridge.isAvailable() &&
         ClientContext.baritoneBridge.isPathingOrCalculating()) {
         return true
      }
      if (PlayerStateUtils.isInLiquid(ClientAccess.collectiveMinecraft.player!!)) {
         if (this.stopInLiquidsSetting.value) {
            return true
         }
      }
      if (elytraFlyModule!!.isToggled()) {
         if (elytraFlyModule!!.modeSetting.value == ElytraFlyModule.ModeChoice.BOUNCE) {
            if (elytraFlyModule!!.isElytraEquipped()) {
               return true
            }
         }
      }
      return false
   }

   fun shouldDeferToVanillaSprinting(): Boolean {
      if (ClientContext.inputOverrides.isSprintBlocked()) {
         return false
      }
      val player = ClientAccess.collectiveMinecraft.player!!
      if (antiCheatModule!!.is2b2tEnabled() &&
         player != null &&
         player.containerMenu != player.inventoryMenu) {
         return true
      }
      if (this.isSprintInBlocksRestricted()) {
         if (WallsVelocityHandler.hasUsableGroundCollision()) {
            return true
         }
      }
      if (this.fallFlyingSinceGround) {
         return true
      }
      val rotationManager: RotationManager = ClientContext.rotationManager
      var hasUnreadyRotation = false
      if (rotationManager.getRequest() != null) {
         if (!ClientContext.rotationManager.isRequestedYawUsedForMovement()) {
            hasUnreadyRotation = true
         }
      }
      if (this.directionSpoofSetting.value) {
         val mode: Setting<ModeChoice> = this.modeSetting
         if (mode.value == SprintModule.ModeChoice.RAGE && hasUnreadyRotation) {
            return true
         }
      }
      val mode: Setting<ModeChoice> = this.modeSetting
      return mode.value == SprintModule.ModeChoice.LEGIT
   }

   fun setPlayerSprinting(sprinting: Boolean) {
      try {
         ClientAccess.collectiveMinecraft.player!!.setSprinting(sprinting)
      } catch (ignoredFailure: Exception) {
      }
   }

   private fun consumeMoveVectorChange(): Boolean {
      val currentVector: Vec2 = ClientAccess.collectiveMinecraft.player!!.input.moveVector
      val changed: Boolean
      if (this.previousMoveVector != null) {
         changed = !currentVector.equals(this.previousMoveVector)
      } else {
         changed = true
      }
      this.previousMoveVector = currentVector
      return changed
   }

   private fun getRotationDifference(): Float {
      val rotations: FloatArray = ClientContext.playerStateTracker.getPacketRotations()
      val sourceYaw: Float = rotations[0]
      val playerYaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
      val yawDifference: Float = Mth.degreesDifferenceAbs(sourceYaw, playerYaw)
      val sourcePitch: Float = rotations[1]
      val pitchDifference: Float = sourcePitch - ClientAccess.collectiveMinecraft.player!!.getXRot()
      return yawDifference + Math.abs(pitchDifference)
   }

   private fun hasMovementSpeedFactorChange(): Boolean {
      val inputVector: Vec2 = ClientAccess.collectiveMinecraft.player!!.input.moveVector
      val adjustedVector: Vec2 = me.collective.client.utils.movement.MovementInputProcessor.applyMovementSpeedFactors(
         ClientAccess.collectiveMinecraft.player!!,
         inputVector
      )
      val inputLength: Float = inputVector.length()
      // Сравниваем длины с длинами (раньше смешивались length и lengthSquared).
      val lengthDifference: Float = inputLength - adjustedVector.length()
      val absoluteDifference: Double = Math.abs(lengthDifference.toDouble())
      return absoluteDifference > 0.05
   }

   fun isSprintInBlocksRestricted(): Boolean {
      if (this.inBlocksSetting.isVisible()) {
         return !this.inBlocksSetting.value
      }
      return false
   }

   fun canStartSprinting(): Boolean {
      if (this.isSprintSuppressed()) {
         return false
      }
      if (this.directionSpoofSetting.value) {
         if (PlayerStateUtils.isFallFlying()) {
            return false
         }
      }
      if (PlayerStateUtils.isHungerLimited()) {
         return false
      }
      if (this.hasWaterBelowWhileAirborne()) {
         return false
      }
      if (this.shouldDeferToVanillaSprinting()) {
         return false
      }
      if (ClientAccess.collectiveMinecraft.player!!.horizontalCollision && !ClientAccess.collectiveMinecraft.player!!.minorHorizontalCollision) {
         return false
      }
      if (this.hasMovementSpeedFactorChange()) {
         return true
      }
      if (fastWebModule!!.isGrimEnabled()) {
         if (PlayerStateUtils.isInCobweb(ClientAccess.collectiveMinecraft.player!!)) {
            val ticks = ClientContext.playerStateTracker.getGroundedTicks()
            if (ticks < 2) {
               return false
            }
            if (ClientAccess.collectiveMinecraft.player!!.input.keyPresses.jump()) {
               return false
            }
         }
      }
      return ClientAccess.collectiveMinecraft.player!!.input.getMoveVector().lengthSquared() > 0.0f
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundPlayerPositionPacket) {
         this.explosionBoostSpeed = 0.0f
      }
   }

   @EventHandler(priority = 100)
   private fun onDirectionUpdateEvent(event: RotationRequestEvent) {
      if (this.modeSetting.value != SprintModule.ModeChoice.INSTANT) {
         this.updateDirectionSpoof(event)
      }
   }

   private fun hasFoodComponentInHand(hand: InteractionHand): Boolean {
      return ClientAccess.collectiveMinecraft.player!!.getItemInHand(hand).has(DataComponents.FOOD)
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.RAGE))
      this.stopInLiquidsSetting = this.add(BooleanSetting("StopInLiquids", true))
      val inBlocksVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.RAGE }
      this.inBlocksSetting = this.add(BooleanSetting("InBlocks", true, inBlocksVisibility))
      this.directionSpoofSetting = this.add(BooleanSetting("DirectionSpoof", false))
      val keepVisibility = Predicate<Any> { ignored -> this.modeSetting.value != ModeChoice.INSTANT }
      this.keepSetting = this.add(BooleanSetting("Keep", true, keepVisibility))
      val slowdownVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.INSTANT }
      this.vanillaSlowdownSetting = this.add(BooleanSetting("VanillaSlowdown", false, slowdownVisibility))
      val adjustVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.INSTANT }
      this.adjustSetting = this.add(BooleanSetting("Adjust", false, adjustVisibility).markVisibilityDependency())
      val factorVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.INSTANT }
      this.factorSetting = this.add(NumberSetting<Float>("Factor", 1.0f, 0.5f, 2.0f, factorVisibility).dependsOn(this.adjustSetting))
      val boostVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.INSTANT }
      this.boostSetting = this.add(BooleanSetting("Boost", false, boostVisibility).markVisibilityDependency())
      val boostSpeedVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.INSTANT && this.boostSetting.isExpanded() }
      this.boostSpeedSetting = this.add(NumberSetting<Float>("BoostSpeed", 0.5f, 0.1f, 1.0f, boostSpeedVisibility))
      this.explosionBoostTimer = ElapsedTimer()
      this.previousMoveVector = null
      this.explosionBoostSpeed = 0.0f

      val stopVisibilitySetting: Setting<Boolean> = this.stopInLiquidsSetting
      val stopVisibility = Predicate<Boolean> { ignored -> this.modeSetting.value != SprintModule.ModeChoice.LEGIT }
      stopVisibilitySetting.addVisibilityPredicate(stopVisibility)
      val directionVisibilitySetting: Setting<Boolean> = this.directionSpoofSetting
      val directionVisibility = Predicate<Boolean> { ignored -> this.modeSetting.value != SprintModule.ModeChoice.LEGIT }
      directionVisibilitySetting.addVisibilityPredicate(directionVisibility)
      this.adjustSetting.disableNumericBounds()
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      LEGIT("Vanilla"),
      RAGE("Rage"),
      INSTANT("Instant");

      override fun getName(): String {
         return this.displayName
      }
   }
}
