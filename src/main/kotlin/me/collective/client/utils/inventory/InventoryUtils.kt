package me.collective.client.utils.inventory

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.player.ItemSaverModule

import me.collective.mixin.MultiPlayerGameModeAccessor
import java.util.function.Predicate
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.equipment.Equippable
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState

class InventoryUtils private constructor() : ClientAccess {
   companion object {
      private val itemSaver: ItemSaverModule get() = ClientContext.getModule(ItemSaverModule::class.java)
      private val enderChestScreenTitle: Component = Component.translatable("container.enderchest")

      @JvmStatic
      fun selectHotbarSlot(slot: Int) {
         if (slot < 0 || slot >= 9) return
         val player = ClientAccess.collectiveMinecraft.player!!
         player.inventory.selectedSlot = slot
         (ClientAccess.collectiveMinecraft.gameMode!! as MultiPlayerGameModeAccessor).sync()
      }

      @JvmStatic
      fun swapIntoSelectedSlot(slot: Int) {
         if (slot >= 0) swapWithHotbarSlot(toMenuSlot(slot), getSelectedSlot())
      }

      @JvmStatic
      fun swapWithHotbarSlot(menuSlot: Int, hotbarSlot: Int) {
         if (menuSlot >= 0 && hotbarSlot >= 0) {
            val clickType = ContainerInput.SWAP
            clickSlot(menuSlot, hotbarSlot, clickType)
         }
      }

      @JvmStatic
      fun swapMenuSlots(firstSlot: Int, secondSlot: Int) {
         if (firstSlot < 0 || secondSlot < 0) return
         if (firstSlot < 36 || firstSlot > 44) {
            clickSlot(firstSlot, 0, ContainerInput.SWAP)
            clickSlot(secondSlot, 0, ContainerInput.SWAP)
            clickSlot(firstSlot, 0, ContainerInput.SWAP)
         } else {
            clickSlot(secondSlot, firstSlot - 36, ContainerInput.SWAP)
         }
      }

      @JvmStatic
      fun swapSlotsWithPickup(firstSlot: Int, secondSlot: Int) {
         if (firstSlot != -1 && secondSlot != -1) {
            clickSlot(firstSlot, 0, ContainerInput.PICKUP)
            clickSlot(secondSlot, 0, ContainerInput.PICKUP)
            val carriedStack = ClientAccess.collectiveMinecraft.player!!.containerMenu.carried
            if (!carriedStack.isEmpty) {
               clickSlot(firstSlot, 0, ContainerInput.PICKUP)
            }
         }
      }

      @JvmStatic
      fun clickSlot(slot: Int, button: Int, clickType: ContainerInput) {
         val player = ClientAccess.collectiveMinecraft.player!!
         val containerId = player.containerMenu.containerId
         ClientAccess.collectiveMinecraft.gameMode!!.handleContainerInput(
            containerId, slot, button, clickType, player
         )
      }

      @JvmStatic
      fun clickSlot(containerId: Int, slot: Int, button: Int, clickType: ContainerInput) {
         val gameMode = ClientAccess.collectiveMinecraft.gameMode!!
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!

         gameMode.handleContainerInput(containerId, slot, button, clickType, player)
      }

      @JvmStatic
      fun getSelectedSlot(): Int {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         return player.inventory.selectedSlot
      }

      @JvmStatic
      fun findMenuSlot(item: Item, includeCrafting: Boolean): Int {
         val predicate = Predicate<ItemStack> { stack -> true }
         return findMenuSlot(item, predicate, includeCrafting)
      }

      @JvmStatic
      fun findMenuSlot(item: Item): Int {
         return findMenuSlot(item, false)
      }

      @JvmStatic
      fun findInventorySlot(vararg items: Item): Int {
         return findInventorySlot(Predicate<ItemStack> { stack ->
            val item = stack.item
            items.any { candidate -> candidate == item }
         }, false)
      }

      @JvmStatic
      fun findMenuSlot(item: Item, predicate: Predicate<ItemStack>, includeCrafting: Boolean): Int {
         if (!ClientContext.inventoryStateManager.hasPendingLocalEntityEvent()) {
            val offhand = ClientAccess.collectiveMinecraft.player!!.offhandItem
            if (offhand.`is`(item) && predicate.test(offhand)) return -1
         }
         for (slot in 35 downTo 0) {
            val stack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot)
            if (stack.`is`(item) && predicate.test(stack)) return toMenuSlot(slot)
         }
         if (includeCrafting) {
            val menu: InventoryMenu = ClientAccess.collectiveMinecraft.player!!.inventoryMenu
            for (slot in 0..3) {
               val stack = menu.craftSlots.items[slot]
               if (stack.`is`(item) && predicate.test(stack)) return slot + 1
            }
         }
         return -1
      }

