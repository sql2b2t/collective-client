package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientLevelAddEntityEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.inventory.TradeOfferSnapshot
import me.collective.client.managers.RotationManager
import me.collective.client.utils.network.InteractPacketUtils
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.managers.TradeRegistry
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import me.collective.mixin.MerchantScreenAccessor
import me.collective.mixin.ServerboundInteractPacketAccessor
import java.util.function.Predicate
import java.util.HashMap
import java.util.Optional
import net.minecraft.ChatFormatting
import net.minecraft.client.Camera
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.NonNullList
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.AbstractVillager
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.ItemCost
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

class AutoTradeModule : Module(
   "AutoTrade",
   "Trades with villagers for you.\n" +
         String.format("%sUse ;trade command to access the trade gui", ChatFormatting.YELLOW),
   ModuleCategory.PLAYER
) {
   private val delaySetting: Setting<Int>
   private val frequencySetting: Setting<Int>
   private val closeSetting: Setting<Boolean>
   private val dropSetting: Setting<Boolean>
   private val autoOpenSetting: Setting<Boolean>
   internal val onSightSetting: Setting<Boolean>
   private val rangeSetting: Setting<Float>
   private val rotateSetting: Setting<Boolean>
   private val offerCache: VillagerOfferCache
   private val tradeDelayTimer: ElapsedTimer
   private val closeQuietTimer: ElapsedTimer
   private val openTimer: ElapsedTimer
   private val cacheRefreshTimer: ElapsedTimer
   private var tradedInMenu: Boolean = false
   private var resetOpenTimer: Boolean = false
   private var villagerId: Int = 0

   private fun findVillagerToOpen(): Entity? {
      val camera: Camera = ClientAccess.collectiveMinecraft.gameRenderer.getMainCamera()
      var selectedVillager: Entity? = null

      var smallestYawDifference: Float = 3.6e+02F
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      for (entity in level.entitiesForRendering()) {
         if (entity !is Villager) {
            continue
         }
         val villagerPosition: Vec3 = entity.position()
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val eyePosition: Vec3 = player.getEyePosition()
         val openRange: Double = (this.rangeSetting.value).toFloat().toDouble()
         if (!villagerPosition.closerThan(eyePosition, openRange)) {
            continue
         }
         val candidateId = entity.id
         if (!this.shouldOpenVillager(candidateId)) {
            continue
         }
         if (this.onSightSetting.value) {
            val hitResult = ClientAccess.collectiveMinecraft.hitResult
            if (hitResult is EntityHitResult &&
         hitResult.entity === entity) {
               return entity
            }
            continue
         }
         val villagerYaw: Float = RotationManager.getRotationsToEntity(entity)[0]
         val cameraYaw: Float = camera.yRot
         val yawDifference: Float = Mth.degreesDifferenceAbs(villagerYaw, cameraYaw)
         if (yawDifference < smallestYawDifference) {
            smallestYawDifference = yawDifference
            selectedVillager = entity
         }
      }

      return selectedVillager
   }

   private fun getCurrentVillager(): AbstractVillager? {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val currentVillagerId = this.villagerId
      return level.getEntity(currentVillagerId) as? AbstractVillager
   }

   private fun isTradeExperienceOrb(entity: Entity): Boolean {
      if (entity.type == EntityType.EXPERIENCE_ORB &&
         this.villagerId != -1) {
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val villager = level.getEntity(this.villagerId) ?: return false
         // Позиция шара всегда внутри его собственного bounding box — старая
         // проверка не фильтровала ничего. Проверяем попадание в габариты
         // ТОРГОВЦА: шар опыта от сделки появляется рядом с ним.
         val villagerBounds: AABB = villager.boundingBox.inflate(1.0)
         return villagerBounds.contains(entity.position())
      } else {
         return false
      }
   }

   @EventHandler
   private fun onSendPacket(
      event: PacketEvent.Send
   ) {
      val interactPacket = event.packet
      if (interactPacket is ServerboundInteractPacket) {
         if (InteractPacketUtils.getEntity(interactPacket) is Villager) {
            if (InteractPacketUtils.getAction(interactPacket) ==
         InteractPacketUtils.Action.INTERACT) {
               this.villagerId = (interactPacket as ServerboundInteractPacketAccessor)
                  .getEntityId()
            }
         }
      }
   }

   private fun tradeOnce(menu: MerchantMenu) {
      val villager = this.getCurrentVillager()
      if (villager == null) {
         return
      }
      val cacheReceiver = this.offerCache

      val queriedVillagerId = villager.id

      val cachedOffers = cacheReceiver.getOffers(queriedVillagerId)
      if (cachedOffers == null || cachedOffers.isEmpty()) {
         return
      }
      val offerIndices: HashMap<TradeItemKey, Int> = HashMap()
      var offerIndex: Int = 0

      val menuOffers: MerchantOffers = menu.getOffers()

      for (menuOffer in menuOffers) {
         val offerKey = TradeItemKey.fromOffer(menuOffer)
         offerIndices.put(offerKey, offerIndex)
         offerIndex++
      }

      for (cachedOffer in cachedOffers) {
         if (cachedOffer === TradeOfferSnapshot.OUT_OF_STOCK) {
            continue
         }
         val trades: TradeRegistry = ClientContext.tradeRegistry
         if (!trades.matches(cachedOffer)) {
            continue
         }
         if (!this.canAffordOffer(cachedOffer)) {
            continue
         }
         val selectedIndex = offerIndices.get(
            TradeItemKey.fromCachedOffer(cachedOffer)
         ) ?: continue

         this.selectTrade(selectedIndex)

         val outputStack: ItemStack = menu.getSlot(2).getItem()

         val expectedResult: ItemStack? = cachedOffer.result()

         if (expectedResult != null && ItemStack.isSameItem(outputStack, expectedResult)) {
            val containerId = menu.containerId
            this.collectTradeResult(containerId)
         }

         this.tradedInMenu = true

         this.tradeDelayTimer.reset()
         break
      }
      offerIndices.clear()
   }

   @EventHandler
   private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
      val containerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      if (!(containerMenu is MerchantMenu)) {
         this.tradedInMenu = false
         return
      }
      val menu: MerchantMenu = containerMenu
      var timer = this.cacheRefreshTimer

      if (timer.hasElapsedAndReset(500L) &&
         ClientAccess.collectiveMinecraft.player!!.containerMenu is MerchantMenu &&
         this.villagerId != -1) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val currentVillagerId = this.villagerId
         val refreshMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu as MerchantMenu
         if (level.getEntity(currentVillagerId) is AbstractVillager) {
            val villager = level.getEntity(currentVillagerId) as AbstractVillager
            val cache = this.offerCache
            val refreshedOffers: MerchantOffers = refreshMenu.getOffers()
            cache.updateOffers(villager, refreshedOffers)
         }
      }

      timer = this.tradeDelayTimer
      val requiredDelayMillis: Long = (this.delaySetting.value).toLong()
      if (!timer.hasElapsed(requiredDelayMillis)) {
         return
      }
      this.resetOpenTimer = true
      var tradeAttempt: Int = 0
      while (true) {
         val frequency: Int = this.frequencySetting.value
         if (tradeAttempt >= frequency) {
            break
         }

         this.tradeOnce(menu)
         tradeAttempt++
      }

      val closeDelay: Int
      if (this.tradedInMenu) {
         closeDelay = 150
      } else {
         closeDelay = 500
      }

      val closeDelayMillis: Long = closeDelay.toLong()
      if (this.closeQuietTimer.hasElapsed(closeDelayMillis)) {
         if (this.closeSetting.value) {
            val screen: Screen? = ClientAccess.collectiveMinecraft.screen
            if (screen != null) {
               screen.onClose()
            } else {
               // Меню торговли открыто без экрана: закрываем явно,
               // иначе ServerboundContainerClosePacket не уйдёт.
               ClientAccess.collectiveMinecraft.player!!.closeContainer()
            }
         }
      }
   }

   @EventHandler
   private fun onEntityAdded(event: ClientLevelAddEntityEvent) {
      val addedEntity: Entity = event.entity

      if (this.isTradeExperienceOrb(addedEntity)) {
         this.closeQuietTimer.reset()
      }
   }

   private fun shouldOpenVillager(entityId: Int): Boolean {
      var cacheAgeMillis: Long = System.currentTimeMillis()
      val villagerCache = this.offerCache

      cacheAgeMillis -= villagerCache.getLastUpdatedMillis(entityId)

      if (cacheAgeMillis > 30000L) {
         return true
      }

      val offers = this.offerCache.getOffers(entityId)
      if (offers == null || offers.isEmpty()) {
         return true
      }
      for (offer in offers) {
         if (offer !== TradeOfferSnapshot.OUT_OF_STOCK) {
            if (ClientContext.tradeRegistry.matches(offer) && this.canAffordOffer(offer)) {
               return true
            }
         }
      }

      return false
   }

   private fun collectTradeResult(containerId: Int) {
      if (this.dropSetting.value) {
         val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!

         val dropPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val dropClick: ClickType = ClickType.THROW
         val dropButton = 0

         gameMode.handleInventoryMouseClick(containerId, 2, dropButton, dropClick, dropPlayer)
      } else {
         val quickMoveGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!

         val movePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val moveClick: ClickType = ClickType.QUICK_MOVE
         val moveButton = 0

         quickMoveGameMode.handleInventoryMouseClick(containerId, 2, moveButton, moveClick, movePlayer)
      }
   }

   private fun selectTrade(offerIndex: Int) {
      val screen = ClientAccess.collectiveMinecraft.screen
      if (screen is MerchantScreen) {
         (screen as MerchantScreenAccessor).`collective$setSelected`(offerIndex)
         (screen as MerchantScreenAccessor).`collective$syncRecipe`()
      } else {
         val selectedTrade = ServerboundSelectTradePacket(offerIndex)
         PacketSender.send(selectedTrade)
      }
   }

   @EventHandler
   private fun onRotationRequest(event: RotationRequestEvent) {
      val autoOpen: Boolean = this.autoOpenSetting.value
      if (!autoOpen) {
         return
      }
      if (ClientAccess.collectiveMinecraft.screen != null) {
         return
      }
      if (this.resetOpenTimer) {
         val timer = this.openTimer
         timer.reset()
         this.resetOpenTimer = false
         return
      }
      val villager = this.findVillagerToOpen()
      if (villager == null) {
         return
      }
      val openDelayTimer = this.openTimer
      if (!openDelayTimer.hasElapsedAndReset(500L)) {
         return
      }
      val hand: InteractionHand = InteractionHand.MAIN_HAND
      BlockInteractionUtils.interactWithEntity(villager, hand)
      val rotate: Boolean = this.rotateSetting.value
      if (!rotate) {
         return
      }
      if (this.onSightSetting.value) {
         return
      }
      val rotations: FloatArray = RotationManager.getRotationsToEntity(villager)
      ClientContext.rotationManager.requestRotation(rotations, 100)
   }

   private fun canAffordOffer(offer: TradeOfferSnapshot): Boolean {
      var firstCostCount: Int = 0
      var secondCostCount: Int = 0
      val slots: NonNullList<Slot> = ClientAccess.collectiveMinecraft.player!!.containerMenu.slots
      for (slot in slots) {
         val stack: ItemStack = slot.getItem()

         val firstCostStack: ItemStack? = offer.firstCost()

         val inventoryItem: Item = stack.getItem()

         if (firstCostStack!!.`is`(inventoryItem)) {
            firstCostCount += stack.getCount()
         }

         val optionalSecondCost: Optional<*> = offer.secondCost()
         if (!optionalSecondCost.isEmpty()) {
            val secondCost = offer.secondCost().get() as ItemCost

            if (secondCost.test(stack)) {
               // Считаем ФАКТИЧЕСКОЕ количество в стаке, а не требуемую
               // стоимость за каждый подходящий стак (иначе «можно позволить»
               // при нехватке предметов).
               secondCostCount += stack.getCount()
            }
         }
      }

      val requiredFirstCost: ItemStack? = offer.firstCost()
      if (firstCostCount < requiredFirstCost!!.getCount()) {
         return false
      }

      val requiredSecondCost: Optional<*> = offer.secondCost()
      if (!requiredSecondCost.isEmpty()) {
         val requiredCostStack: ItemStack = (offer.secondCost().get() as ItemCost).itemStack()
         if (secondCostCount < requiredCostStack.getCount()) {
            return false
         }
      }

      return true
   }

   @EventHandler
   private fun onReceivePacket(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundMerchantOffersPacket) {
         val offersPacket = event.packet as ClientboundMerchantOffersPacket
         // Событие постится с netty-потока: getCurrentVillager() читает мир,
         // а таймеры пишутся из чужого потока — переносим на главный поток.
         ClientAccess.collectiveMinecraft.execute {
            val cache = this.offerCache

            val villager = this.getCurrentVillager()
            val packetOffers: MerchantOffers = offersPacket.getOffers()
            cache.updateOffers(villager, packetOffers)

            this.closeQuietTimer.reset()
         }
      }
   }

   init {
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 150, 25, 1500).withUnit("ms"))
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 6))
      val closeDefault: Boolean = false
      this.closeSetting = this.add(BooleanSetting("Close", closeDefault))
      val dropDefault: Boolean = false
      this.dropSetting = this.add(BooleanSetting("Drop", dropDefault))
      val autoOpenDefault: Boolean = false
      val autoOpenOption = BooleanSetting("AutoOpen", autoOpenDefault)
      autoOpenOption.markVisibilityDependency()
      this.autoOpenSetting = this.add(autoOpenOption)
      val sightDefault: Boolean = false
      val sightOption = BooleanSetting("OnSight", sightDefault)
      this.onSightSetting = this.add(sightOption.dependsOn(this.autoOpenSetting))
      val rangeVisibility: Predicate<Any> = Predicate<Any> { ignoredValue ->
         val onSight: Setting<Boolean> = this.onSightSetting
         !onSight.value
      }
      val rangeOption = NumberSetting<Float>("Range", 3.0F, 0.1F, 6.0F, rangeVisibility)
      rangeOption.withUnit("m")
      this.rangeSetting = this.add(rangeOption.dependsOn(this.autoOpenSetting))
      val rotateVisibility: Predicate<Any> = Predicate<Any> { ignoredValue ->
         val onSight: Setting<Boolean> = this.onSightSetting
         !onSight.value
      }
      val rotateDefault: Boolean = true
      val rotateOption = BooleanSetting("Rotate", rotateDefault, rotateVisibility)
      this.rotateSetting = this.add(rotateOption.dependsOn(this.autoOpenSetting))
      this.offerCache = VillagerOfferCache()
      this.tradeDelayTimer = ElapsedTimer()
      this.closeQuietTimer = ElapsedTimer()
      this.openTimer = ElapsedTimer()
      this.cacheRefreshTimer = ElapsedTimer()
      this.villagerId = -1
   }

}
