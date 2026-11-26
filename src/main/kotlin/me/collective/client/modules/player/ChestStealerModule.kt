package me.collective.client.modules.player

import java.util.Collections
import java.util.Locale
import java.util.function.Predicate

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.AbstractContainerMenuMoveItemStackToEvent
import me.collective.client.events.AbstractContainerScreenEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.gui.screens.ShulkerPreviewScreen
import me.collective.client.managers.KitRegistry
import me.collective.client.utils.collections.IntPair
import me.collective.client.managers.TaskScheduler
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.ElapsedTimer
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.inventory.Kit
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.misc.ChestSearchBarModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetSetting
import me.collective.client.settings.options.ChestStealerLookForChoice
import me.collective.client.settings.options.ChestStealerModeChoice
import me.collective.client.settings.options.ListFilterMode

import me.collective.mixin.AbstractContainerScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

class ChestStealerModule : Module("ChestStealer", "Steals items from the storages you open.", ModuleCategory.PLAYER, *createAliases()) {
   private val itemsSetting: Setting<Set<Item>>
   private val namesSetting: Setting<Set<String>>
   private val modeSetting: Setting<ChestStealerModeChoice>
   val selectSetting: Setting<ListFilterMode>
   private val lookForSetting: Setting<ChestStealerLookForChoice>
   @JvmField
   val onlyButtonsSetting: Setting<Boolean>
   private val animateSetting: Setting<Boolean>
   private val delaySetting: Setting<Int>
   private val timeoutSetting: Setting<Int>
   private val frequencySetting: Setting<Int>
   @JvmField
   val fullSetting: Setting<Boolean>
   @JvmField
   val closeSetting: Setting<Boolean>
   @JvmField
   val returnSetting: Setting<Boolean>
   private val placementController: ShulkerPlacementController
   private val cleanupController: ShulkerCleanupController
   private val returnController: ShulkerReturnController
   private val refillController: ShulkerRefillController
   @JvmField
   val openController: ShulkerOpenController
   private val transferAnimations: MutableList<InventoryTransferAnimation>
   private val slotCooldowns: MutableMap<Int, Long>
   private val transferDelayTimer: ElapsedTimer
   private val screenOpenTimer: ElapsedTimer
   private val idleCloseTimer: ElapsedTimer
   private var lastInteractedBlock: BlockPos? = null
   private var requestedMode: ChestStealerModeChoice? = null
   private var trackingQuickMove: Boolean = false
   private var hasTransferred: Boolean = false
   private var transferredThisTick: Boolean = false
   private var quickMoveDestination: Slot? = null

