package me.collective.client.bots.control

import me.collective.client.bots.BotClient
import me.collective.client.bots.world.BotPlayerState
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import java.util.Arrays

class BotInventorySnapshot {
    companion object {
        const val SLOTS_COUNT: Int = 36

        private fun mapSlotIndex(slot: Int): Int {
            return when {
                slot in 9..35 -> slot
                slot in 36..44 -> slot - 36
                else -> -1
            }
        }
    }

    private val items: Array<ItemStack> = Array(SLOTS_COUNT) { ItemStack.EMPTY }

    fun update(bot: BotClient) {
        val player = bot.worldState.botLocalPlayer
        if (player != null) {
            updateFromInventory(player.inventory)
        } else {
            updateFromState(bot.playerState)
        }
    }

    private fun updateFromInventory(inv: Inventory) {
        for (i in 0 until SLOTS_COUNT) {
            items[i] = inv.getItem(i)
        }
    }

    private fun updateFromState(state: BotPlayerState) {
        Arrays.fill(items, ItemStack.EMPTY)
        val content = state.containerContentPacket
        if (content != null) {
            val list = content.items
            for (i in list.indices) {
                val mapped = mapSlotIndex(i)
                if (mapped in 0 until SLOTS_COUNT) {
                    items[mapped] = list[i]
                }
            }
        }

        for (slotPacket in state.containerSlotPackets.values) {
            val mapped = mapSlotIndex(slotPacket.slot)
            if (mapped in 0 until SLOTS_COUNT) {
                items[mapped] = slotPacket.item
            }
        }

        for (invPacket in state.playerInventoryPackets.values) {
            if (invPacket.slot in 0 until SLOTS_COUNT) {
                items[invPacket.slot] = invPacket.contents
            }
        }
    }

    fun getItem(slot: Int): ItemStack {
        return if (slot in 0 until SLOTS_COUNT) items[slot] else ItemStack.EMPTY
    }
}
