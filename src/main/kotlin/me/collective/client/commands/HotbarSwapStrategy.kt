package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.world.item.Item

class HotbarSwapStrategy : ActionSwapStrategy {
   override fun findSlots(items: Array<Item>): SwapSlots {
      val sourceSlot = InventoryUtils.findHotbarSlot(*items)
      return SwapSlots(sourceSlot, ClientAccess.collectiveMinecraft.player!!.inventory.selectedSlot)
   }

   override fun apply(slots: SwapSlots) {
      InventoryUtils.selectHotbarSlot(slots.sourceSlot)
   }

   override fun undo(slots: SwapSlots) {
      InventoryUtils.selectHotbarSlot(slots.targetSlot)
   }
}
