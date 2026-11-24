package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.LivingEntityRotationEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.FireworkRocketEntityTickEvent
import me.collective.client.events.KeyboardInputTickEvent
import me.collective.client.events.LocalPlayerAiStepEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemEvent
import me.collective.client.events.SoundEnginePlayEvent
import me.collective.client.integrations.ProtocolCompatibility
import me.collective.client.integrations.ProtocolVersionAccess
import me.collective.client.managers.RotationManager
import me.collective.client.utils.movement.ControlElytraFlyHandler
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.movement.VanillaElytraFlyHandler
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.render.FontRenderer
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.movement.BounceElytraFlyHandler
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.movement.NewGrimElytraFlyHandler
import me.collective.client.utils.movement.ElytraFlyModeHandler
import me.collective.client.utils.movement.BoostElytraFlyHandler
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.misc.MiddleClickModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import me.collective.mixin.MinecraftAccessor
import java.util.function.Predicate
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.resources.Identifier
import net.minecraft.tags.BlockTags
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Input
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

class ElytraFlyModule : Module("ElytraFly", "Turns you into a block game boeing.", ModuleCategory.MOVEMENT, "elytraflight") {
   companion object {
      @JvmField
      var hook: Boolean = false

      private var middleClickModule: MiddleClickModule? = null
      private var obstaclePasserModule: ObstaclePasserModule? = null
      private var noSlowModule: NoSlowModule? = null
      private var antiCheatModule: AntiCheatModule? = null

      @JvmStatic
      fun bindModuleReferences(registry: ModuleRegistry) {
         middleClickModule = registry.getModule(
            MiddleClickModule::class.java
         ) as MiddleClickModule
         obstaclePasserModule = registry.getModule(
            ObstaclePasserModule::class.java
         ) as? ObstaclePasserModule
         noSlowModule = registry.getModule(
            NoSlowModule::class.java
         ) as NoSlowModule
         antiCheatModule = registry.getModule(
            AntiCheatModule::class.java
         ) as AntiCheatModule
         BoostElytraFlyHandler.bindModuleReferences(registry)
         ControlElytraFlyHandler.bindModuleReferences(registry)
         BounceElytraFlyHandler.bindModuleReferences(registry)
         NewGrimElytraFlyHandler.bindModuleReferences(registry)
         VanillaElytraFlyHandler.bindModuleReferences(registry)
      }
   }

