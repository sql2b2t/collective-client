package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.inventory.ArmorSlot
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

open class ArmorSwapAction internal constructor(
    private val armorKind: ArmorSlot,
    private val inventorySlot: Int
) : ClientAccess {

    private var quickMove: Boolean = false
    private var alternative: Boolean = false

    fun setQuickMove(quickMove: Boolean) {
        this.quickMove = quickMove
    }

    fun setAlternative(alternative: Boolean) {
        this.alternative = alternative
    }

    open fun execute() {
        if (this.quickMove) {
            val armorContainerSlot = 8 - this.armorKind.getArmorIndex()
            InventoryUtils.clickSlot(armorContainerSlot, 0, ClickType.QUICK_MOVE)
            return
        }

        val firstModeBlocked = InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 4)
        val secondModeBlocked = InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 3)
        val sourceContainerSlot = InventoryUtils.toMenuSlot(this.inventorySlot)

        ClientContext.inventoryStateManager.setClickBatching(true)
        if (this.alternative && !firstModeBlocked) {
            var canPlaceCarriedStack = true
            val carriedBeforePlacement = InventoryUtils.getCarriedStack()
            if (!carriedBeforePlacement.isEmpty) {
                val carriedForPlacement = InventoryUtils.getCarriedStack()
                canPlaceCarriedStack = this.returnCarriedStack(carriedForPlacement)
            }
            if (canPlaceCarriedStack) {
                val destinationContainerSlot = 8 - this.armorKind.getArmorIndex()
                InventoryUtils.swapMenuSlots(sourceContainerSlot, destinationContainerSlot)
            }
        } else if (!secondModeBlocked) {
            val destinationContainerSlot = 8 - this.armorKind.getArmorIndex()
            InventoryUtils.swapSlotsWithPickup(sourceContainerSlot, destinationContainerSlot)
        }

        ClientContext.inventoryStateManager.setClickBatching(false)
    }

    private fun returnCarriedStack(carriedStack: ItemStack): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val slots: List<Slot> = player.containerMenu.slots

        val destination = slots.filter { it.index > 8 && AbstractContainerMenu.canItemQuickReplace(it, carriedStack, false) }
            .minByOrNull { it.index } ?: return false

        InventoryUtils.clickSlot(destination.index, 0, ClickType.PICKUP)
        return true
    }
}