   init {
      val itemRegistry: DefaultedRegistry<Item> = BuiltInRegistries.ITEM
      val itemsRegistration = RegistrySetSetting<Item>("Items", itemRegistry)
      this.itemsSetting = this.add(itemsRegistration)
      val namesRegistration = StringSetSetting("Names")
      this.namesSetting = this.add(namesRegistration)
      val modeRegistration = EnumSetting<ChestStealerModeChoice>("Mode", ChestStealerModeChoice.STEAL)
      this.modeSetting = this.add(modeRegistration)
      val selectRegistration = EnumSetting<ListFilterMode>("Select", ListFilterMode.ANY)
      this.selectSetting = this.add(selectRegistration)
      val lookForPredicate = Predicate<ChestStealerLookForChoice> { _ -> this.selectSetting.value != ListFilterMode.ANY }
      val lookForRegistration = EnumSetting<ChestStealerLookForChoice>("LookFor", ChestStealerLookForChoice.ITEM, lookForPredicate)
      this.lookForSetting = this.add(lookForRegistration)
      val onlyButtonsRegistration = BooleanSetting("OnlyButtons", false)
      this.onlyButtonsSetting = this.add(onlyButtonsRegistration)
      val animateRegistration = BooleanSetting("Animate", true)
      this.animateSetting = this.add(animateRegistration)
      val delayRegistration = NumberSetting<Int>("Delay", 50, 0, 1000)
      delayRegistration.withUnit("ms")
      this.delaySetting = this.add(delayRegistration)
      val timeoutRegistration = NumberSetting<Int>("Timeout", 200, 0, 750)
      timeoutRegistration.withUnit("ms")
      this.timeoutSetting = this.add(timeoutRegistration)
      val frequencyRegistration = NumberSetting<Int>("Frequency", 1, 1, 6)
      this.frequencySetting = this.add(frequencyRegistration)
      val autoRegistration = BooleanSetting("Auto", false)
      autoRegistration.markAsGroup()
      autoRegistration.markVisibilityDependency()
      val registeredAuto = this.add(autoRegistration)
      val fullWithDependency: Setting<Boolean> = BooleanSetting("Full", false).dependsOn(registeredAuto)
      this.fullSetting = this.add(fullWithDependency)
      val closePredicate = Predicate<Boolean> { _ -> !(this.fullSetting.value!!) }
      val closeWithDependency: Setting<Boolean> = BooleanSetting("Close", false, closePredicate).dependsOn(registeredAuto)
      this.closeSetting = this.add(closeWithDependency)
      val returnPredicate = Predicate<Boolean> { _ -> !(this.fullSetting.value!!) }
      val returnWithDependency: Setting<Boolean> = BooleanSetting("Return", false, returnPredicate).dependsOn(registeredAuto)
      this.returnSetting = this.add(returnWithDependency)
      this.placementController = ShulkerPlacementController(this)
      this.cleanupController = ShulkerCleanupController(this)
      this.returnController = ShulkerReturnController(this)
      this.refillController = ShulkerRefillController(this)
      this.openController = ShulkerOpenController(this)
      this.transferAnimations = Collections.synchronizedList(ArrayList<InventoryTransferAnimation>())
      this.slotCooldowns = HashMap()
      this.transferDelayTimer = ElapsedTimer()
      this.screenOpenTimer = ElapsedTimer()
      this.idleCloseTimer = ElapsedTimer()
      var itemsOption: Setting<*> = this.itemsSetting

      itemsOption.setConfigName("Whitelist")
      itemsOption = this.frequencySetting

      val maximumLabelCondition = SettingLabelCondition.MAX
      val maximumLabel = "Max"
      itemsOption.setBoundaryLabel(maximumLabel, maximumLabelCondition)
      itemsOption = this.animateSetting
      itemsOption.setChangeCallback { this.transferAnimations.clear() }
      itemsOption = this.modeSetting
      val modeVisibility = Predicate<Any> { _ ->
         !(this.onlyButtonsSetting.value!!)
      }

      itemsOption.addVisibilityPredicate(modeVisibility)
      val afterInitEvent = ScreenEvents.AFTER_INIT
      val screenInitializer = ChestStealerScreenInitializer(this)
      afterInitEvent.register(screenInitializer)
   }

   fun getRequestedMode(): ChestStealerModeChoice? {
      return this.requestedMode
   }

   fun shouldCloseAutomatically(): Boolean {
      return this.closeSetting.value!! || this.fullSetting.value!!
   }

   fun shouldReturnAutomatically(): Boolean {
      return this.returnSetting.value!! || this.fullSetting.value!!
   }

   fun hasScreenOpenTimedOut(): Boolean {
      val timer = this.screenOpenTimer
      val timeout = this.timeoutSetting.value
      return timer.hasElapsed(timeout!!.toLong())
   }

   private fun markSlotCoolingDown(slotIndex: Int) {
      val cooldowns = this.slotCooldowns
      val boxedSlotIndex = slotIndex

      val expiresAtMillis = System.currentTimeMillis() + 1000L
      cooldowns[boxedSlotIndex] = expiresAtMillis
   }

   private fun quickMoveAndRecordCounts(slotIndex: Int, capacities: List<SlotCapacity>, stackCount: Int) {
      val quickMoveType = ContainerInput.QUICK_MOVE
      val button = 0
      InventoryUtils.clickSlot(slotIndex, button, quickMoveType)
      // Quick-move тоже переносит стак: без флагов авто-закрытие контейнера
      // в режимах REFILL/REKIT не срабатывает вообще.
      this.transferredThisTick = true
      this.hasTransferred = true
      var remainingCount = stackCount
      for (capacityEntry in capacities) {
         if (remainingCount <= 0) {
            break
         }

         val freeSpace = capacityEntry.capacity - capacityEntry.count
         if (freeSpace > 0) {
            val movedCount = Math.min(freeSpace, remainingCount)

            val updatedCount = capacityEntry.count + movedCount

            capacityEntry.setCount(updatedCount)
            remainingCount -= movedCount
         }
      }
   }

