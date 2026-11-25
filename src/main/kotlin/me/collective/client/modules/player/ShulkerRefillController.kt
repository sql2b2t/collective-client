package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.KitRegistry
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.inventory.Kit
import me.collective.client.utils.inventory.InventoryUtils

import java.util.ArrayList
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents

class ShulkerRefillController(module: ChestStealerModule) : ChestStealerController(module) {
   private var savedSelectedSlot = -1
   private var pendingClose = false

   private fun findBestShulker(menu: ChestMenu, profile: Kit): RefillSelection {
      var bestSlot = -1
      var bestScore = 0.001f
      for (slot in 0 until 27) {
         val stack = menu.getSlot(slot).item
         if (!stack.`is`(ItemTags.SHULKER_BOXES)) continue
         val score = this.scoreContents(stack, profile)
         if (score > bestScore) {
            bestScore = score
            bestSlot = slot
         }
      }
      return RefillSelection(bestSlot, bestScore)
   }

   private fun scoreContents(shulkerStack: ItemStack, profile: Kit): Float {
      val contents = shulkerStack.get(DataComponents.CONTAINER) as ItemContainerContents?
      if (contents == null) {
         return 0f
      }

      val contentStacks = ArrayList<ItemStack>()
      for (contentStack in contents.nonEmptyItems()) {
         contentStacks.add(contentStack)
      }

      var score = 0.0f

      val profileEntries = profile.itemsBySlot.entries.iterator()

      while (profileEntries.hasNext()) {
         val profileEntry = profileEntries.next()
         val desiredStack = profileEntry.value
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val inventory: Inventory = player.inventory
         val inventorySlot = profileEntry.key
         val inventoryStack = inventory.getItem(inventorySlot)
         val missingFraction = 1.0f - inventoryStack.count.toFloat() / this.getStackCapacity(inventoryStack)
         if (desiredStack.matches(inventoryStack) && missingFraction == 0.0f) {
            continue
         }
         if (inventoryStack.`is`(ItemTags.SWORDS)) {
            continue
         }
         var bestFraction = 0.0f
         for (candidateStack in contentStacks) {
            val fraction = candidateStack.count.toFloat() / this.getStackCapacity(candidateStack)
            if (desiredStack.matches(candidateStack) && fraction > bestFraction) {
               bestFraction = fraction
            }
         }
         if (bestFraction > 0.0f) {
            score += missingFraction * bestFraction
         }
      }

      return score
   }

   private fun getStackCapacity(stack: ItemStack): Int {
      return if (stack.isEmpty) 64 else stack.maxStackSize
   }

   private fun findSwordOrEmptySlot(menu: ChestMenu): Int {
      for (slot in 54..62) {
         if (menu.getSlot(slot).item.`is`(ItemTags.SWORDS)) return slot
      }
      for (slot in 54..62) {
         if (menu.getSlot(slot).item.isEmpty) return slot
      }
      return 54
   }

   private fun restoreSelectedSlot() {
      if (this.savedSelectedSlot != -1) {
         val selectedSlot = this.savedSelectedSlot

         InventoryUtils.selectHotbarSlot(selectedSlot)
         this.savedSelectedSlot = -1
      }
   }

   fun tick() {
      if (this.pendingClose) {
         ClientAccess.collectiveMinecraft.player!!.closeContainer()
         this.pendingClose = false
         return
      }
      if (!InventoryUtils.isEnderChestScreen(ClientAccess.collectiveMinecraft.screen)) {
         this.restoreSelectedSlot()
         return
      }
      val menu = ClientAccess.collectiveMinecraft.player!!.containerMenu
      if (!this.module.hasScreenOpenTimedOut() ||
         menu !is ChestMenu || menu.rowCount != 3
      ) return
      if (InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 1)) return
      val profile = KitRegistry.getCurrentKit(ClientContext.kitRegistry)
      if (profile == null) return
      val selection = this.findBestShulker(menu, profile)
      if (selection.slotIndex == -1) return
      for (slot in 54..62) {
         val stack = menu.getSlot(slot).item
         if (stack.`is`(ItemTags.SHULKER_BOXES) && this.scoreContents(stack, profile) >= selection.score) return
      }
      val hotbarSlot = this.findSwordOrEmptySlot(menu) - 54
      InventoryUtils.swapWithHotbarSlot(selection.slotIndex, hotbarSlot)
      this.module.openController.requestOpen()
      this.savedSelectedSlot = hotbarSlot
      if (this.module.shouldCloseAutomatically()) this.pendingClose = true
   }

   data class RefillSelection(val slotIndex: Int, val score: Float) {
   }
}
