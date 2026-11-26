package me.collective.client.commands

import me.collective.client.ClientAccess
import net.minecraft.world.item.Item

interface ActionSwapStrategy : ClientAccess {
   fun findSlots(items: Array<Item>): SwapSlots

   fun apply(slots: SwapSlots)

   fun undo(slots: SwapSlots)
}
