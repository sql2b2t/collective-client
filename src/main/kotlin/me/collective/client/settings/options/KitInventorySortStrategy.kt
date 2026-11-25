package me.collective.client.settings.options

import me.collective.client.ClientContext
import me.collective.client.managers.KitRegistry
import me.collective.client.utils.inventory.InventorySortStrategy
import me.collective.client.utils.inventory.Kit
import java.util.Collections
import java.util.stream.IntStream
import net.minecraft.world.item.Item

object KitInventorySortStrategy : InventorySortStrategy {
    @Suppress("UNCHECKED_CAST")
    override fun getSourceSlots(scope: InventorySorterScopeChoice): Array<Int> {
        val kitManager = ClientContext.kitRegistry
        val kit = KitRegistry.getCurrentKit(kitManager)
        val startInclusive = scope.startInclusive
        val endExclusive = scope.endExclusive
        val inventorySlots: List<Int> = IntStream.range(startInclusive, endExclusive).boxed().toList()
        val stacks = ArrayList(inventorySlots.map { slot -> InventorySortStrategy.getInventoryStack(slot) })
        val sourceSlots = ArrayList(inventorySlots)
        val claimedSlots = BooleanArray(stacks.size)

        val kitEntries = ArrayList<Map.Entry<Int, Kit.ItemDescriptor>>(
            kit.itemsBySlot.entries as Collection<Map.Entry<Int, Kit.ItemDescriptor>>
        )
        kitEntries.sortBy { it.key }

        kitTraversal@ for (entry in kitEntries) {
            val kitSlot = entry.key
            if (kitSlot >= scope.startInclusive) {
                if (kitSlot < scope.endExclusive) {
                    val targetIndex = kitSlot - scope.startInclusive
                    val kitItem = entry.value
                    val desiredItem = Item.byId(kitItem.itemId())
                    val targetStack = stacks[targetIndex]
                    if (!targetStack.isEmpty && targetStack.item === desiredItem) {
                        claimedSlots[targetIndex] = true
                        continue@kitTraversal
                    }

                    for (candidateIndex in stacks.indices) {
                        if (!claimedSlots[candidateIndex] && candidateIndex != targetIndex) {
                            val candidateStack = stacks[candidateIndex]
                            if (!candidateStack.isEmpty && candidateStack.item === desiredItem) {
                                Collections.swap(sourceSlots, targetIndex, candidateIndex)
                                Collections.swap(stacks, targetIndex, candidateIndex)
                                claimedSlots[targetIndex] = true
                                continue@kitTraversal
                            }
                        }
                    }
                }
            }
        }

        return sourceSlots.toTypedArray()
    }
}
