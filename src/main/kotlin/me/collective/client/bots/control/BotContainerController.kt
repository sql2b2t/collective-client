package me.collective.client.bots.control

import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import me.collective.client.bots.BotClient
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.*
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.ArrayList

class BotContainerController(val bot: BotClient) {
    var menu: AbstractContainerMenu? = null
        private set
    var title: Component = Component.empty()
        private set

    val isMenuOpen: Boolean get() = menu != null

    val containerId: Int get() = menu?.containerId ?: 0

    fun tick() {}

    fun handleOpenScreen(packet: ClientboundOpenScreenPacket) {
        val player = bot.worldState.botLocalPlayer ?: return
        val newMenu = packet.type.create(packet.containerId, player.inventory)
        if (newMenu != null) {
            menu = newMenu
            title = packet.title
            player.containerMenu = newMenu
        }
    }

    fun handleContainerClose(containerId: Int) {
        if (menu != null && menu?.containerId == containerId) {
            resetMenu()
        }
    }

    fun handleContainerSetContent(packet: ClientboundContainerSetContentPacket) {
        val currentMenu = menu
        if (currentMenu != null && currentMenu.containerId == packet.containerId) {
            currentMenu.initializeContents(packet.stateId, packet.items, packet.carriedItem)
        }
    }

    fun handleContainerSetSlot(packet: ClientboundContainerSetSlotPacket) {
        val currentMenu = menu
        if (currentMenu != null && currentMenu.containerId == packet.containerId && currentMenu.isValidSlotIndex(packet.slot)) {
            currentMenu.setItem(packet.slot, packet.stateId, packet.item)
        }
    }

    fun closeMenu() {
        val currentMenu = menu ?: return
        bot.sendPacket(ServerboundContainerClosePacket(currentMenu.containerId))
        resetMenu()
    }

    fun clickButton(buttonId: Int) {
        val currentMenu = menu ?: return
        bot.sendPacket(ServerboundContainerButtonClickPacket(currentMenu.containerId, buttonId))
    }

    fun clickSlot(slotIndex: Int, buttonIndex: Int, clickType: ClickType) {
        val player = bot.worldState.botLocalPlayer ?: return
        val activeMenu = menu ?: player.inventoryMenu
        val slots: NonNullList<Slot> = activeMenu.slots
        val oldItems = ArrayList<ItemStack>(slots.size)

        for (slot in slots) {
            oldItems.add(slot.item.copy())
        }

        val carriedCopy = activeMenu.carried.copy()
        activeMenu.clicked(slotIndex, buttonIndex, clickType, player)

        val packet: ServerboundContainerClickPacket
        try {
            packet = ServerboundContainerClickPacket(
                activeMenu.containerId,
                activeMenu.stateId,
                slotIndex,
                buttonIndex,
                clickType,
                activeMenu.carried.copy(),
                computeChangedSlots(slots, oldItems)
            )
        } catch (e: RuntimeException) {
            for (i in slots.indices) {
                slots[i].set(oldItems[i])
            }
            activeMenu.carried = carriedCopy
            throw e
        }

        bot.sendPacket(packet)
    }

    private fun computeChangedSlots(slots: NonNullList<Slot>, oldItems: List<ItemStack>): Int2ObjectMap<ItemStack> {
        val changed: Int2ObjectMap<ItemStack> = Int2ObjectOpenHashMap()
        for (i in slots.indices) {
            val item = slots[i].item
            if (!ItemStack.matches(oldItems[i], item)) {
                changed.put(i, item.copy())
            }
        }
        return changed
    }

    fun clear() {
        menu = null
        title = Component.empty()
    }

    private fun resetMenu() {
        val player = bot.worldState.botLocalPlayer
        if (player != null) {
            player.containerMenu = player.inventoryMenu
        }
        menu = null
        title = Component.empty()
    }
}
