package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.combat.AutoCrystalModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.exploit.XCarryModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode

import java.util.function.Predicate
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ReplenishModule : Module("Replenish", "Replenishes your hotbar.", ModuleCategory.PLAYER) {
   private val itemsSetting: Setting<Set<Item>>
   private val selectionSetting: Setting<ListFilterMode>
   private val delaySetting: Setting<Int>
   private val thresholdSetting: Setting<Int>
   private val sortSetting: Setting<Boolean>
   private val unstackableSetting: Setting<Boolean>
   private val renamedSetting: Setting<Boolean>
   internal val limitSetting: Setting<Boolean>
   private val motionSetting: Setting<Boolean>
   private val fallingSetting: Setting<Boolean>
   private val rememberedHotbar: NonNullList<RememberedStack>
   private val replenishTimer: ElapsedTimer

   private fun replenishStack(hotbarSlot: Int): Boolean {
      val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
      val target: ItemStack = inventory.getItem(hotbarSlot)
      if (!ListFilterMode.matches(this.selectionSetting.value, target.getItem(),
            this.itemsSetting.value) || target.isEmpty() || !target.isStackable()) return false
      val threshold: Float = (this.thresholdSetting.value).toFloat() / 64.0f
      if (target.count.toFloat() / target.maxStackSize >= threshold) return false
      var sourceSlot = -1
      var source: ItemStack = ItemStack.EMPTY
      for (slot in 9..<36) {
         val candidate: ItemStack = inventory.getItem(slot)
         if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target) && candidate.count > source.count) {
            sourceSlot = slot
            source = candidate
         }
      }
      if (xCarryModule!!.isToggled() && !(ClientAccess.collectiveMinecraft.gui.screen() is InventoryScreen)) {
         val crafting: CraftingContainer = ClientAccess.collectiveMinecraft.player!!.inventoryMenu.craftSlots
         for (slot in 0..<4) {
            val candidate: ItemStack = crafting.getItem(slot)
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, target) && candidate.count > source.count) {
               sourceSlot = slot + 1
               source = candidate
            }
         }
      }
      if (sourceSlot == -1) return false
      val count = source.count
      val useSwap = count > (source.maxStackSize * threshold).toDouble() * 1.5 || count == source.maxStackSize
      ClientContext.inventoryStateManager.setClickBatching(true)
      try {
         if (this.sortSetting.value && count != 1 && target.maxStackSize - target.count > count) {
            InventoryUtils.clickSlot(sourceSlot, 0, ContainerInput.PICKUP)
            InventoryUtils.clickSlot(sourceSlot, 1, ContainerInput.PICKUP)
            InventoryUtils.clickSlot(InventoryUtils.toMenuSlot(hotbarSlot), 0, ContainerInput.PICKUP)
         } else if (InventoryUtils.isHotbarFull()) {
            InventoryUtils.clickSlot(sourceSlot, 0, ContainerInput.QUICK_MOVE)
         } else if (useSwap) {
            InventoryUtils.clickSlot(sourceSlot, hotbarSlot, ContainerInput.SWAP)
         } else {
            InventoryUtils.swapSlotsWithPickup(sourceSlot, InventoryUtils.toMenuSlot(hotbarSlot))
         }
      } finally {
         ClientContext.inventoryStateManager.setClickBatching(false)
      }
      return true
   }

   private fun areOtherScannedSlotsOccupied(excludedSlot: Int): Boolean {
      val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
      for (slot in 0..<9) {
         if (slot != excludedSlot && inventory.getItem(slot).isEmpty()) return false
      }
      return true
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.isWorldUnavailable() || !this.replenishTimer.hasElapsed((this.delaySetting.value).toLong())) return
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      if (!player.containerMenu.getCarried().isEmpty()) return
      if (MovementPhysics.isLocalPlayerMoving() && this.motionSetting.value) return
      if (player.deltaMovement.y() > 0.0 && this.fallingSetting.value) return
      if (multiTaskModule!!.isItemUseRestricted()) return
      val containerOpen = ClientAccess.collectiveMinecraft.gui.screen() is AbstractContainerScreen<*>
      for (slot in 0..<9) {
         val stack: ItemStack = player.inventory.getItem(slot)
         val remembered: RememberedStack = this.rememberedHotbar[slot]
         val restore = this.unstackableSetting.value &&
         (remembered.item.getDefaultMaxStackSize() == 1 || this.renamedSetting.value && remembered.customNamePresent)
         if (restore && stack.isEmpty() && remembered.item !== Items.AIR && !containerOpen && this.restoreMissingItem(remembered.item, slot)) {
            this.replenishTimer.reset()
            return
         }
         this.rememberedHotbar[slot] = RememberedStack.from(stack)
      }
      if (containerOpen && !(ClientAccess.collectiveMinecraft.gui.screen() is InventoryScreen)) return
      for (slot in 0..<9) {
         if (this.replenishStack(slot)) {
            this.replenishTimer.reset()
            return
         }
      }
   }

   private fun restoreMissingItem(item: Item, hotbarSlot: Int): Boolean {
      val selectionMode: ListFilterMode = this.selectionSetting.value
      val selectedItemValues: Collection<*> = this.itemsSetting.value
      if (!ListFilterMode.matches(selectionMode, item, selectedItemValues)) {
         return false
      }

      val forceSuicide = autoCrystalModule!!.isToggled() && autoCrystalModule!!.forceSuicideSetting.value
      val restorePredicate = Predicate<ItemStack> { candidateStack: ItemStack ->
         val offhandPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         candidateStack !== offhandPlayer.offhandItem &&
         (!forceSuicide || candidateStack.getItem() !== Items.TOTEM_OF_UNDYING)
      }
      var sourceSlot = InventoryUtils.findMenuSlot(item, restorePredicate, true)
      if (sourceSlot == -1 && item === Items.TOTEM_OF_UNDYING && !forceSuicide) {
         val fallbackPredicate = Predicate<ItemStack> { candidateStack: ItemStack ->
            val fallbackItem: Item = candidateStack.getItem()
            if (fallbackItem === Items.ENCHANTED_GOLDEN_APPLE || fallbackItem === Items.GOLDEN_APPLE) {
               val offhandPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
               candidateStack !== offhandPlayer.offhandItem
            } else {
               false
            }
         }
         sourceSlot = InventoryUtils.findInventorySlot(fallbackPredicate, true)
      }

      if (sourceSlot < 36 && sourceSlot != -1) {
         if (this.areOtherScannedSlotsOccupied(hotbarSlot)) {
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            val containerId = ClientAccess.collectiveMinecraft.player!!.containerMenu.containerId
            val clickSourceSlot = sourceSlot
            val mouseButton: Byte = 0
            val quickMove = ContainerInput.QUICK_MOVE
            val clickPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            gameMode.handleContainerInput(containerId, clickSourceSlot, mouseButton.toInt(), quickMove, clickPlayer)
         } else {
            val conversionHotbarSlot = hotbarSlot
            val targetSlot = InventoryUtils.toMenuSlot(conversionHotbarSlot)
            InventoryUtils.swapSlotsWithPickup(sourceSlot, targetSlot)
         }

         return true
      } else {
         return false
      }
   }

   init {
      this.itemsSetting = this.add(RegistrySetSetting<Item>("Items", BuiltInRegistries.ITEM,
         Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.EXPERIENCE_BOTTLE, Items.ENCHANTED_GOLDEN_APPLE))
      this.selectionSetting = this.add(EnumSetting("Selection", ListFilterMode.ANY))
      this.delaySetting = this.add(NumberSetting<Int>("Delay", 50, 0, 500).withUnit("ms"))
      this.thresholdSetting = this.add(NumberSetting<Int>("Threshold", 32, 16, 63))
      this.sortSetting = this.add(BooleanSetting("Sort", false))
      this.unstackableSetting = this.add(BooleanSetting("Unstackable", false).markVisibilityDependency())
      this.renamedSetting = this.add(BooleanSetting("Renamed", true).dependsOn(this.unstackableSetting))
      this.limitSetting = this.add(BooleanSetting("Limit", true).markAsGroup())
      val motionVisibility = Predicate<Any> { ignored: Any -> this.limitSetting.isExpanded() }
      this.motionSetting = this.add(BooleanSetting("Motion", false, motionVisibility))
      val fallingVisibility = Predicate<Any> { ignored: Any -> this.limitSetting.isExpanded() }
      this.fallingSetting = this.add(BooleanSetting("Falling", false, fallingVisibility))

      val emptyEntry: RememberedStack = RememberedStack.EMPTY
      this.rememberedHotbar = NonNullList.withSize<RememberedStack>(9, emptyEntry)
      this.replenishTimer = ElapsedTimer()

      this.setDrawn(false)
   }

   private class RememberedStack(
      val item: Item,
      val customNamePresent: Boolean
   ) {
      companion object {
         val EMPTY = RememberedStack(Items.AIR, false)

         fun from(
            stack: ItemStack
         ): RememberedStack {
            return RememberedStack(stack.getItem(), stack.has(DataComponents.CUSTOM_NAME))
         }
      }
   }

   companion object {
      private var multiTaskModule: MultiTaskModule? = null
      private var xCarryModule: XCarryModule? = null
      private var autoCrystalModule: AutoCrystalModule? = null

      @JvmStatic
      fun bindModules(registry: ModuleRegistry) {
         multiTaskModule = registry.getModuleOrNull(
            MultiTaskModule::class.java
         )
         xCarryModule = registry.getModuleOrNull(
            XCarryModule::class.java
         )
         autoCrystalModule = registry.getModuleOrNull(
            AutoCrystalModule::class.java
         )
      }
   }
}