   private fun moveRekitStack(stack: ItemStack, sourceSlotIndex: Int, containerSlotCount: Int): Boolean {
      val menu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      val inventoryCoordinator = ClientContext.inventoryStateManager

      if (InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, 3)) {
         return false
      }

      if (this.isSlotCoolingDown(sourceSlotIndex)) {
         return false
      }

      val kitRegistry = ClientContext.kitRegistry

      val profile = KitRegistry.getCurrentKit(kitRegistry)
      if (profile == null) {
         return false
      }

      val sourceSlot = menu.getSlot(sourceSlotIndex)
      val profileEntries = profile.itemsBySlot.entries.iterator()

      while (profileEntries.hasNext()) {
         val profileEntry = profileEntries.next()
         if (profileEntry.value.matches(stack)) {
            val hotbarSwap = profileEntry.key < 9
            val destinationOffset = if (hotbarSwap) {
               profileEntry.key + 27
            } else {
               profileEntry.key - 9
            }

            val destinationSlotIndex = containerSlotCount + destinationOffset

            if (!this.isSlotCoolingDown(destinationSlotIndex)) {
               val destinationStack = menu.getSlot(destinationSlotIndex).item
               val destinationDescriptor = profileEntry.value
               if (!destinationDescriptor.matches(destinationStack)) {
                  val destinationItem = destinationStack.item

                  if (!ItemUtils.isShulkerBox(destinationItem) ||
         menu !is ShulkerBoxMenu
                  ) {
                     val destinationSlot = menu.getSlot(destinationSlotIndex)
                     val transferredStack = sourceSlot.item
                     this.recordTransferAnimation(sourceSlot, destinationSlot, transferredStack)
                     if (hotbarSwap) {
                        val hotbarKey = profileEntry.key
                        val hotbarButton = hotbarKey
                        val swapType = ContainerInput.SWAP
                        InventoryUtils.clickSlot(sourceSlotIndex, hotbarButton, swapType)
                     } else {
                        InventoryUtils.swapSlotsWithPickup(sourceSlotIndex, destinationSlotIndex)
                     }

                     this.markSlotCoolingDown(sourceSlotIndex)

                     this.markSlotCoolingDown(destinationSlotIndex)
                     return true
                  }
               }
            }
         }
      }

