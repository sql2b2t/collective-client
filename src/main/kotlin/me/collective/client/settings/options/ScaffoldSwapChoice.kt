package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.settings.NamedEntry

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.player.Inventory

enum class ScaffoldSwapChoice(private val displayName: String) : ClientAccess, NamedEntry {
    NONE("None"),
    NORMAL("Normal") {
        override fun runWithSlot(slot: Int, action: Runnable) {
            InventoryUtils.selectHotbarSlotForPlacement(slot)
            action.run()
        }
    },
    SILENT("Silent") {
        override fun runWithSlot(slot: Int, action: Runnable) {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val inventory: Inventory = player.getInventory()
            val originalSlot = inventory.selected

            InventoryUtils.selectHotbarSlotForPlacement(slot)
            action.run()

            InventoryUtils.selectHotbarSlotForPlacement(originalSlot)
        }
    };

    override fun getName(): String = displayName

    open fun runWithSlot(slot: Int, action: Runnable) {
        val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
        val inventory: Inventory = player.getInventory()
        if (inventory.selected == slot) {
            action.run()
        }
    }
}
