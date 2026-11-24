package me.collective.client.utils.inventory

import me.collective.client.ClientAccess
import me.collective.client.settings.options.InventorySorterScopeChoice

import net.minecraft.world.item.ItemStack

interface InventorySortStrategy {
    fun getSourceSlots(scope: InventorySorterScopeChoice): Array<Int>

    companion object {
        @JvmStatic
        fun getInventoryStack(inventorySlot: Int): ItemStack {
            val player = ClientAccess.collectiveMinecraft.player ?: return ItemStack.EMPTY
            return player.inventory.getItem(inventorySlot)
        }
    }
}
