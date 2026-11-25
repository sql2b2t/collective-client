package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.world.item.Item

class AlternativeSwapStrategy : ActionSwapStrategy {
   override fun findSlots(items: Array<Item>): SwapSlots {
      val hotbarSlot = InventoryUtils.findHotbarSlot(*items)
      var sourceSlot = InventoryUtils.toMenuSlot(hotbarSlot)
      if (sourceSlot == -1) {
         sourceSlot = InventoryUtils.findInventorySlot(*items)
      }

      return SwapSlots(sourceSlot, ClientAccess.collectiveMinecraft.player!!.inventory.selected)
   }

   override fun apply(slots: SwapSlots) {
      InventoryUtils.swapIntoSelectedSlot(slots.sourceSlot)
   }

   override fun undo(slots: SwapSlots) {
      InventoryUtils.swapIntoSelectedSlot(slots.sourceSlot)
   }
}