   @JvmField
   val modeSetting: Setting<ModeChoice>
   @JvmField
   val takeoffSetting: Setting<TakeoffChoice>
   @JvmField
   val responsiveSetting: Setting<Boolean>
   @JvmField
   val factorizeSetting: Setting<Boolean>
   @JvmField
   val alwaysSetting: Setting<Boolean>
   @JvmField
   val nCPBoostSetting: Setting<Boolean>
   @JvmField
   val minBoostSetting: Setting<Float>
   @JvmField
   val limitSetting: Setting<Float>
   @JvmField
   val autoJumpSetting: Setting<Boolean>
   @JvmField
   val inLavaSetting: Setting<Boolean>
   @JvmField
   val speedSetting: Setting<Float>
   @JvmField
   val minYSetting: Setting<Int>
   @JvmField
   val heightSetting: Setting<Int>
   @JvmField
   val useFireworkSetting: Setting<Boolean>
   @JvmField
   val flightSpeedSetting: Setting<Float>
   @JvmField
   val verticalSetting: Setting<VerticalChoice>
   @JvmField
   val vSpeedSetting: Setting<Float>
   @JvmField
   val vPitchSetting: Setting<Float>
   @JvmField
   val glideSetting: Setting<Float>
   @JvmField
   val spoofPitchSetting: Setting<Boolean>
   @JvmField
   val spoofedPitchSetting: Setting<Float>
   @JvmField
   val antiAfkSetting: Setting<Boolean>
   @JvmField
   val packetVerticalSetting: Setting<Boolean>
   @JvmField
   val accelerateSetting: Setting<Boolean>
   @JvmField
   val verAccelMinSetting: Setting<Float>
   @JvmField
   val accelMinSetting: Setting<Float>
   @JvmField
   val accelTimeSetting: Setting<Float>
   @JvmField
   val startSetting: Setting<Float>
   @JvmField
   val strictSpeedSetting: Setting<Float>
   @JvmField
   val deployTimeSetting: Setting<Int>
   @JvmField
   val pitchLockSetting: Setting<Boolean>
   @JvmField
   val autoPitchSetting: Setting<Boolean>
   @JvmField
   val silentSetting: Setting<Boolean>
   @JvmField
   val pitchSetting: Setting<Float>
   @JvmField
   val packetSetting: Setting<Boolean>
   @JvmField
   val diagonalSetting: Setting<Boolean>
   @JvmField
   val maxBoostSetting: Setting<Int>
   @JvmField
   val compensateSetting: Setting<Boolean>
   @JvmField
   val stopMotionSetting: Setting<StopMotionChoice>
   @JvmField
   val fallChestplateSetting: Setting<Boolean>
   @JvmField
   val responsiveDescendSetting: Setting<Boolean>
   @JvmField
   val muteElytraSetting: Setting<Boolean>
   @JvmField
   val infDurabilitySetting: Setting<InfDurabilityChoice>
   private val modeHandlers: EnumSelectionMap<ElytraFlyModeHandler>
   @JvmField
   val fireworkUseTimer: ElapsedTimer
   @JvmField
   val takeoffTimer: ElapsedTimer
   private val durabilitySwapTimer: ElapsedTimer
   @JvmField
   val enableTimer: ElapsedTimer
   private val elytraSearchTimer: ElapsedTimer
   @JvmField
   var fireworkUsePending: Boolean = false
   private var replayingFireworkUse: Boolean = false
   private var jumpPressed: Boolean = false
   private var sneakPressed: Boolean = false
   private var takeoffRequested: Boolean = false
   private var takeoffJumpPhase: TakeoffJumpPhase? = null

   override fun getInfo(): String? {
      return try {
         FontRenderer.capitalize(this.modeSetting.value.name)
      } catch (failure: Exception) {
         null
      }
   }

   override fun onEnable() {
      this.enableTimer.clear()
      if (!this.isWorldUnavailable()) {
         this.getActiveMode().onEnable()
      }
   }

   override fun onDisable() {
      try {
         if (!this.isWorldUnavailable()) {
            this.getActiveMode().onDisable()
         }
      } finally {
         ClientContext.tickRateController.releaseMultiplier(this)
         this.fireworkUseTimer.reset()
      }
   }

   val activeMode: ElytraFlyModeHandler
      @JvmName("getActiveModeProp") get() = getActiveMode()

   fun getActiveMode(): ElytraFlyModeHandler {
      return this.modeHandlers.getSelected()
   }

   private fun startFallFlyingWithPacket() {
      if (this.enableTimer.hasElapsed(500L) &&
         !this.infDurabilitySetting.isVisible() && !this.isBouncePacketMode()) {
         return
      }
      PacketSender.sendStartFallFlying()
      ClientAccess.collectiveMinecraft.player!!.startFallFlying()
   }

   private fun isBouncePacketMode(): Boolean {
      return this.modeSetting.value == ModeChoice.BOUNCE && this.packetSetting.value
   }

   fun tryStartNormalFlight(): Boolean {
      val obstaclePasserSnapshot = obstaclePasserModule
      if (!(this.infDurabilitySetting.value).isDisabled() ||
         this.isBouncePacketMode() || ClientContext.baritoneBridge.isPathingOrCalculating() ||
         obstaclePasserSnapshot != null && obstaclePasserSnapshot.isPassingObstacles()) {
         return false
      }
      if (!this.canStartFlightFromJump()) {
         return false
      }
      ClientAccess.collectiveMinecraft.execute(Runnable {
         val currentPlayer = ClientAccess.collectiveMinecraft.player ?: return@Runnable
         currentPlayer.startFallFlying()
         PacketSender.sendStartFallFlying()
      })
      return true
   }

