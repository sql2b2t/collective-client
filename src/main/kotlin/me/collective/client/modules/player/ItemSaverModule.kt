package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class ItemSaverModule : Module("ItemSaver", "Prevents your tools from breaking.", ModuleCategory.PLAYER) {

   companion object {

      @JvmStatic
      fun isSafeToUse(stack: ItemStack): Boolean {
         if (stack.isEmpty || !stack.isDamageableItem || stack.has(DataComponents.EQUIPPABLE) || stack.`is`(Items.ELYTRA)) {
            return true
         }
         return InventoryUtils.getDurabilityPercent(stack) > 5.0f
      }
   }

   @EventHandler
   private fun onMultiPlayerGameModeStartDestroyBlockEvent(event: MultiPlayerGameModeStartDestroyBlockEvent) {
      val inventory: Inventory = ClientAccess.collectiveMinecraft.player!!.inventory
      val selectedSlot: Int = inventory.selectedSlot
      if (!isSafeToUse(inventory.getItem(selectedSlot))) {
         // Ищем любой безопасный слот хотбара: старая логика «±1 слот»
         // осциллировала между 7 и 8 и не проверяла новый слот.
         var safeSlot = -1
         for (candidate in 0..8) {
            if (candidate != selectedSlot && isSafeToUse(inventory.getItem(candidate))) {
               safeSlot = candidate
               break
            }
         }
         if (safeSlot != -1) {
            InventoryUtils.selectHotbarSlotForPlacement(safeSlot)
         }
         event.cancel()
      }
   }

}