      @JvmStatic
      fun findInventorySlot(predicate: Predicate<ItemStack>, excludeHotbar: Boolean): Int {
         for (slot in 35 downTo (if (excludeHotbar) 9 else 0)) {
            val stack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot)
            if (predicate.test(stack)) return slot
         }
         return -1
      }

      @JvmStatic
      fun findHotbarSlotInItemOrder(vararg items: Item): Int {
         for (item in items) {
            for (slot in 0..8) {
               if (ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot).`is`(item)) return slot
            }
         }
         return -1
      }

      @JvmStatic
      fun findHotbarSlot(vararg items: Item): Int {
         return findHotbarSlot(Predicate<ItemStack> { stack ->
            items.any { item -> stack.`is`(item) }
         })
      }

      @JvmStatic
      fun findHotbarSlot(item: Item): Int {
         return findHotbarSlot(Predicate<ItemStack> { stack -> stack.`is`(item) })
      }

      @JvmStatic
      fun findHotbarSlot(predicate: Predicate<ItemStack>): Int {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         if (predicate.test(player.mainHandItem)) return player.inventory.selectedSlot
         for (slot in 0..8) {
            if (predicate.test(player.inventory.getItem(slot))) return slot
         }
         return -1
      }

      @JvmStatic
      fun countItems(predicate: Predicate<ItemStack>): Int {
         var count = 0
         val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
         for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (predicate.test(stack)) count += stack.count
         }
         for (stack in ClientAccess.collectiveMinecraft.player!!.inventoryMenu.craftSlots.items) {
            if (predicate.test(stack)) count += stack.count
         }
         return count
      }

      @JvmStatic
      fun getHandForItem(item: Item): InteractionHand? {
         if (ClientAccess.collectiveMinecraft.player!!.offhandItem.item === item) {
            return InteractionHand.OFF_HAND
         }

         return if (ClientAccess.collectiveMinecraft.player!!.mainHandItem.item === item) InteractionHand.MAIN_HAND else null
      }

      @JvmStatic
      fun getLowestArmorDurabilityPercent(player: Player): Float {
         var lowest = 100.0f
         for (stack in EquipmentUtils.getArmorStacks(player)) {
            lowest = Math.min(lowest, getDurabilityPercent(stack))
         }
         return lowest
      }

      @JvmStatic
      fun getDurabilityPercent(stack: ItemStack): Float {
         if (!stack.isDamageableItem) return 100.0f
         val remaining = (stack.maxDamage.toFloat() - stack.damageValue.toFloat()) / stack.maxDamage
         return (100 - ((1.0f - remaining) * 100.0f).toInt()).toFloat()
      }

      @JvmStatic
      fun toMenuSlot(slot: Int): Int {
         return if (slot >= 0 && slot < 9) slot + 36 else slot
      }

      @JvmStatic
      fun isInventoryFull(): Boolean {
         for (slot in 0..35) {
            if (ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot).isEmpty) return false
         }
         return true
      }

      @JvmStatic
      fun isHotbarFull(): Boolean {
         for (slot in 0..8) {
            if (ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot).isEmpty) return false
         }
         return true
      }

      @JvmStatic
      fun selectHotbarSlotForPlacement(slot: Int) {
         selectHotbarSlot(slot)
      }

      @JvmStatic
      fun canEquip(slot: EquipmentSlot?, stack: ItemStack): Boolean {
         if (slot == null) {
            return false
         }
         val equippable = stack.get(DataComponents.EQUIPPABLE)
         if (equippable is Equippable) {
            return equippable.slot() === slot
         } else {
            return stack.item === Items.ELYTRA && slot === EquipmentSlot.CHEST
         }
      }

      @JvmStatic
      fun findBestToolSlot(position: BlockPos, includeInventory: Boolean): Int {
         val state: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(position)
         val selected = getSelectedSlot()
         var bestSlot = -1
         var bestSpeed = -1.0
         for (slot in 0 until (if (includeInventory) 36 else 9)) {
            val stack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(slot)
            if (itemSaver.isToggled() && !ItemSaverModule.isSafeToUse(stack)) continue
            if (ClientAccess.collectiveMinecraft.player!!.isCreative && stack.`is`(ItemTags.SWORDS)) continue
            val speed = stack.getDestroySpeed(state).toDouble()
            if (speed > bestSpeed || speed == bestSpeed && slot == selected) {
               bestSpeed = speed
               bestSlot = slot
            }
         }
         return bestSlot
      }

      @JvmStatic
      fun getCarriedStack(): ItemStack {
         return ClientAccess.collectiveMinecraft.player!!.containerMenu.carried
      }

      @JvmStatic
      fun findSwapSlot(item: Item, includeInventory: Boolean): SwapSlot {
         val predicate = Predicate<ItemStack> { stack -> stack.`is`(item) }
         return findSwapSlot(predicate, includeInventory)
      }

      @JvmStatic
      fun findSwapSlot(predicate: Predicate<ItemStack>, includeInventory: Boolean): SwapSlot {
         if (predicate.test(ClientAccess.collectiveMinecraft.player!!.offhandItem)) {
            return SwapSlot(-2, false, InteractionHand.OFF_HAND)
         }
         val foundSlot = findHotbarSlot(predicate)
         if (foundSlot == -1 && includeInventory) {
            return SwapSlot(findInventorySlot(predicate, true), false, InteractionHand.MAIN_HAND)
         }
         return SwapSlot(foundSlot, true, InteractionHand.MAIN_HAND)
      }

      @JvmStatic
      fun swapTo(slot: SwapSlot) {
         if (!slot.isFound() || slot.isInHand()) return
         if (slot.hotbar) selectHotbarSlot(slot.slot) else swapIntoSelectedSlot(slot.slot)
      }

      @JvmStatic
      fun swapBack(previousSlot: Int, slot: SwapSlot) {
         if (!slot.isFound() || slot.hand === InteractionHand.OFF_HAND) return
         if (slot.hotbar) selectHotbarSlot(previousSlot) else swapIntoSelectedSlot(slot.slot)
      }

      @JvmStatic
      fun runWithSwap(previousSlot: Int, slot: SwapSlot, action: Runnable) {
         if (!slot.isFound()) return
         swapTo(slot)
         try {
            action.run()
         } finally {
            swapBack(previousSlot, slot)
         }
      }

      @JvmStatic
      fun isEnderChestScreen(screen: Screen?): Boolean {
         return screen != null && screen.title == enderChestScreenTitle
      }
   }

   class SwapSlot(
      @get:JvmName("slot") val slot: Int,
      @get:JvmName("hotbar") val hotbar: Boolean,
      @get:JvmName("hand") val hand: InteractionHand
   ) {
      val isFound: Boolean
         @JvmName("isFoundProp") get() = isFound()
      val found: Boolean
         @JvmName("foundProp") get() = isFound()

      fun isFound(): Boolean = this.slot != -1
      fun found(): Boolean = isFound()

      val stack: ItemStack
         @JvmName("getStackProp") get() = getStack()

      fun getStack(): ItemStack {
         if (this.hand === InteractionHand.OFF_HAND) {
            return ClientAccess.collectiveMinecraft.player!!.offhandItem
         } else {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val inventory: Inventory = player.inventory
            val inventorySlot = this.slot
            return inventory.getItem(inventorySlot)
         }
      }

      val isInHand: Boolean
         @JvmName("isInHandProp") get() = isInHand()
      val inHand: Boolean
         @JvmName("inHandProp") get() = isInHand()

      fun isInHand(): Boolean = this.hand === InteractionHand.OFF_HAND ||
         (this.slot == InventoryUtils.getSelectedSlot() && this.hotbar)
      fun inHand(): Boolean = isInHand()
   }
}