   fun updateTakeoff() {
      this.takeoffRequested = true
   }

   private fun processTakeoff() {
      if (this.modeSetting.value == ModeChoice.BOUNCE) {
         return
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (ClientContext.baritoneBridge.isPathingOrCalculating() || !this.isElytraEquipped() ||
         player.isFallFlying || EntityUtils.hasCollisionBelow(player) ||
         this.takeoffSetting.value == TakeoffChoice.NONE && !this.takeoffRequested) {
         this.takeoffJumpPhase = null
         ClientContext.tickRateController.releaseMultiplier(this)
         return
      }
      if (player.isInWater) {
         return
      }
      if (this.takeoffSetting.value == TakeoffChoice.STRICT) {
         ClientContext.tickRateController.requestMultiplier(this, 0.1f)
      }
      val deployDelayMillis = if (this.modeSetting.value == ModeChoice.STRICT)
         this.deployTimeSetting.value.toLong() else 100L
      if (!this.takeoffTimer.hasElapsedAndReset(deployDelayMillis)) {
         return
      }
      val targetVersion: ProtocolVersionAccess? = ProtocolCompatibility.getTargetVersion()
      val legacyProtocol: Boolean = targetVersion != null && targetVersion.olderEq_1_21_5()
      if (!legacyProtocol && RotationManager.isMovementSyncEnabled()) {
         val lastInput: Input? = ClientContext.playerStateTracker.getLastSentInput()
         this.takeoffJumpPhase = if (lastInput != null && lastInput.jump())
            TakeoffJumpPhase.RELEASE else TakeoffJumpPhase.PRESS
      } else {
         player.startFallFlying()
         PacketSender.sendStartFallFlying()
      }
      this.takeoffRequested = false
   }

   fun isElytraEquipped(): Boolean {
      return PlayerStateUtils.isWearingElytra(ClientAccess.collectiveMinecraft.player!!)
   }

   private fun updateDurabilitySwap() {
      if (FireworksModule.temporaryElytraSwapActive.get()) {
         return
      }
      val activeHandler = this.getActiveMode()
      if (activeHandler is BounceElytraFlyHandler && activeHandler.isDiagonalPacketPending()) {
         return
      }
      var elytraSlot = InventoryUtils.findHotbarSlot(Items.ELYTRA)
      val inventorySwapRequired = elytraSlot == -1
      if (inventorySwapRequired) {
         elytraSlot = InventoryUtils.findMenuSlot(Items.ELYTRA)
      }
      val durabilityMode = this.infDurabilitySetting.value
      val obstaclePasserSnapshot = obstaclePasserModule
      if (!this.shouldSwapForDurability(durabilityMode, inventorySwapRequired) ||
         !this.durabilitySwapTimer.hasElapsed(200L) || !this.isDurabilityBypassActive() ||
         obstaclePasserSnapshot != null && obstaclePasserSnapshot.isPassingObstacles()) {
         return
      }
      val clicksRequired = if (inventorySwapRequired) 4 else 2
      if (InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, clicksRequired)) {
         return
      }
      if (this.isElytraEquipped()) {
         ClientAccess.collectiveMinecraft.player!!.startFallFlying()
         PacketSender.sendStartFallFlying()
         return
      }
      if (elytraSlot == -1) {
         return
      }
      val originalMenuSlot = elytraSlot
      if (inventorySwapRequired) {
         elytraSlot = 0
         InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP)
      }

      InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP)
      try {
         this.startFallFlyingWithPacket()
         if (this.fireworkUsePending && this.fireworkUseTimer.hasElapsedAndReset(150L)) {
            this.replayingFireworkUse = true
            try {
               if (middleClickModule!!.isToggled()) {
                  middleClickModule!!.launchFirework()
               } else {
                  (ClientAccess.collectiveMinecraft as MinecraftAccessor).interact()
               }
            } finally {
               this.replayingFireworkUse = false
            }
            this.fireworkUsePending = false
            this.durabilitySwapTimer.reset()
         }
      } finally {
         InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP)
         if (inventorySwapRequired) {
            InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP)
         }
      }
   }

   fun isFallFlyingWithoutOverride(): Boolean {
      val previousHook = hook
      hook = true
      try {
         return ClientAccess.collectiveMinecraft.player!!.isFallFlying
      } finally {
         hook = previousHook
      }
   }

   private fun isInClimbableBlock(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      return !player.isSpectator && player.inBlockState.`is`(BlockTags.CLIMBABLE)
   }

   fun isManualVerticalControlActive(): Boolean {
      return !this.isWorldUnavailable() && (this.jumpPressed || this.sneakPressed) &&
         this.verticalSetting.value == VerticalChoice.MANUAL &&
         ClientAccess.collectiveMinecraft.player!!.isFallFlying && this.isElytraEquipped()
   }

   fun isItemUseSlowingFlight(): Boolean {
      return !antiCheatModule!!.is2b2tEnabled() && ClientAccess.collectiveMinecraft.player!!.isUsingItem() &&
         (!noSlowModule!!.isToggled() || !noSlowModule!!.itemsSetting.value)
   }

   fun canStartFlightFromJump(): Boolean {
      val obstaclePasserSnapshot = obstaclePasserModule
      if (ClientContext.baritoneBridge.isPathingOrCalculating() ||
         obstaclePasserSnapshot != null && obstaclePasserSnapshot.isPassingObstacles()) {
         return false
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      return !player.abilities.flying && !player.isPassenger && !this.isInClimbableBlock() &&
         this.isElytraEquipped() && !player.isInWater && !player.hasEffect(MobEffects.LEVITATION) &&
         player.input.keyPresses.jump()
   }

   @EventHandler(priority = -300)
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      this.updateDurabilitySwap()
      this.getActiveMode().handlePrePlayerTick(event)
   }

   @EventHandler
   private fun onFireworkRocketEntityTickEvent(event: FireworkRocketEntityTickEvent) {
      this.getActiveMode().handleFireworkTick(event)
   }

   @EventHandler
   private fun onSoundEnginePlayEvent(event: SoundEnginePlayEvent) {
      if (this.isDurabilityBypassActive() && event.soundInstance != null) {
         val identifier: Identifier? = event.soundInstance.getIdentifier()
         if (identifier != null && identifier.toString().contains("item.armor.equip")) {
            event.cancel()
         }
      }
   }

   @EventHandler
   private fun onLocalPlayerAiStepEvent(event: LocalPlayerAiStepEvent) {
      if (this.isFallFlyingWithoutOverride() && this.modeSetting.value == ModeChoice.BOUNCE) {
         ClientAccess.collectiveMinecraft.player!!.setSprinting(true)
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      this.getActiveMode().handlePlayerTick(event)
   }

   private fun shouldSwapForDurability(durabilityMode: InfDurabilityChoice, inventorySwapRequired: Boolean): Boolean {
      if (!PlayerStateUtils.isLocalWearingElytra() && !InventoryUtils.getCarriedStack().isEmpty) {
         return false
      }
      if (this.isBouncePacketMode() && PlayerStateUtils.isLocalWearingElytra()) {
         return true
      }
      return when (durabilityMode) {
         InfDurabilityChoice.NONE -> false
         InfDurabilityChoice.PLAIN -> true
         InfDurabilityChoice.GRIM -> !PlayerStateUtils.isFallFlying()
         InfDurabilityChoice._2B2T -> {
            if (PlayerStateUtils.isLocalWearingElytra()) {
               true
            } else if (inventorySwapRequired) {
               false
            } else if (this.modeSetting.value == ModeChoice.BOUNCE && this.pitchLockSetting.value &&
         this.pitchSetting.isAtMaximum()) {
               val airborneTicks = ClientContext.playerStateTracker.getAirborneTicks()
               ClientAccess.collectiveMinecraft.player!!.onGround() || airborneTicks == 0 ||
         airborneTicks == 1 || airborneTicks == 6
            } else {
               val tick = ClientAccess.collectiveMinecraft.player!!.tickCount
               tick % 2 != 0 && tick % 20 != 1
            }
         }
      }
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
      val outgoingPacket = event.packet
      if (outgoingPacket is ServerboundPlayerActionPacket &&
         outgoingPacket.action == Action.RELEASE_USE_ITEM && antiCheatModule!!.is2b2tEnabled()) {
         this.durabilitySwapTimer.reset()
      }
      this.getActiveMode().handleOutgoingPacket(event)
   }

   @EventHandler(priority = -200)
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      this.getActiveMode().handleMovement(event)
   }

   @EventHandler
   private fun handleTravelRotation(event: LivingEntityRotationEvent) {
      this.getActiveMode().handleTravelRotation(event)
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      this.getActiveMode().handleClientTick(event)
   }

   fun isPacketFlightActive(): Boolean {
      if (this.isWorldUnavailable() || ClientAccess.collectiveMinecraft.player!!.input == null) {
         return false
      }
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      return !(this.packetVerticalSetting.value && player.input.keyPresses.jump()) &&
         this.isToggled() && !player.onGround() && this.modeSetting.value == ModeChoice.PACKET &&
         this.isElytraEquipped()
   }

   fun isDurabilityBypassActive(): Boolean {
      if (this.isWorldUnavailable() || ClientContext.baritoneBridge.isPathingOrCalculating() || hook) {
         return false
      }
      val mode = this.modeSetting.value
      if (EntityUtils.hasCollisionBelow(ClientAccess.collectiveMinecraft.player!!) &&
         (mode == ModeChoice.NEWGRIM || mode == ModeChoice.CONTROL)) {
         return false
      }
      var elytraSlot = InventoryUtils.findHotbarSlot(Items.ELYTRA)
      if (elytraSlot == -1) {
         elytraSlot = InventoryUtils.findMenuSlot(Items.ELYTRA)
      } else {
         this.elytraSearchTimer.reset()
      }
      if (elytraSlot == -1 && this.elytraSearchTimer.hasElapsed(150L) &&
         !PlayerStateUtils.isLocalWearingElytra()) {
         return false
      }
      val durabilityEnabled = !(this.infDurabilitySetting.value).isDisabled() &&
         this.infDurabilitySetting.isVisible()
      return this.isToggled() && (durabilityEnabled || this.isBouncePacketMode())
   }

   @EventHandler
   private fun onKeyboardInputTickEvent(event: KeyboardInputTickEvent) {
      this.jumpPressed = event.input.keyPresses.jump()
      this.sneakPressed = event.input.keyPresses.shift()
      if (!this.isElytraEquipped()) {
         return
      }
      if (this.modeSetting.value != ModeChoice.PACKET ||
         this.packetVerticalSetting.value && this.jumpPressed) {
         this.processTakeoff()
      } else {
         ClientContext.tickRateController.releaseMultiplier(this)
      }
      val takeoffPhase = this.takeoffJumpPhase
      if (takeoffPhase != null) {
         when (takeoffPhase) {
            TakeoffJumpPhase.RELEASE -> {
               ClientContext.inputOverrides.requestJump(false)
               this.takeoffJumpPhase = TakeoffJumpPhase.PRESS
            }
            TakeoffJumpPhase.PRESS -> {
               ClientContext.inputOverrides.requestJump(true)
               this.takeoffJumpPhase = TakeoffJumpPhase.FINISH
            }
            TakeoffJumpPhase.FINISH -> {
               ClientContext.inputOverrides.requestJump(false)
               this.takeoffJumpPhase = null
            }
         }
      }
   }

   private enum class TakeoffJumpPhase {
      RELEASE, PRESS, FINISH
   }

   @EventHandler
   private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
      this.getActiveMode().handleSendPosition(event)
   }

   @EventHandler
   private fun onUseItem(event: MultiPlayerGameModeUseItemEvent) {
      if (!this.isDurabilityBypassActive() || this.replayingFireworkUse) {
         return
      }
      val heldStack: ItemStack = ClientAccess.collectiveMinecraft.player!!.getItemInHand(event.hand)
      if (heldStack.`is`(Items.FIREWORK_ROCKET) && !PlayerStateUtils.isLocalWearingElytra()) {
         this.fireworkUsePending = true
         event.cancel()
      }
   }

   @EventHandler
   private fun handleRotationManagerEvent(event: RotationRequestEvent) {
      this.getActiveMode().handleRotationManagerEvent(event)
   }

   @EventHandler
   private fun onIncomingPacket(event: PacketEvent.Receive) {
      val incomingPacket = event.packet
      if (incomingPacket is ClientboundAddEntityPacket && incomingPacket.type == EntityType.FIREWORK_ROCKET) {
         val rocketPosition = Vec3(incomingPacket.getX(), incomingPacket.getY(), incomingPacket.getZ())
         if (rocketPosition.distanceToSqr(ClientAccess.collectiveMinecraft.player!!.position()) <= 36.0) {
            this.durabilitySwapTimer.reset()
         }
      }
      this.getActiveMode().handleIncomingPacket(event)
   }

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ElytraFlyModule.ModeChoice.CONTROL))
      val elytraFlyTakeoffPredicate = Predicate<Any> { ignored -> this.modeSetting.value != ModeChoice.PACKET }
      this.takeoffSetting = this.add(EnumSetting<TakeoffChoice>("Takeoff", ElytraFlyModule.TakeoffChoice.PLAIN, elytraFlyTakeoffPredicate))
      val boostVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.BOOST }
      this.responsiveSetting = this.add(BooleanSetting("Responsive", true, boostVisibility))
      this.factorizeSetting = this.add(BooleanSetting("Factorize", false, boostVisibility))
      this.alwaysSetting = this.add(BooleanSetting("Always", false, boostVisibility))
      this.nCPBoostSetting = this.add(BooleanSetting("NCPBoost", false, boostVisibility))
      this.minBoostSetting = this.add(NumberSetting<Float>("MinBoost", 1.5f, 0.0f, 5.0f, boostVisibility))
      this.limitSetting = this.add(NumberSetting<Float>("Limit", 500.0f, 20.0f, 500.0f, boostVisibility).withUnit("km/h"))
      this.autoJumpSetting = this.add(BooleanSetting("AutoJump", false, boostVisibility))
      this.inLavaSetting = this.add(BooleanSetting("InLava", true, boostVisibility).markVisibilityDependency())
      val lavaSpeedVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.BOOST && this.inLavaSetting.isExpanded() }
      this.speedSetting = this.add(NumberSetting<Float>("Speed", 1.0f, 0.0f, 2.0f, lavaSpeedVisibility))
      val vanillaVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.VANILLA }
      this.minYSetting = this.add(NumberSetting<Int>("MinY", 100, 0, 500, vanillaVisibility))
      this.heightSetting = this.add(NumberSetting<Int>("Height", 35, 20, 150, vanillaVisibility))
      this.useFireworkSetting = this.add(BooleanSetting("UseFirework", false, vanillaVisibility))
      val elytraFlySpeedPredicate = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.CONTROL ||
         this.modeSetting.value == ModeChoice.PACKET }
      this.flightSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.6f, 0.1f, 10.0f, elytraFlySpeedPredicate))
      val controlVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.CONTROL }
      this.verticalSetting = this.add(EnumSetting<VerticalChoice>("Vertical", ElytraFlyModule.VerticalChoice.NONE, controlVisibility))
      val elytraFlyVSpeedPredicate = Predicate<Any> { ignored -> this.verticalSetting.value == ElytraFlyModule.VerticalChoice.PLAIN &&
         this.modeSetting.value == ModeChoice.CONTROL }
      this.vSpeedSetting = this.add(NumberSetting<Float>("VSpeed", 0.6f, 0.0f, 15.0f, elytraFlyVSpeedPredicate))
      val elytraFlyVPitchPredicate = Predicate<Any> { ignored -> this.verticalSetting.value == ElytraFlyModule.VerticalChoice.STRICT &&
         this.modeSetting.value == ModeChoice.CONTROL }
      this.vPitchSetting = this.add(NumberSetting<Float>("VPitch", 45.0f, 0.0f, 90.0f, elytraFlyVPitchPredicate))
      this.glideSetting = this.add(NumberSetting<Float>("Glide", 0.0f, 0.0f, 5.0f, elytraFlySpeedPredicate))
      this.spoofPitchSetting = this.add(BooleanSetting("SpoofPitch", false, controlVisibility).markVisibilityDependency())
      val spoofedPitchVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.CONTROL && this.spoofPitchSetting.isExpanded() }
      this.spoofedPitchSetting = this.add(NumberSetting<Float>("Pitch", 0.5f, -90.0f, 90.0f, spoofedPitchVisibility))
      val packetVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.PACKET }
      this.antiAfkSetting = this.add(BooleanSetting("AntiAfk", true, packetVisibility))
      this.packetVerticalSetting = this.add(BooleanSetting("Vertical", false, packetVisibility))
      val elytraFlyAcceleratePredicate = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.PACKET ||
         this.modeSetting.value == ModeChoice.CONTROL }
      this.accelerateSetting = this.add(BooleanSetting("Accelerate", false, elytraFlyAcceleratePredicate).markVisibilityDependency())
      this.verAccelMinSetting = this.add(NumberSetting<Float>("VerAccelMin", 1.0f, 0.0f, 5.0f, elytraFlyAcceleratePredicate).dependsOn(this.accelerateSetting))
      this.accelMinSetting = this.add(NumberSetting<Float>("AccelMin", 1.0f, 0.0f, 5.0f, elytraFlyAcceleratePredicate).dependsOn(this.accelerateSetting))
      this.accelTimeSetting = this.add(NumberSetting<Float>("AccelTime", 1.0f, 0.1f, 10.0f, elytraFlyAcceleratePredicate).withUnit("s").dependsOn(this.accelerateSetting))
      val strictVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.STRICT }
      this.startSetting = this.add(NumberSetting<Float>("Start", 1.8f, 0.0f, 5.0f, strictVisibility))
      this.strictSpeedSetting = this.add(NumberSetting<Float>("Speed", 1.8f, 0.1f, 5.0f, strictVisibility))
      this.deployTimeSetting = this.add(NumberSetting<Int>("DeployTime", 400, 50, 1000, strictVisibility).withUnit("ms"))
      val bounceVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.BOUNCE }
      this.pitchLockSetting = this.add(BooleanSetting("PitchLock", false, bounceVisibility).markVisibilityDependency())
      val elytraFlySilentPredicate = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.BOUNCE && this.pitchLockSetting.isExpanded() }
      this.silentSetting = this.add(BooleanSetting("Silent", true, elytraFlySilentPredicate))
      this.autoPitchSetting = this.add(BooleanSetting("Auto", false, elytraFlySilentPredicate))
      val manualPitchVisible = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.BOUNCE &&
         this.pitchLockSetting.isExpanded() && !this.autoPitchSetting.value }
      this.pitchSetting = this.add(NumberSetting<Float>("Pitch", 90.0f, -90.0f, 90.0f, manualPitchVisible))
      this.packetSetting = this.add(BooleanSetting("Packet", false, bounceVisibility).markVisibilityDependency())
      this.diagonalSetting = this.add(BooleanSetting("Diagonal", false, bounceVisibility).dependsOn(this.packetSetting))
      this.maxBoostSetting = this.add(NumberSetting<Int>("MaxBoost", 300, 40, 999, bounceVisibility).withUnit("km/h").dependsOn(this.packetSetting))
      this.compensateSetting = this.add(BooleanSetting("Compensate", true, bounceVisibility))
      val newGrimVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.NEWGRIM }
      this.stopMotionSetting = this.add(EnumSetting<StopMotionChoice>("StopMotion", ElytraFlyModule.StopMotionChoice.NONE, newGrimVisibility))
      this.fallChestplateSetting = this.add(BooleanSetting("FallChestplate", false, newGrimVisibility))
      this.responsiveDescendSetting = this.add(BooleanSetting("ResponsiveDescend", false, newGrimVisibility))
      this.muteElytraSetting = this.add(BooleanSetting("MuteElytra", false))
      val elytraFlyInfDurabilityPredicate = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.BOOST ||
         this.modeSetting.value == ModeChoice.NEWGRIM ||
         this.modeSetting.value == ModeChoice.BOUNCE ||
         this.modeSetting.value == ModeChoice.CONTROL ||
         this.modeSetting.value == ModeChoice.VANILLA }
      this.infDurabilitySetting = this.add(EnumSetting<InfDurabilityChoice>("InfDurability", ElytraFlyModule.InfDurabilityChoice.NONE, elytraFlyInfDurabilityPredicate))
      this.modeHandlers = EnumSelectionMap<ElytraFlyModeHandler>(this.modeSetting)
      this.fireworkUseTimer = ElapsedTimer()
      this.takeoffTimer = ElapsedTimer()
      this.durabilitySwapTimer = ElapsedTimer()
      this.enableTimer = ElapsedTimer()
      this.elytraSearchTimer = ElapsedTimer()
      this.speedSetting.setConfigName("LavaSpeed")
      this.packetVerticalSetting.setConfigName("VerticalPacket")
      this.strictSpeedSetting.setConfigName("StrictSpeed")
      this.pitchSetting.setConfigName("BouncePitch")
      this.minBoostSetting.setBoundaryLabel("GrimV3", SettingLabelCondition.MAX)
      this.limitSetting.setBoundaryLabel("Unlimited", SettingLabelCondition.MAX)
      this.packetSetting.setConfigName("BounceBoost")
      this.maxBoostSetting.setConfigName("BounceMaxBoost")
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.BOOST, BoostElytraFlyHandler(this))
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.CONTROL, ControlElytraFlyHandler(this))
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.PACKET, PacketElytraFlyHandler(this))
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.STRICT, StrictElytraFlyHandler(this))
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.BOUNCE, BounceElytraFlyHandler(this))
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.NEWGRIM, NewGrimElytraFlyHandler(this))
      this.modeHandlers.register(ElytraFlyModule.ModeChoice.VANILLA, VanillaElytraFlyHandler(this))
   }

   enum class InfDurabilityChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      PLAIN("Plain"),
      GRIM("Grim"),
      _2B2T("2b2t");

      val isDisabled: Boolean
         @JvmName("isDisabledProp") get() = isDisabled()

      fun isDisabled(): Boolean {
         return this == NONE
      }

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      CONTROL("Control"),
      BOOST("Boost"),
      PACKET("Packet"),
      STRICT("Strict"),
      BOUNCE("Bounce"),
      NEWGRIM("2b2t"),
      VANILLA("Vanilla");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class StopMotionChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      STATIC("Static"),
      MOTION("Jitter"),
      TIMER("Timer");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class TakeoffChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      PLAIN("Plain"),
      STRICT("Strict");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class VerticalChoice(private val displayName: String) : NamedEntry {
      NONE("None"),
      PLAIN("Plain"),
      MANUAL("Manual"),
      STRICT("Strict");

      override fun getName(): String {
         return this.displayName
      }
   }
}
