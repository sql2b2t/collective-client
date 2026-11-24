package me.collective.client.modules.movement


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.SoundEnginePlayEvent
import me.collective.client.managers.RotationManager
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.combat.AutoArmorModule
import me.collective.client.modules.combat.AutoMaceModule
import me.collective.client.modules.exploit.RocketExtenderModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.util.function.Predicate
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity.RemovalReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

class FireworksModule : Module("Fireworks", "Enhances the usage of firework rockets.", ModuleCategory.MOVEMENT, "rockets") {
   companion object {
      @JvmField
      val temporaryElytraSwapActive: AtomicBoolean = AtomicBoolean(
         false
      )

      private var autoArmorModule: AutoArmorModule? = null
      private var autoMaceModule: AutoMaceModule? = null
      private var rocketExtenderModule: RocketExtenderModule? = null
      private var elytraFlyModule: ElytraFlyModule? = null

      @JvmStatic
      fun getGrimBoost(yaw: Float, pitch: Float, previousYaw: Float, previousPitch: Float, movement: Vec3): Vec3 {
         val lookDirection: Vec3 = Vec3.directionFromRotation(pitch, yaw)
         val movementBounds: AABB = getBoostMovementBounds(yaw, pitch, previousYaw, previousPitch, movement)
         return BoundingBoxUtils.clampToBounds(lookDirection.scale(0.8), movementBounds)
      }

      @JvmStatic
      fun getBoostMovementBounds(yaw: Float, pitch: Float, previousYaw: Float, previousPitch: Float, movement: Vec3): AABB {
         val currentLook: Vec3 = Vec3.directionFromRotation(pitch, yaw)
         val previousLook: Vec3 = Vec3.directionFromRotation(previousPitch, previousYaw)

         val yawFraction: Float = ((yaw % 45.0f + 45.0f) % 45.0f) / 45.0f

         var predictionScale: Double = 1.54

         if (pitch < -35.0f) {
            predictionScale = 1.63
         } else if (pitch < -15.0f) {
            predictionScale = 1.62
         } else if (pitch < -10.0f) {
            predictionScale = 1.61
         }

         if (yawFraction.toDouble() > 0.1 && yawFraction.toDouble() < 0.9) {
            predictionScale -= 0.05
         }

         val minimumX: Double = Math.max(-predictionScale,
            (Math.min(-0.0, currentLook.x) + Math.min(-0.0, previousLook.x)) * predictionScale)
         val minimumY: Double = Math.max(-predictionScale,
            (Math.min(-0.0, currentLook.y) + Math.min(-0.0, previousLook.y)) * predictionScale)
         val minimumZ: Double = Math.max(-predictionScale,
            (Math.min(-0.0, currentLook.z) + Math.min(-0.0, previousLook.z)) * predictionScale)
         val maximumX: Double = Math.min(predictionScale,
            (Math.max(0.0, currentLook.x) + Math.max(0.0, previousLook.x)) * predictionScale)
         val maximumY: Double = Math.min(predictionScale,
            (Math.max(0.0, currentLook.y) + Math.max(0.0, previousLook.y)) * predictionScale)
         val maximumZ: Double = Math.min(predictionScale,
            (Math.max(0.0, currentLook.z) + Math.max(0.0, previousLook.z)) * predictionScale)
         val bounds = AABB(
            Math.min(0.0, minimumX - movement.x),
            Math.min(0.0, minimumY - movement.y),
            Math.min(0.0, minimumZ - movement.z),
            Math.max(0.0, maximumX - movement.x),
            Math.max(0.0, maximumY - movement.y),
            Math.max(0.0, maximumZ - movement.z)
         )
         return bounds.contract(0.0, 0.0231, 0.0)
      }

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         autoArmorModule = modules.getModule(
            AutoArmorModule::class.java
         ) as AutoArmorModule
         autoMaceModule = modules.getModule(AutoMaceModule::class.java) as AutoMaceModule
         rocketExtenderModule = modules.getModule(
            RocketExtenderModule::class.java
         ) as RocketExtenderModule
         elytraFlyModule = modules.getModule(
            ElytraFlyModule::class.java
         ) as ElytraFlyModule
      }
   }

   @JvmField
   val antiWasteSetting: Setting<Boolean>
   @JvmField
   val softLagSetting: Setting<Boolean>
   @JvmField
   val fakeRocketSetting: Setting<Boolean>
   @JvmField
   val boostSetting: Setting<Boolean>
   @JvmField
   val withElytraFlySetting: Setting<Boolean>
   @JvmField
   val grimSetting: Setting<Boolean>
   @JvmField
   val directionalSetting: Setting<Boolean>
   @JvmField
   val boostFactorSetting: Setting<Float>
   @JvmField
   val autoLaunchSetting: Setting<Boolean>
   private val onlyElytraFlySetting: Setting<Boolean>
   private val onlyFirstSetting: Setting<Boolean>
   private val awaitSetting: Setting<Boolean>
   private val delaySetting: Setting<Float>
   @JvmField
   val fastLaunchSetting: Setting<Boolean>
   val modeSetting: Setting<ModeChoice>
   val swapSetting: Setting<SwapChoice>
   private val ticksSetting: Setting<Int>
   private val swapBackSetting: Setting<Boolean>
   private val autoPhaseSetting: Setting<Boolean>
   @JvmField
   val rocketUseTimer: ElapsedTimer
   @JvmField
   val fakeRocketTimer: ElapsedTimer
   private var rocketLaunchedDuringFlight: Boolean = false
   private var fastLaunchTicksRemaining: Int = 0
   private var swapBackPending: Boolean = false
   private var fastLaunchWasActive: Boolean = false
   @JvmField
   var resetRocketAge: Boolean = false
   private var ceilingFlightTicks: Int = 0
   private var swappedElytraSlot: Int = 0
   private var fakeRocketLifetimeTicks: Int = 0
   private var fakeRocketEntityId: Int = 0

   override fun onToggle() {
      this.removeFakeRocket()
      ClientContext.tickRateController.releaseMultiplier(this)
   }

   fun canFastLaunch(requireAirborne: Boolean): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      return !PlayerStateUtils.isWearingElytra(player) && (!requireAirborne || !player.onGround()) &&
         this.isToggled() && this.fastLaunchSetting.value &&
         InventoryUtils.findMenuSlot(Items.ELYTRA) != -1
   }

   fun getAdditionalBoost(fromRocketEntity: Boolean): Float {
      if (!this.isToggled() || !this.boostSetting.value) {
         return 0.0f
      }
      if (!fromRocketEntity && !this.withElytraFlySetting.value) {
         return 0.0f
      }
      return if (this.isRocketBoostActive()) this.boostFactorSetting.value else 0.0f
   }

   fun isFastLaunchActive(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (player.onGround()) {
         return false
      }
      val mode: FireworksModule.ModeChoice = this.modeSetting.value
      return when (mode) {
         ModeChoice.MANUAL -> this.fastLaunchTicksRemaining > 0
         ModeChoice.TOGGLE -> this.fastLaunchSetting.value
      }
   }

   fun isGrimBoostEnabled(): Boolean {
      return this.isToggled() && this.boostSetting.value && this.grimSetting.value
   }

   @EventHandler(priority = 200)
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      var fakeRocketLifetimeMillis: Long = this.fakeRocketLifetimeTicks.toLong() * 50L
      if (rocketExtenderModule!!.isToggled()) {
         fakeRocketLifetimeMillis = (rocketExtenderModule!!.getTimeoutSeconds() * 1000.0f).toLong()
      }
      if (this.fakeRocketTimer.hasElapsed(fakeRocketLifetimeMillis)) {
         this.removeFakeRocket()
      }
      if (PlayerStateUtils.isFallFlying() && this.softLagSetting.value &&
         ClientContext.serverStateManager.hasElapsedSinceSetback(400L)) {
         ClientContext.tickRateController.requestMultiplier(this, 0.8f)
      } else {
         ClientContext.tickRateController.releaseMultiplier(this)
      }
      if (this.swapBackPending) {
         if (!InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3)) {
            val swapMode: FireworksModule.SwapChoice = this.swapSetting.value
            if (!swapMode.isSilent() && this.swappedElytraSlot != -1) {
               (this.swapSetting.value).swapElytra(this.swappedElytraSlot, 6)
               autoArmorModule!!.skipNextArmorUpdate()
               this.swappedElytraSlot = -1
            }
            this.swapBackPending = false
         }
         if (!PlayerStateUtils.isWearingElytra(ClientAccess.collectiveMinecraft.player!!) &&
         (this.swapSetting.value).isSilent()) {
            ClientAccess.collectiveMinecraft.player!!.stopFallFlying()
            this.swapBackPending = false
         }
      }
      if (this.fastLaunchSetting.value && this.modeSetting.value == ModeChoice.TOGGLE &&
         ClientAccess.collectiveMinecraft.player!!.onGround() && this.fastLaunchWasActive) {
         ClientContext.inputOverrides.requestJump(true)
      }
      if (this.isFastLaunchActive() && this.fastLaunchSetting.value) {
         autoArmorModule!!.skipNextArmorUpdate()
         this.equipElytraAndStartFlight()
         if (this.modeSetting.value == ModeChoice.MANUAL) {
            this.fastLaunchTicksRemaining--
         }
         this.fastLaunchWasActive = true
      } else if (this.fastLaunchWasActive) {
         if (this.swapBackSetting.value) {
            autoArmorModule!!.skipNextArmorUpdate()
            this.swapBackPending = true
         }
         this.fastLaunchWasActive = false
         if (this.modeSetting.value == ModeChoice.TOGGLE && this.fastLaunchSetting.value &&
         ClientAccess.collectiveMinecraft.player!!.onGround()) {
            autoArmorModule!!.skipNextArmorUpdate()
            this.swapBackPending = false
         }
      }
      if (this.fastLaunchSetting.value && this.autoPhaseSetting.value) {
         val top: Double = ClientAccess.collectiveMinecraft.player!!.boundingBox.maxY
         if (Math.floor(top) == Math.floor(top + 0.01) && !ClientAccess.collectiveMinecraft.player!!.onGround()) {
            this.ceilingFlightTicks++
         } else {
            this.ceilingFlightTicks = 0
         }
         this.tryAutoPhase()
         if (!ClientAccess.collectiveMinecraft.player!!.isFallFlying && EntityUtils.hasFirework(ClientAccess.collectiveMinecraft.player!!) &&
         ClientAccess.collectiveMinecraft.player!!.getXRot() < 0.0f &&
         KeyUtils.isPhysicallyDown(ClientAccess.collectiveMinecraft.options.keyJump)) {
            PacketSender.sendStartFallFlying()
            ClientAccess.collectiveMinecraft.player!!.startFallFlying()
         }
      }
      val toggleLaunchActive: Boolean = this.modeSetting.value == ModeChoice.TOGGLE && this.isFastLaunchActive()
      var fallFlyingForLaunch: Boolean = PlayerStateUtils.isFallFlying()
      val elytraFlySnapshot = elytraFlyModule
      if (elytraFlySnapshot!!.isToggled() && elytraFlySnapshot.isDurabilityBypassActive() &&
         elytraFlySnapshot.modeSetting.value == ElytraFlyModule.ModeChoice.NEWGRIM) {
         fallFlyingForLaunch = true
      }
      if (!fallFlyingForLaunch || ClientAccess.collectiveMinecraft.player!!.onGround()) {
         this.rocketUseTimer.clear()
      }
      if (!this.autoLaunchSetting.value) {
         return
      }
      if (!elytraFlySnapshot.isToggled() && this.onlyElytraFlySetting.value && !toggleLaunchActive) {
         return
      }
      if (elytraFlySnapshot.isToggled() && elytraFlySnapshot.modeSetting.value == ElytraFlyModule.ModeChoice.BOUNCE) {
         return
      }
      val delaySeconds: Double = this.delaySetting.value.toDouble()
      var awaitRocket = !this.rocketUseTimer.hasElapsed(delaySeconds, TimeUnit.SECONDS) ||
         rocketExtenderModule!!.isToggled() && rocketExtenderModule!!.trackedRocketId != -1 && rocketExtenderModule!!.rangeAnchor != null
      if (this.isRocketBoostActive()) {
         awaitRocket = true
      }
      if (awaitRocket) {
         return
      }
      if (!PlayerStateUtils.isFallFlying()) {
         this.rocketLaunchedDuringFlight = false
      }
      if (this.rocketLaunchedDuringFlight && this.onlyFirstSetting.value) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.onGround()) {
         return
      }
      if (elytraFlySnapshot.isToggled() && elytraFlySnapshot.modeSetting.value == ElytraFlyModule.ModeChoice.NEWGRIM &&
         !(elytraFlySnapshot.infDurabilitySetting.value).isDisabled() &&
         !RotationManager.is2b2tMode()) {
         this.temporarilyEquipElytra()
      }
      this.tryUseRocket()
   }

   @EventHandler
   private fun onSoundEnginePlayEvent(event: SoundEnginePlayEvent) {
      if (this.fastLaunchTicksRemaining <= 0 || event.soundInstance == null) {
         return
      }
      val identifier: Identifier? = event.soundInstance.getIdentifier()
      if (identifier != null && identifier.toString().contains("item.armor.equip")) {
         event.cancel()
      }
   }

   @EventHandler(priority = -100)
   private fun onLocalPlayerMoveEvent(event: LocalPlayerMoveEvent) {
      var yaw: Float = ClientAccess.collectiveMinecraft.player!!.getYRot()
      var pitch: Float = ClientAccess.collectiveMinecraft.player!!.getXRot()
      val rotationManager = ClientContext.rotationManager

      val request = rotationManager.getRequest()
      if (request != null) {
         yaw = request.angles[0]
         pitch = request.angles[1]
      }

      if (!autoMaceModule!!.shouldSuppressGrimBoost() && PlayerStateUtils.isFallFlying() && this.boostSetting.value &&
         this.grimSetting.value) {
         if (EntityUtils.hasFirework(ClientAccess.collectiveMinecraft.player!!)) {
            val packetRotations: FloatArray = ClientContext.playerStateTracker.getPacketRotations()
            val previousYaw: Float = packetRotations[0]
            val previousPitch: Float = packetRotations[1]
            val movement: Vec3 = event.originalVelocity
            val boostMovement: Vec3 = getGrimBoost(yaw, pitch, previousYaw, previousPitch, movement)
            val currentMovement: Vec3 = event.originalVelocity
            val boostedMovement: Vec3 = currentMovement.add(boostMovement)
            event.setVelocity(boostedMovement)
         }
      }

      if (PlayerStateUtils.isFallFlying() && this.awaitSetting.value &&
         this.autoLaunchSetting.value) {

         if (!this.isRocketBoostActive()) {
            val pausedMovement: Vec3 = Vec3(0.0, 0.0, 0.0)
            event.setVelocity(pausedMovement)
         }

      }
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemEvent(event: MultiPlayerGameModeUseItemEvent) {
      if (!ClientAccess.collectiveMinecraft.player!!.isHolding(Items.FIREWORK_ROCKET)) {
         return
      }
      val fakeRocketEnabled: Boolean = this.fakeRocketSetting.value
      if (fakeRocketEnabled && PlayerStateUtils.isFallFlying()) {

         this.removeFakeRocket()

         this.fakeRocketEntityId = -84751
         val playerForRocketStack: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val hand: InteractionHand = event.hand
         val rocketStack: ItemStack = playerForRocketStack.getItemInHand(hand)
         val fakeRocket = FireworkRocketEntity(
            ClientAccess.collectiveMinecraft.level!!,
            rocketStack,
            ClientAccess.collectiveMinecraft.player!!
         )
         val entityId = this.fakeRocketEntityId
         fakeRocket.setId(entityId)
         var flightDuration = 1
         val fireworksType: DataComponentType<Fireworks> = DataComponents.FIREWORKS
         val fireworksComponent: Fireworks? = rocketStack.get(fireworksType)
         if (fireworksComponent != null) {
            flightDuration += fireworksComponent.flightDuration()
         }

         var lifetimeTicks = 10 * flightDuration
         lifetimeTicks += ThreadLocalRandom.current().nextInt(6)
         this.fakeRocketLifetimeTicks = lifetimeTicks + ThreadLocalRandom.current().nextInt(7)
         ClientAccess.collectiveMinecraft.level!!.addEntity(fakeRocket)
         this.fakeRocketTimer.reset()
         event.cancel()
      }

      val fastLaunchEnabled: Boolean = this.fastLaunchSetting.value
      if (fastLaunchEnabled && PlayerStateUtils.isLocalWearingElytra() &&
         !PlayerStateUtils.isFallFlying()) {
         if (!ClientAccess.collectiveMinecraft.player!!.onGround()) {

            PacketSender.sendStartFallFlying()
            ClientAccess.collectiveMinecraft.player!!.startFallFlying()
         }
      }

      if (this.canFastLaunch(true)) {

         this.equipElytraAndStartFlight()
         val launchTicks: Int = this.ticksSetting.value
         this.fastLaunchTicksRemaining = launchTicks
         this.resetRocketAge = true
      } else if (this.fastLaunchTicksRemaining > 0) {
         val refreshedLaunchTicks: Int = this.ticksSetting.value
         this.fastLaunchTicksRemaining = refreshedLaunchTicks
      }
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
      if (this.antiWasteSetting.value &&
         ClientAccess.collectiveMinecraft.player!!.getItemInHand(event.hand).`is`(Items.FIREWORK_ROCKET)) {
         ClientAccess.collectiveMinecraft.gameMode!!.useItem(ClientAccess.collectiveMinecraft.player!!, event.hand)
         ClientAccess.collectiveMinecraft.player!!.swing(event.hand)
         event.cancel()
      }
   }

   private fun removeFakeRocket() {
      if (this.fakeRocketEntityId != -1) {
         val level: ClientLevel? = ClientAccess.collectiveMinecraft.level
         if (level != null) {
            level.removeEntity(this.fakeRocketEntityId, RemovalReason.KILLED)
         }
         this.fakeRocketEntityId = -1
      }
   }

   private fun equipElytraAndStartFlight() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (player.onGround()) {
         return
      }
      val swapMode: FireworksModule.SwapChoice = this.swapSetting.value
      if (!swapMode.isSilent()) {
         if (PlayerStateUtils.isLocalWearingElytra()) {
            if (!PlayerStateUtils.isFallFlying()) {
               PacketSender.sendStartFallFlying()
               player.startFallFlying()
            }
            return
         }
         val slot = InventoryUtils.findMenuSlot(Items.ELYTRA)
         if (slot != -1 && !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 4)) {
            autoArmorModule!!.skipNextArmorUpdate()
            swapMode.swapElytra(slot, 6)
            if (!PlayerStateUtils.isFallFlying()) {
               PacketSender.sendStartFallFlying()
               player.startFallFlying()
            }
            this.swappedElytraSlot = slot
         }
         return
      }
      var slot = InventoryUtils.findHotbarSlot(Items.ELYTRA)
      val inventorySwapRequired = slot == -1
      if (inventorySwapRequired) {
         slot = InventoryUtils.findMenuSlot(Items.ELYTRA)
      }
      if (slot == -1) {
         return
      }
      val originalMenuSlot = slot
      if (inventorySwapRequired) {
         slot = 0
         InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP)
      }
      InventoryUtils.clickSlot(6, slot, ClickType.SWAP)
      try {
         PacketSender.sendStartFallFlying()
         player.startFallFlying()
      } finally {
         InventoryUtils.clickSlot(6, slot, ClickType.SWAP)
         if (inventorySwapRequired) {
            InventoryUtils.clickSlot(originalMenuSlot, 0, ClickType.SWAP)
         }
      }
   }

   private fun tryAutoPhase() {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (!PlayerStateUtils.isFallFlying() || !EntityUtils.hasFirework(player)) {
         return
      }
      val raycastResult = RaycastUtils.raycastWithEntityTrace(player)
      if (raycastResult is EntityHitResult &&
         raycastResult.entity.type == EntityType.END_CRYSTAL) {
         return
      }
      val selectedSlot = InventoryUtils.getSelectedSlot()
      val pearlSlot = InventoryUtils.findSwapSlot(Items.ENDER_PEARL, true)
      val lookDirection: Vec3 = Vec3.directionFromRotation(player.getXRot(), player.getYRot())
      val projectedBounds: AABB = player.getBoundingBox().move(lookDirection.scale(0.1))
      val blockedAhead: Boolean = !ClientAccess.collectiveMinecraft.level!!.noBlockCollision(player, projectedBounds) &&
         this.ceilingFlightTicks >= 2
      val hitResult = ClientAccess.collectiveMinecraft.hitResult
      val pearlTarget: Boolean = !player.getCooldowns().isOnCooldown(Items.ENDER_PEARL.getDefaultInstance()) &&
         hitResult is BlockHitResult &&
         WorldUtils.hasCollision(hitResult.blockPos)
      if (this.autoPhaseSetting.value && pearlSlot.isFound() && blockedAhead && pearlTarget) {
         InventoryUtils.swapTo(pearlSlot)
         try {
            ClientAccess.collectiveMinecraft.gameMode!!.useItem(player, pearlSlot.hand)
            PacketSender.sendSwing(pearlSlot.hand)
         } finally {
            InventoryUtils.swapBack(selectedSlot, pearlSlot)
         }
      }
   }

   fun tryUseRocket() {
      if (!this.autoLaunchSetting.value ||
         !PlayerStateUtils.isFallFlying() && !temporaryElytraSwapActive.get() ||
         ClientAccess.collectiveMinecraft.player!!.isUsingItem()) {
         return
      }
      val selectedSlot = ClientAccess.collectiveMinecraft.player!!.getInventory().getSelectedSlot()
      val rocketSlot = InventoryUtils.findSwapSlot(Items.FIREWORK_ROCKET, true)
      if (!rocketSlot.isFound()) {
         return
      }
      InventoryUtils.swapTo(rocketSlot)
      try {
         ClientAccess.collectiveMinecraft.gameMode!!.useItem(ClientAccess.collectiveMinecraft.player!!, rocketSlot.hand)
         ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT)
      } finally {
         InventoryUtils.swapBack(selectedSlot, rocketSlot)
      }
      rocketExtenderModule!!.launchPosition = ClientAccess.collectiveMinecraft.player!!.position()
      this.rocketUseTimer.reset()
      this.rocketLaunchedDuringFlight = true
   }

   val isRocketBoostActive: Boolean @JvmName("isRocketBoostActiveProp") get() = isRocketBoostActive()
   fun isRocketBoostActive(): Boolean {
      return !this.rocketUseTimer.hasElapsed(500L) || EntityUtils.hasFirework(ClientAccess.collectiveMinecraft.player!!)
   }

   private fun temporarilyEquipElytra() {
      val elytraSlot = InventoryUtils.findHotbarSlot(Items.ELYTRA)
      if (elytraSlot == -1 || !temporaryElytraSwapActive.compareAndSet(false, true)) {
         return
      }
      InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP)
      PacketSender.sendStartFallFlying()
      ClientAccess.collectiveMinecraft.player!!.startFallFlying()
      ClientContext.taskScheduler.scheduleRotationTask(Runnable {
         try {
            InventoryUtils.clickSlot(6, elytraSlot, ClickType.SWAP)
         } finally {
            temporaryElytraSwapActive.set(false)
         }
      }, 2)
   }

   init {
      this.antiWasteSetting = this.add(BooleanSetting("AntiWaste", true))
      this.softLagSetting = this.add(BooleanSetting("SoftLag", false))
      this.fakeRocketSetting = this.add(BooleanSetting("FakeRocket", false))
      this.boostSetting = this.add(BooleanSetting("Boost", false).markVisibilityDependency())
      this.withElytraFlySetting = this.add(BooleanSetting("WithElytraFly", true).dependsOn(this.boostSetting))
      this.grimSetting = this.add(BooleanSetting("Grim", false).dependsOn(this.boostSetting))
      val directionalVisibility = Predicate<Any> { ignored -> !(this.grimSetting.value) }
      this.directionalSetting = this.add(BooleanSetting("Directional", false, directionalVisibility).dependsOn(this.boostSetting))
      val boostFactorVisibility = Predicate<Any> { ignored -> !(this.grimSetting.value) }
      this.boostFactorSetting = this.add(NumberSetting<Float>("BoostFactor", 0.0f, 0.0f, 3.0f, boostFactorVisibility).dependsOn(this.boostSetting))
      this.autoLaunchSetting = this.add(BooleanSetting("AutoLaunch", false).markVisibilityDependency())
      this.onlyElytraFlySetting = this.add(BooleanSetting("OnlyElytraFly", true).dependsOn(this.autoLaunchSetting))
      this.onlyFirstSetting = this.add(BooleanSetting("OnlyFirst", false).dependsOn(this.autoLaunchSetting))
      this.awaitSetting = this.add(BooleanSetting("Await", false).dependsOn(this.autoLaunchSetting))
      val delaySettingValue = NumberSetting<Float>("Delay", 0.0f, 0.0f, 20.0f)
      delaySettingValue.withUnit("s")
      this.delaySetting = this.add(delaySettingValue.dependsOn(this.autoLaunchSetting))
      this.fastLaunchSetting = this.add(BooleanSetting("FastLaunch", false).markVisibilityDependency())
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.MANUAL).dependsOn(this.fastLaunchSetting))
      this.swapSetting = this.add(EnumSetting<SwapChoice>("Swap", FireworksModule.SwapChoice.NORMAL).dependsOn(this.fastLaunchSetting))
      val ticksVisibility = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.MANUAL }
      this.ticksSetting = this.add(NumberSetting<Int>("Ticks", 10, 3, 40, ticksVisibility).dependsOn(this.fastLaunchSetting))
      val swapBackVisibility = Predicate<Any> { ignored -> this.swapSetting.value != SwapChoice.SILENT }
      this.swapBackSetting = this.add(BooleanSetting("SwapBack", true, swapBackVisibility).dependsOn(this.fastLaunchSetting))
      this.autoPhaseSetting = this.add(BooleanSetting("AutoPhase", false).dependsOn(this.fastLaunchSetting))
      this.rocketUseTimer = ElapsedTimer()
      this.fakeRocketTimer = ElapsedTimer()
      this.fakeRocketLifetimeTicks = 0
      this.fakeRocketEntityId = -1
      this.delaySetting.setBoundaryLabel("Auto", SettingLabelCondition.MIN)
      this.fastLaunchSetting.setChangeCallback(Runnable {
         if (!this.isWorldUnavailable() && this.fastLaunchSetting.value &&
         this.modeSetting.value == FireworksModule.ModeChoice.TOGGLE) {
            this.rocketUseTimer.clear()
            ClientContext.inputOverrides.requestJump(true)
         }
      })
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      MANUAL("Manual"),
      TOGGLE("Toggle");

      override fun getName(): String {
         return this.displayName
      }
   }

   enum class SwapChoice(private val displayName: String) : NamedEntry {
      NORMAL("Normal"),
      ALT("Alt"),
      SILENT("Silent");

      override fun getName(): String {
         return this.displayName
      }

      fun isSilent(): Boolean {
         return this == SILENT
      }

      fun swapElytra(elytraSlot: Int, chestMenuSlot: Int) {
         if (this == NORMAL) {
            InventoryUtils.swapSlotsWithPickup(elytraSlot, chestMenuSlot)
         } else if (this == ALT) {
            InventoryUtils.swapMenuSlots(elytraSlot, chestMenuSlot)
         }
      }
   }
}
