package me.collective.client.modules.player


import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ScreenChangeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.managers.TaskScheduler
import me.collective.client.managers.RotationManager
import me.collective.client.utils.collections.ObjectPair
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode

import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import net.minecraft.client.ClientRecipeBook
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CraftingScreen
import net.minecraft.client.gui.screens.recipebook.RecipeCollection
import net.minecraft.client.gui.screens.recipebook.RecipeCollection.CraftableStatus
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket
import net.minecraft.util.Mth
import net.minecraft.util.context.ContextMap
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.inventory.RecipeBookType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.SmithingTemplateItem
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry
import net.minecraft.world.item.crafting.display.RecipeDisplayId
import net.minecraft.world.item.crafting.display.SlotDisplayContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class AutoCraftModule : Module("AutoCraft", "Automatically crafts listed items.", ModuleCategory.PLAYER, "craftbot") {
   private val itemsSetting: Setting<Set<Item>>
   private val recipeBlackListSetting: Setting<Set<Item>>
   private val delaySetting: Setting<Int>
   private val frequencySetting: Setting<Int>
   private val inInventorySetting: Setting<Boolean>
   internal val dropSetting: Setting<DropChoice>
   internal val craftAllSetting: Setting<Boolean>
   private val fastCloseSetting: Setting<Boolean>
   private val autoOpenSetting: Setting<Boolean>
   private val rangeSetting: Setting<Float>
   private val raytraceSetting: Setting<Boolean>
   private val strictDirectionSetting: Setting<Boolean>
   private val rotateSetting: Setting<Boolean>
   private val limitSetting: Setting<Boolean>
   private val maxSetting: Setting<Int>
   private val stackLimitSetting: Setting<Boolean>
   private val craftTimer: ElapsedTimer
   private val openTimer: ElapsedTimer
   private val cachedRecipes: MutableList<CachedRecipe>
   private var recipesCached: Boolean = false
   private var recipeBookWasOpen: Boolean? = null

   private fun hasPotentialRecipe(): Boolean {
      if ((this.itemsSetting.value).isEmpty()) {
         return false
      }

      if (!this.recipesCached) {
         return true
      }

      for (recipe in this.cachedRecipes) {
         val output = recipe.output

         if (this.shouldCraftRecipe(output, recipe.ingredients)) {
            return true
         }
      }

      return false
   }

   private fun findCraftableRecipe(): RecipeDisplayEntry? {
      if ((this.itemsSetting.value).isEmpty()) {
         return null
      }

      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val displayContext: ContextMap = SlotDisplayContext.fromLevel(level)
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val recipeBook: ClientRecipeBook = player.recipeBook
      for (collection in recipeBook.collections) {
         val status: CraftableStatus = CraftableStatus.ANY

         val selectedRecipes = collection.getSelectedRecipes(status)
         for (recipe in selectedRecipes) {
            val display: RecipeDisplay = recipe.display()
            val output = display.result().resolveForFirstStack(displayContext)

            val capturedRequirements = recipe.craftingRequirements()
            val emptyRequirements: List<Ingredient> = Collections.emptyList()
            val ingredients = capturedRequirements.orElse(emptyRequirements)
            if (this.shouldCraftRecipe(output, ingredients)) {
               return recipe
            }
         }
      }

      return null
   }

   private fun cacheRecipeDisplays() {
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val displayContext: ContextMap = SlotDisplayContext.fromLevel(level)
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val recipeBook: ClientRecipeBook = player.recipeBook
      for (collection in recipeBook.collections) {
         val status: CraftableStatus = CraftableStatus.ANY

         val selectedRecipes = collection.getSelectedRecipes(status)
         for (recipe in selectedRecipes) {
            val display: RecipeDisplay = recipe.display()
            val output = display.result().resolveForFirstStack(displayContext)

            val capturedRequirements = recipe.craftingRequirements()
            val emptyRequirements: List<Ingredient> = Collections.emptyList()
            val ingredients = capturedRequirements.orElse(emptyRequirements)
            this.cachedRecipes.add(CachedRecipe(output, ingredients))
         }
      }
   }

   private fun findCraftingTable(): ObjectPair? {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val capturedEyes: Vec3 = player.getEyePosition()
      val capturedRange: Float = this.rangeSetting.value
      val searchFlag: Boolean = true

      for (position in WorldUtils.collectSphereBlocks(capturedEyes, capturedRange, searchFlag)) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val capturedState: BlockState = level.getBlockState(position)
         val craftingTable = Blocks.CRAFTING_TABLE
         if (!capturedState.`is`(craftingTable)) {
            continue
         }
         val visibleSamples: List<Vec3> = BlockSampleMode.X8.sampleBlock(position)
         if (!WorldUtils.canSeeAny(visibleSamples)) {
            if (this.raytraceSetting.value) {
               continue
            }
         }
         val validDirections: List<Direction> = BlockInteractionUtils.getStrictDirections(position)
         if (validDirections.isEmpty()) {
            if (this.strictDirectionSetting.value) {
               continue
            }
         }
         return ObjectPair(position, if (validDirections.isEmpty()) Direction.UP else validDirections[0])
      }
      return null
   }

   override fun onEnable() {
      this.cachedRecipes.clear()
      this.recipesCached = false
   }

   override fun onDisable() {
      if (!this.isWorldUnavailable()) {
         this.restoreRecipeBook()
      }
   }

   private fun restoreRecipeBook() {
      if (this.recipeBookWasOpen == null) {
         return
      }
      val wasOpen: Boolean = this.recipeBookWasOpen!!
      this.recipeBookWasOpen = null
      if (wasOpen) {
         return
      }
      val stateBook: ClientRecipeBook = ClientAccess.collectiveMinecraft.player!!.recipeBook
      val stateType: RecipeBookType = RecipeBookType.CRAFTING
      if (!stateBook.isOpen(stateType)) {
         return
      }
      val closeBook: ClientRecipeBook = ClientAccess.collectiveMinecraft.player!!.recipeBook
      val closeType: RecipeBookType = RecipeBookType.CRAFTING
      closeBook.setOpen(closeType, false)
      val filterBook: ClientRecipeBook = ClientAccess.collectiveMinecraft.player!!.recipeBook
      val filterType: RecipeBookType = RecipeBookType.CRAFTING
      val filtering: Boolean = filterBook.isFiltering(filterType)
      PacketSender.send(ServerboundRecipeBookChangeSettingsPacket(RecipeBookType.CRAFTING, false, filtering))
   }

   private fun hasIngredients(ingredients: Collection<Ingredient>): Boolean {
      if (ingredients.isEmpty()) {
         return false
      }

      val itemCounts: HashMap<Item, Int> = HashMap()
      val slots = ClientAccess.collectiveMinecraft.player!!.containerMenu.slots
      for (slot in slots) {
         val stack: ItemStack = slot.item
         itemCounts.compute(stack.item) { item, previousCount ->
            if (item != null && previousCount != null) {
               return@compute slot.item.count + previousCount
            } else {
               return@compute slot.item.count
            }
         }
      }

      for (ingredient in ingredients) {
         if (!ingredient.isEmpty) {
            var matchedIngredient = false

            for (itemHolder in ingredient.items().toList()) {
               if ((this.recipeBlackListSetting.value).contains(itemHolder.value())) {
                  // Забанена одна альтернатива ингредиента — пропускаем её,
                  // а не отменяем весь рецепт: return false срабатывает ниже,
                  // если не осталось ни одного варианта.
                  continue
               }

               if (itemCounts.containsKey(itemHolder.value())) {
                  val availableCount = itemCounts[itemHolder.value()]

                  if (availableCount!! - 1 >= 0) {
                     val consumedItem: Item = itemHolder.value()

                     val remainingCount: Int = availableCount!! - 1
                     itemCounts.replace(consumedItem, remainingCount)
                     matchedIngredient = true
                     break
                  }
               }
            }

            if (!matchedIngredient) {
               return false
            }
         }
      }

      return true
   }

   private fun shouldCraftRecipe(output: ItemStack?, ingredients: List<Ingredient>): Boolean {
      if (output == null) {
         return false
      }
      var effectiveIngredients = ingredients
      if (output.item == null || output.item is SmithingTemplateItem) {
         return false
      }
      if (output.`is`(Items.FIREWORK_ROCKET)) {
         effectiveIngredients = rocketIngredients
      }
      val selectedItems: Set<*> = this.itemsSetting.value as Set<*>
      if (!selectedItems.contains(output.item) || !this.hasIngredients(effectiveIngredients)) {
         return false
      }
      val outputItem: Item = output.item
      return this.isBelowItemLimit(outputItem)
   }

   @EventHandler
   private fun prepareCraftingScreen(
      event: ScreenChangeEvent.Before
   ) {
      if (event.newScreen is CraftingScreen) {
         if (this.recipeBookWasOpen == null) {
            val stateBook: ClientRecipeBook = ClientAccess.collectiveMinecraft.player!!.recipeBook
            val stateType: RecipeBookType = RecipeBookType.CRAFTING
            this.recipeBookWasOpen = stateBook.isOpen(stateType)
         }
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val capturedRecipeBook: ClientRecipeBook = player.recipeBook
         val open: Boolean = true
         val recipeBookType: RecipeBookType = RecipeBookType.CRAFTING

         capturedRecipeBook.setOpen(recipeBookType, open)
         val scheduler: TaskScheduler = ClientContext.taskScheduler
         val cacheCallback = Runnable {
            if (!this.recipesCached) {
               this.cacheRecipeDisplays()
               this.recipesCached = true
            }
         }

         scheduler.scheduleRotationTask(cacheCallback, 2)
         if ((this.fastCloseSetting.value) && event.newScreen is CraftingScreen) {
            val capturedScreen = event.newScreen
            val height: Byte = 0
            val width: Byte = 0

            capturedScreen!!.init(ClientAccess.collectiveMinecraft, width.toInt(), height.toInt())

            event.cancel()
            val closeScheduler: TaskScheduler = ClientContext.taskScheduler
            val closeCallback = Runnable {
               val containerId: Int = ClientAccess.collectiveMinecraft.player!!.containerMenu.containerId
               val closePacket = ServerboundContainerClosePacket(containerId)
               PacketSender.send(closePacket)
               ClientAccess.collectiveMinecraft.player!!.containerMenu = ClientAccess.collectiveMinecraft.player!!
                  .inventoryMenu
            }
            closeScheduler.scheduleRotationTask(closeCallback, 0)
         }
      }
   }

   @EventHandler
   private fun craftSelectedItems(event: LocalPlayerPreTickEvent) {
      if (this.recipeBookWasOpen != null &&
         ClientAccess.collectiveMinecraft.screen !is CraftingScreen &&
         ClientAccess.collectiveMinecraft.player!!.containerMenu !is CraftingMenu
      ) {
         this.restoreRecipeBook()
      }
      val recipe = this.findCraftableRecipe()
      if (this.autoOpenSetting.value) {
         if (this.openTimer.hasElapsed(500L) &&
         ClientAccess.collectiveMinecraft.screen !is AbstractContainerScreen<*>
         ) {
            val craftingTable = this.findCraftingTable()
            if (craftingTable != null && (recipe != null || this.hasPotentialRecipe())) {
               if (this.rotateSetting.value) {
                  val tableCenter = (craftingTable.first() as BlockPos).getCenter()

                  val capturedAngles = RotationManager.getRotationsToPosition(
                     tableCenter
                  )
                  val onGround: Boolean = ClientAccess.collectiveMinecraft.player!!.onGround()

                  PacketSender.sendRotation(capturedAngles, onGround)
               }

               val capturedHand: InteractionHand = InteractionHand.MAIN_HAND

               val hitCenter = (craftingTable.first() as BlockPos).getCenter()

               val hitDirection = craftingTable.second() as Direction

               val hitResult = BlockHitResult(
                  hitCenter,
                  hitDirection,
                  craftingTable.first() as BlockPos,
                  false
               )

               PacketSender.sendUseItemOn(capturedHand, hitResult)
               val swingHand: InteractionHand = InteractionHand.MAIN_HAND
               PacketSender.sendSwing(swingHand)

               this.openTimer.reset()
            }
         }
      }

      if (recipe == null) {
         return
      }
      if (ClientAccess.collectiveMinecraft.player!!.containerMenu !is CraftingMenu &&
         !(this.inInventorySetting.value &&
         ClientAccess.collectiveMinecraft.player!!.containerMenu === ClientAccess.collectiveMinecraft.player!!.inventoryMenu &&
         ClientAccess.collectiveMinecraft.screen is InventoryScreen)
      ) {
         return
      }
      if ((this.itemsSetting.value).isEmpty()) {
         return
      }
      val capturedCraftTimer = this.craftTimer
      val delayMillis: Long = (this.delaySetting.value).toLong()
      if (!capturedCraftTimer.hasElapsed(delayMillis)) {
         return
      }
      val craftingMenu: AbstractContainerMenu = ClientAccess.collectiveMinecraft.player!!
         .containerMenu
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val displayContext: ContextMap = SlotDisplayContext.fromLevel(level)

      val display: RecipeDisplay = recipe.display()
      val output = display.result().resolveForFirstStack(displayContext)
      val rocket = Items.FIREWORK_ROCKET

      if (!output.`is`(rocket)) {
         var requestIndex = 0
         while (true) {
            val requestedOutputItem: Item = output.item

            if (requestIndex
               >= this.getCraftRequestCount(requestedOutputItem)
            ) {
               break
            }

            val capturedRecipeGameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            val capturedRecipeContainerId: Int = craftingMenu.containerId

            val capturedRecipeId: RecipeDisplayId = recipe.id()
            val placeAll: Boolean = this.craftAllSetting.value

            capturedRecipeGameMode.handlePlaceRecipe(capturedRecipeContainerId, capturedRecipeId, placeAll)
            requestIndex++
         }
      } else {
         val inventoryCrafting: Boolean = craftingMenu === ClientAccess.collectiveMinecraft.player!!.inventoryMenu
         var gunpowderSlot = -1
         var paperSlot = -1
         var gunpowderUnits = 0

         var sourceSlotIndex = if (inventoryCrafting) 9 else 10

         while (true) {
            if (sourceSlotIndex >= (if (inventoryCrafting) 45 else 46)) {
               break
            }

            val sourceStack: ItemStack = craftingMenu.getSlot(sourceSlotIndex).item
            val gunpowder = Items.GUNPOWDER
            if (sourceStack.`is`(gunpowder)) {
               if (sourceStack.count == sourceStack.maxStackSize) {
                  gunpowderUnits++
               }

               if (gunpowderSlot == -1) {
                  gunpowderSlot = sourceSlotIndex
               } else {
                  if (sourceStack.count == sourceStack.maxStackSize) {
                     gunpowderSlot = sourceSlotIndex
                  }
               }
            }

            val paper = Items.PAPER
            if (sourceStack.`is`(paper) && paperSlot == -1) {
               paperSlot = sourceSlotIndex
            }

            sourceSlotIndex++
         }

         if (paperSlot != -1) {
            val paperGridSlot: Byte = 1
            if (craftingMenu.getSlot(paperGridSlot.toInt()).item.isEmpty) {
               val capturedResetTimer = this.craftTimer

               capturedResetTimer.reset()

               val paperTargetSlot: Byte = 1
               InventoryUtils.swapSlotsWithPickup(
                  paperSlot, paperTargetSlot.toInt()
               )
               return
            }
         }

         val gunpowderGridSlots = if (inventoryCrafting) intArrayOf(2, 3, 4) else intArrayOf(2, 4, 5)
         var gridHasGunpowder = true

         for (gridSlotIndex in gunpowderGridSlots) {
            val gridStack: ItemStack = craftingMenu.getSlot(gridSlotIndex).item
            val gridGunpowder = Items.GUNPOWDER

            if (!gridStack.`is`(gridGunpowder)) {
               gridHasGunpowder = false
            } else {
               if (gridStack.count == gridStack.maxStackSize) {
                  gunpowderUnits++
               }
            }
         }

         if (gunpowderSlot != -1 && !gridHasGunpowder) {
            InventoryUtils.clickSlot(craftingMenu.containerId, gunpowderSlot, 0, ClickType.PICKUP)

            for (targetGridSlot in gunpowderGridSlots) {
               val capturedPlacementStack: ItemStack = craftingMenu.getSlot(targetGridSlot).item
               val placementGunpowder = Items.GUNPOWDER

               if (!capturedPlacementStack.`is`(placementGunpowder)) {
                  val carriedStack: ItemStack = craftingMenu.carried
                  if (!carriedStack.isEmpty) {
                     val targetGridButton = if (gunpowderUnits >= (3)) 0 else 1
                     InventoryUtils.clickSlot(craftingMenu.containerId, targetGridSlot, targetGridButton, ClickType.PICKUP)
                  }
                  break
               }
            }

            InventoryUtils.clickSlot(craftingMenu.containerId, gunpowderSlot, 0, ClickType.PICKUP)
            val capturedResetTimer = this.craftTimer

            capturedResetTimer.reset()
            return
         }

         val resultIndex: Byte = 0

         val rocketResult: ItemStack = craftingMenu.getSlot(resultIndex.toInt()).item
         val fireworksComponent: DataComponentType<Fireworks> = DataComponents.FIREWORKS
         val defaultFireworks = Fireworks(0, Collections.emptyList())
         val fireworks = rocketResult.getOrDefault(fireworksComponent, defaultFireworks) as Fireworks
         val resultRocket = Items.FIREWORK_ROCKET

         if (!rocketResult.`is`(resultRocket)) {
            return
         }

         val flightDuration: Int = fireworks.flightDuration()

         if (flightDuration != (3) || !gridHasGunpowder) {
            return
         }

         val capturedRocketTimer = this.craftTimer

         val rocketDelay: Int = this.delaySetting.value

         val rocketWaitMillis: Long = Math.max(200, rocketDelay).toLong()
         if (!capturedRocketTimer.hasElapsed(rocketWaitMillis)) {
            return
         }
      }

      val capturedResultContainerId: Int = craftingMenu.containerId
      val capturedClickType: ClickType = (this.dropSetting.value).getClickType()
      InventoryUtils.clickSlot(capturedResultContainerId, 0, 1, capturedClickType)
      val carriedAfterResult: ItemStack = craftingMenu.carried
      if (this.dropSetting.value == DropChoice.Pickup) {
         // PICKUP уже смержил результат в carried, поэтому output.count + carried.count
         // считает результат дважды и при полном carried выбрасывался ВЕСЬ стак в руке
         // (включая вещи игрока). Выбрасываем только остаток из слота результата.
         val leftoverResult: ItemStack = craftingMenu.getSlot(0).item
         if (!leftoverResult.isEmpty) {
            val capturedOverflowContainerId: Int = craftingMenu.containerId

            InventoryUtils.clickSlot(capturedOverflowContainerId, 0, 1, ClickType.THROW)
         }
      }

      val capturedFinalTimer = this.craftTimer

      capturedFinalTimer.reset()
   }

   private fun countInventoryItems(item: Item): Int {
      var inventoryCount = 0
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val inventoryItems = player.inventory.items
      for (i in 0 until inventoryItems.size) {
         val stack = inventoryItems[i]
         if (stack.`is`(item)) {
            inventoryCount += stack.count
         }
      }
      return inventoryCount
   }

   private fun isBelowItemLimit(item: Item): Boolean {
      val limitEnabled: Boolean = this.limitSetting.value
      if (!limitEnabled) {
         return true
      }
      if (this.craftAllSetting.value) {
         return true
      }
      var maximumCount: Int = this.maxSetting.value
      if (this.stackLimitSetting.value) {
         maximumCount *= item.defaultMaxStackSize
      }

      return this.countInventoryItems(item) < maximumCount
   }

   private fun getCraftRequestCount(item: Item): Int {
      val limitEnabled: Boolean = this.limitSetting.value
      if (!limitEnabled) {
         return this.frequencySetting.value
      }

      var maximumCount: Int = this.maxSetting.value
      if (this.stackLimitSetting.value) {
         maximumCount *= item.defaultMaxStackSize
      }

      val inventoryCount: Int = this.countInventoryItems(item)

      val missingCount: Int = maximumCount - inventoryCount
      val maximumRequests: Int = this.frequencySetting.value
      val minimumRequests: Byte = 0

      return Mth.clamp(missingCount, minimumRequests.toInt(), maximumRequests)
   }

   init {
      this.itemsSetting = this.add(RegistrySetSetting<Item>("Items", BuiltInRegistries.ITEM, Items.GOLD_INGOT))
      this.recipeBlackListSetting = this.add(RegistrySetSetting<Item>("RecipeBlackList", BuiltInRegistries.ITEM, Items.GOLD_BLOCK))
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 150, 0, 1500).withUnit("ms"))
      this.inInventorySetting = this.add(BooleanSetting("InInventory", false))
      this.frequencySetting = this.add(NumberSetting<Int>("Frequency", 1, 1, 6))
      this.dropSetting = this.add(EnumSetting<DropChoice>("Drop", DropChoice.None))
      this.craftAllSetting = this.add(BooleanSetting("CraftAll", false) { ignored -> this.dropSetting.value == DropChoice.None })
      this.fastCloseSetting = this.add(BooleanSetting("FastClose", false))
      this.autoOpenSetting = this.add(BooleanSetting("AutoOpen", false).markVisibilityDependency())
      this.rangeSetting = this.add(NumberSetting<Float>("Range", 4.0F, 1.0F, 6.0F).withUnit("m").dependsOn(this.autoOpenSetting))
      this.raytraceSetting = this.add(BooleanSetting("Raytrace", false).dependsOn(this.autoOpenSetting))
      this.strictDirectionSetting = this.add(BooleanSetting("StrictDirection", false).dependsOn(this.autoOpenSetting))
      this.rotateSetting = this.add(BooleanSetting("Rotate", false).dependsOn(this.autoOpenSetting))
      this.limitSetting = this.add(BooleanSetting("Limit", false) { ignored -> !this.craftAllSetting.value }.markVisibilityDependency())
      this.maxSetting = this.add(NumberSetting<Int>("Max", 64, 1, 64) { ignored -> !this.craftAllSetting.value }.dependsOn(this.limitSetting))
      this.stackLimitSetting = this.add(BooleanSetting("StackLimit", false).dependsOn(this.limitSetting))
      this.craftTimer = ElapsedTimer()
      this.openTimer = ElapsedTimer()
      this.cachedRecipes = ArrayList()
   }

   enum class DropChoice(private val displayName: String, private val clickType: ClickType) : NamedEntry {
      None("None", ClickType.QUICK_MOVE),
      Throw("Throw", ClickType.THROW),
      Pickup("Pickup", ClickType.PICKUP);

      fun getClickType(): ClickType {
         return this.clickType
      }

      override fun getName(): String {
         return this.displayName
      }
   }

   private data class CachedRecipe(
      val output: ItemStack,
      val ingredients: List<Ingredient>
   )

   companion object {
      private val rocketIngredients: List<Ingredient>

      init {
         val paperIngredient = Ingredient.of(Items.PAPER)
         val gunpowderIngredient = Ingredient.of(Items.GUNPOWDER)

         rocketIngredients = java.util.List.of(paperIngredient, gunpowderIngredient)
      }
   }
}
