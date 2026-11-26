package me.collective.client.commands

import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Item
import net.minecraft.world.item.equipment.Equippable

class ArmorSwapStrategy : ActionSwapStrategy {
   override fun findSlots(items: Array<Item>): SwapSlots {
      var targetSlot = 6

      for (item in items) {
         if (!ActionCommand.canEquip(item)) {
            return SwapSlots.INVALID
         }

         val components = item.components()
         val component = components.get(DataComponents.EQUIPPABLE)
         if (component is Equippable) {
            val equipmentSlot = component.slot()
            targetSlot = 8 - equipmentSlot.index
         }
      }

      val sourceSlot = InventoryUtils.findInventorySlot(*items)
      return SwapSlots(sourceSlot, targetSlot)
   }

   override fun apply(slots: SwapSlots) {
      InventoryUtils.swapSlotsWithPickup(slots.sourceSlot, slots.targetSlot)
   }

   override fun undo(slots: SwapSlots) {
      InventoryUtils.swapSlotsWithPickup(slots.targetSlot, slots.sourceSlot)
   }
}