      return false
   }

   private fun refillStack(sourceSlotIndex: Int, capacities: List<SlotCapacity>, allowPartial: Boolean) {
      val availableCapacities = capacities.filter { capacityEntry -> capacityEntry.count < capacityEntry.capacity }
      if (availableCapacities.isEmpty()) {
         return
      }
      val stack = ClientAccess.collectiveMinecraft.player!!.containerMenu.getSlot(sourceSlotIndex).item
      if (!stack.isStackable || stack.isEmpty) {
         return
      }
      val stackCount = stack.count

      val canQuickMove = InventoryUtils.isInventoryFull() ||
         allowPartial ||
         this.hasEnoughCapacity(availableCapacities, stackCount)
      val inventoryCoordinator = ClientContext.inventoryStateManager
      val clickCount = if (canQuickMove) {
         1
      } else {
         3
      }

      if (InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, clickCount)) {
         return
      }

      if (canQuickMove) {
         this.quickMoveAndRecordCounts(sourceSlotIndex, availableCapacities, stackCount)
      } else {
         this.distributeStackWithPickup(sourceSlotIndex, availableCapacities, stackCount)
      }
   }

   @EventHandler(priority = 200)
   private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
      if (this.fullSetting.value!!) {
         this.cleanupController.tick()
      }

      if (this.shouldReturnAutomatically()) {
         var returnHelper = this.returnController

         returnHelper.tick()
         returnHelper = this.returnController

         if (returnHelper.hasReturnedThisTick()) {
            return
         }
      }

      if (this.fullSetting.value!!) {
         this.refillController.tick()
      }
   }

   @EventHandler
   private fun onInventoryClick(
      event: MultiPlayerGameModeHandleInventoryMouseClickEvent
   ) {
      if (!this.shouldReturnAutomatically()) {
         return
      }

      this.returnController.onInventoryMouseClick(event)
   }

   @EventHandler
   private fun onMoveItemStack(
      event: AbstractContainerMenuMoveItemStackToEvent
   ) {
      if (this.trackingQuickMove) {
         val movedStack = event.slot.item
         if (!movedStack.isEmpty) {
            this.quickMoveDestination = event.slot
            return
         }
      }
   }

   @EventHandler
   private fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
      val world: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val blockState: BlockPos = event.hitResult.blockPos
      val interactedState: BlockState = world.getBlockState(blockState)
      val fullOption: Block = Blocks.ENDER_CHEST
      if (interactedState.`is`(fullOption)) {
         this.lastInteractedBlock = event.hitResult.blockPos
      }

      if (this.fullSetting.value!!) {
         this.placementController.onUseItemOn(event)
      }
   }

   fun recordTransferAnimation(
      sourceSlot: Slot, destinationSlot: Slot, stack: ItemStack
   ) {
      val screenAccessor = ClientAccess.collectiveMinecraft.gui.screen() as AbstractContainerScreenAccessor?
      this.transferredThisTick = true
      this.hasTransferred = true
      if (this.animateSetting.value!!) {
         val accessor = screenAccessor!!
         this.transferAnimations.add(
            InventoryTransferAnimation(
               stack,
               IntPair(accessor.x + sourceSlot.x, accessor.y + sourceSlot.y),
               IntPair(accessor.x + destinationSlot.x, accessor.y + destinationSlot.y)
            )
         )
      }
   }

   private fun collectTransferStacks(
      startSlot: Int, endSlot: Int, mode: ChestStealerModeChoice
   ): List<SlotStack> {
      val stacks = ArrayList<SlotStack>()

      for (slotIndex in startSlot until endSlot) {
         val stack = ClientAccess.collectiveMinecraft.player!!.containerMenu.getSlot(slotIndex).item
         if (!stack.isEmpty) {
            val searchBar = chestSearchBar

            if (!searchBar!!.shouldSkip(stack)) {
               if (this.lookForSetting.value == ChestStealerLookForChoice.ITEM) {
                  val itemFilterMode = this.selectSetting.value
                  val item = stack.item
                  val itemsOption = this.itemsSetting
                  if (!ListFilterMode.matches(itemFilterMode, item, itemsOption)) {
                     continue
                  }
               } else {
                  val nameFilterMode = this.selectSetting.value
                  var itemName = stack.hoverName.getString()
                  itemName = itemName.lowercase(Locale.ROOT)
                  val namesOption = this.namesSetting
                  if (!ListFilterMode.matches(nameFilterMode, itemName, namesOption)) {
                     continue
                  }
               }

               stacks.add(SlotStack(slotIndex, stack))
            }
         }
      }

      if (mode == ChestStealerModeChoice.REFILL || mode == ChestStealerModeChoice.REKIT) {
         stacks.sortWith(Comparator.comparingDouble { slotStack: SlotStack -> this.transferPriority(slotStack).toDouble() })
      }

      return stacks
   }

   fun requestMode(mode: ChestStealerModeChoice?) {
      this.requestedMode = mode
   }

   private fun transferPriority(slotStack: SlotStack): Float {
      val stack = slotStack.stack
      if (!stack.isStackable) {
         val weightedStack = slotStack.stack

         return -DamageUtils.getDurabilityPercent(weightedStack) / 1e+02f
      } else {
         val countedStack = slotStack.stack
         return (-countedStack.count).toFloat()
      }
   }

   private fun hasEnoughCapacity(capacities: List<SlotCapacity>, requiredCount: Int): Boolean {
      return capacities.sumOf { capacityEntry -> capacityEntry.capacity - capacityEntry.count } >= requiredCount
   }

   private fun distributeStackWithPickup(sourceSlotIndex: Int, capacities: List<SlotCapacity>, remainingCount: Int) {
      var remainingCount = remainingCount
      val pickupClickType = ContainerInput.PICKUP
      val pickupButton = 0
      InventoryUtils.clickSlot(sourceSlotIndex, pickupButton, pickupClickType)
      for (capacityEntry in capacities) {
         val menu = ClientAccess.collectiveMinecraft.player!!.containerMenu

         val destinationSlotIndex = capacityEntry.slotIndex
         val destinationSlot = menu.getSlot(destinationSlotIndex)

         val clickSlotIndex = capacityEntry.slotIndex
         val destinationClickType = ContainerInput.PICKUP
         val clickButton = 0
         InventoryUtils.clickSlot(clickSlotIndex, clickButton, destinationClickType)

         // В слот реально переезжает min(свободно, остаток): счётчик ёмкости
         // должен расти на movedCount, а не на весь remainingCount (иначе
         // count выходит за capacity и модель ёмкости ломается).
         val movedCount = Math.min(capacityEntry.capacity - capacityEntry.count, remainingCount)

         val updatedCount = capacityEntry.count + movedCount

         capacityEntry.setCount(updatedCount)
         remainingCount -= movedCount
         val sourceSlot = ClientAccess.collectiveMinecraft.player!!.containerMenu.getSlot(sourceSlotIndex)
         val animationStack = destinationSlot.item.copyWithCount(movedCount)
         this.recordTransferAnimation(sourceSlot, destinationSlot, animationStack)

         if (InventoryUtils.getCarriedStack().isEmpty) {
            break
         }
      }

      val carriedStack = InventoryUtils.getCarriedStack()
      if (!carriedStack.isEmpty) {
         val returnClickType = ContainerInput.PICKUP
         val returnButton = 0
         InventoryUtils.clickSlot(sourceSlotIndex, returnButton, returnClickType)
      }
   }

   @EventHandler
   private fun onRender(event: AbstractContainerScreenEvent) {
      synchronized(this.transferAnimations) {
         this.transferAnimations.removeIf(InventoryTransferAnimation::isFinished)
         val animationIterator = this.transferAnimations.iterator()

         while (animationIterator.hasNext()) {
            val animation = animationIterator.next()
            val graphics = event.graphics

            if (graphics != null) {
               animation.render(graphics)
            }
         }
      }
   }

   private fun collectDestinationCapacities(startSlot: Int): Map<StackGroupKey, MutableList<SlotCapacity>> {
      val capacitiesByStack = HashMap<StackGroupKey, MutableList<SlotCapacity>>()
      var relativeSlotIndex = 0

      while (true) {
         if (relativeSlotIndex >= 36) {
            return capacitiesByStack
         }

         val candidateSlotIndex = relativeSlotIndex + startSlot
         val slots = ClientAccess.collectiveMinecraft.player!!.containerMenu.slots
         if (candidateSlotIndex < slots.size) {
            val menuSlotIndex = startSlot + relativeSlotIndex
            val stack = ClientAccess.collectiveMinecraft.player!!.containerMenu.getSlot(menuSlotIndex).item
            val stackCount = stack.count
            if (stackCount < stack.maxStackSize) {
               if (!stack.isEmpty) {
                  if (stack.isStackable) {
                     val components: DataComponentMap = stack.components
                     val key = StackGroupKey(stack.item, components)
                     capacitiesByStack.putIfAbsent(key, ArrayList())
                     val maxCount = stack.maxStackSize
                     val matchingCapacities = capacitiesByStack[key]
                     val capacitySlotIndex = startSlot + relativeSlotIndex
                     matchingCapacities!!.add(SlotCapacity(capacitySlotIndex, maxCount, stack.count))
                  }
               }
            }
         }

         relativeSlotIndex++
      }
   }

   @EventHandler
   private fun onSendPosition(event: LocalPlayerSendPositionEvent) {
      if (event.stage == EventStage.Post) {
         return
      }
      this.transferredThisTick = false
      if (ClientAccess.collectiveMinecraft.player!!.containerMenu === ClientAccess.collectiveMinecraft.player!!.inventoryMenu) {
         return
      }
      val screen = ClientAccess.collectiveMinecraft.gui.screen()
      val shulkerScreen = screen is ShulkerBoxScreen
      val onlyButtonsEnabled: Boolean = this.onlyButtonsSetting.value!!
      val selectedModeValue: ChestStealerModeChoice?
      if (onlyButtonsEnabled) {
         selectedModeValue = this.requestedMode
      } else {
         selectedModeValue = this.modeSetting.value
      }
      if (screen !is ContainerScreen &&
         screen !is ShulkerBoxScreen
      ) {
         return
      }
      val delayTimer = this.transferDelayTimer
      val delayMillis = this.delaySetting.value!!.toLong()
      if (!delayTimer.hasElapsed(delayMillis)) {
         return
      }
      if ((this.onlyButtonsSetting.value!!) && this.requestedMode == null) {
         return
      }
      val inventoryCoordinator = ClientContext.inventoryStateManager
      if (InventoryStateManager.isClickRateLimited(inventoryCoordinator)) {
         return
      }
      if (!this.hasScreenOpenTimedOut() || screen is ShulkerPreviewScreen) {
         return
      }
      if (screen !is ShulkerBoxScreen &&
         selectedModeValue == ChestStealerModeChoice.REKIT
      ) {
         val requireButtons: Boolean = this.onlyButtonsSetting.value!!
         if (!requireButtons) {
            return
         }
      }

      val menu: AbstractContainerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      var startSlot = 0

      var slotCount = 27
      val containerId = menu.containerId
      if (screen is ContainerScreen) {
         val chestMenu = screen.menu as? ChestMenu ?: return
         slotCount = 9 * chestMenu.rowCount
      }

      val capacitiesByStack: Map<StackGroupKey, MutableList<SlotCapacity>> = this.collectDestinationCapacities(slotCount)
      var missingRekitSlot = false
      if (selectedModeValue == ChestStealerModeChoice.FILL) {
         startSlot = slotCount

         slotCount = 36
      } else if (selectedModeValue == ChestStealerModeChoice.REKIT) {
         missingRekitSlot = this.hasFreeRekitSlot(
            startSlot, slotCount
         )
      }

      var transfers = 0
      val endSlot = startSlot + slotCount
      for (slotStack in this.collectTransferStacks(startSlot, endSlot, selectedModeValue!!)) {
         val stack = slotStack.stack
         val slotIndex = slotStack.slotIndex
         val item = stack.item
         val matchingCapacities = capacitiesByStack[StackGroupKey(item, stack.components)]
         when (selectedModeValue!!) {
            ChestStealerModeChoice.DROP -> {
               val dropCoordinator = ClientContext.inventoryStateManager

               val dropClickCount = 1
               if (InventoryStateManager.wouldReachClickLimit(dropCoordinator, dropClickCount)
                  )
                {
                  continue
               }

               val throwGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
               val throwButton = 1
               val throwType = ContainerInput.THROW
               val throwPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               throwGameMode.handleContainerInput(containerId, slotIndex, throwButton, throwType, throwPlayer)
               this.transferredThisTick = true
               this.hasTransferred = true
            }
            ChestStealerModeChoice.FILL, ChestStealerModeChoice.STEAL -> {
               if (shulkerScreen) {
                  val shulkerItem = stack.item

                  if (ItemUtils.isShulkerBox(shulkerItem)) {
                     continue
                  }
               }

               val quickMoveCoordinator = ClientContext.inventoryStateManager

               val quickMoveClickCount = 1
               if (InventoryStateManager.wouldReachClickLimit(quickMoveCoordinator, quickMoveClickCount)
                  )
                {
                  continue
               }

               this.quickMoveDestination = null
               this.trackingQuickMove = true
               val quickMoveGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
               val quickMoveButton = 0
               val quickMoveType = ContainerInput.QUICK_MOVE
               val quickMovePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               quickMoveGameMode.handleContainerInput(containerId, slotIndex, quickMoveButton, quickMoveType, quickMovePlayer)
               this.trackingQuickMove = false
               if (this.quickMoveDestination != null) {
                  val sourceSlot = menu.getSlot(slotIndex)
                  val destinationSlot = this.quickMoveDestination
                  val movedStack = destinationSlot!!.item
                  this.recordTransferAnimation(sourceSlot, destinationSlot, movedStack)
               }
            }
            ChestStealerModeChoice.REFILL -> {
               if (matchingCapacities == null) {
                  continue
               }

               this.refillStack(slotIndex, matchingCapacities, false)
            }
            ChestStealerModeChoice.REKIT -> {
               if (matchingCapacities != null && !missingRekitSlot) {
                  this.refillStack(slotIndex, matchingCapacities, false)
               } else {
                  if (!this.moveRekitStack(
                     stack, slotIndex, slotCount
                  )
                  ) {
                     continue
                  }
               }
            }
         }

         this.transferDelayTimer.reset()
         val postTransferCoordinator = ClientContext.inventoryStateManager

         if (InventoryStateManager.isClickRateLimited(postTransferCoordinator)) {
            break
         }

         transfers++
         val frequencyValue: Int = this.frequencySetting.value!!
         if (transfers >= frequencyValue) {
            val frequencyOption: Setting<Int> = this.frequencySetting

            if (!frequencyOption.isAtMaximum()) {
               break
            }
         }
      }

      if (this.transferredThisTick) {
         this.idleCloseTimer.reset()
      }

      if (this.idleCloseTimer.hasElapsed(100L) &&
         this.hasTransferred
      ) {
         if (this.shouldCloseAutomatically()) {
            val closeDelay = (ClientContext.serverStateManager!!.latencyMillis / 5e+01f + 1.0f).toInt()
            ClientAccess.collectiveMinecraft.gui.setScreen(null)
            val closeScheduler = ClientContext.taskScheduler
            val closeContainer = Runnable {
               if (ClientAccess.collectiveMinecraft.player != null &&
         ClientAccess.collectiveMinecraft.player!!.containerMenu === menu
               ) {
                  ClientAccess.collectiveMinecraft.player!!.closeContainer()
               }
            }
            closeScheduler!!.scheduleRotationTask(closeContainer, closeDelay)
         }
      }

   }

   @EventHandler
   private fun onRotationUpdate(event: RotationRequestEvent) {
      if (this.shouldReturnAutomatically()) {
         this.returnController.reopenRequestedShulker()
      }

      if (this.fullSetting.value!!) {
         this.openController.openPendingShulker()
      }
   }

   @EventHandler
   private fun onPacketReceive(
      event: PacketEvent.Receive
   ) {
      val packet = event.packet
      if (packet is ClientboundContainerSetSlotPacket) {
         if (this.shouldReturnAutomatically()) {
            val animationMinecraft: Minecraft = ClientAccess.collectiveMinecraft
            val recordAnimation = Runnable {
               val returnHelper = this.returnController
               val containerId = packet.containerId
               val updatedStack = packet.item
               returnHelper.onContainerSlotUpdate(containerId, updatedStack)
            }
            animationMinecraft.execute(recordAnimation)
         }
      }
   }

   @EventHandler
   private fun onScreenChange(
      event: ScreenChangeEvent.AfterInit
   ) {
      this.hasTransferred = false

      this.screenOpenTimer.reset()
      this.transferAnimations.clear()
      this.slotCooldowns.clear()
   }

   private fun isSlotCoolingDown(slotIndex: Int): Boolean {
      val expiresAtMillis = this.slotCooldowns[slotIndex]
      if (expiresAtMillis != null) {
         if (System.currentTimeMillis() < expiresAtMillis) {
            return true
         }
      }

      return false
   }

   private fun hasFreeRekitSlot(startSlot: Int, slotCount: Int): Boolean {
      val menu: AbstractContainerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      val kitRegistry = ClientContext.kitRegistry

      val profile = KitRegistry.getCurrentKit(kitRegistry)
      if (profile == null) {
         return false
      }

      for (relativeSlotIndex in startSlot until startSlot + slotCount) {
         if (!this.isSlotCoolingDown(relativeSlotIndex)) {
            val sourceStack = menu.getSlot(relativeSlotIndex).item

            val profileEntries = profile.itemsBySlot.entries.iterator()

            while (profileEntries.hasNext()) {
               val profileEntry = profileEntries.next()
               if (profileEntry.value.matches(sourceStack)) {
                  val hotbarDestination = profileEntry.key < 9
                  val destinationOffset = if (hotbarDestination) {
                     profileEntry.key + 27
                  } else {
                     profileEntry.key - 9
                  }

                  val destinationSlotIndex = slotCount + destinationOffset

                  if (!this.isSlotCoolingDown(destinationSlotIndex)) {
                     val destinationStack = menu.getSlot(destinationSlotIndex).item
                     val destinationDescriptor = profileEntry.value
                     if (!destinationDescriptor.matches(destinationStack)) {
                        val destinationItem = destinationStack.item

                        if (!ItemUtils.isShulkerBox(destinationItem)) {
                           if (profileEntry.value.itemId() != 0) {
                              return true
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return false
   }

   data class SlotStack(
      val slotIndex: Int,
      val stack: ItemStack
   ) {
   }

   companion object {
      private var chestSearchBar: ChestSearchBarModule? = null

      @JvmStatic
      fun getLastInteractedBlock(module: ChestStealerModule): BlockPos? {
         return module.lastInteractedBlock
      }

      @JvmStatic
      fun bindReferencedModules(moduleRegistry: ModuleRegistry) {
         chestSearchBar = moduleRegistry.getModuleOrNull(ChestSearchBarModule::class.java)
      }

      private fun createAliases(): Array<String> {
         val aliases = arrayOf("stealer", "regear", "autoregear")
         return aliases
      }
   }
}
