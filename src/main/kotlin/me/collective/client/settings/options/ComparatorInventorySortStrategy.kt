package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.utils.inventory.InventorySortStrategy
import java.util.Comparator
import java.util.function.Function
import java.util.stream.IntStream
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.ItemStack

class ComparatorInventorySortStrategy(private val primaryComparator: Comparator<Int>) : InventorySortStrategy {
    private fun getSlotComparator(): Comparator<Int> {
        return primaryComparator
            .thenComparingInt { slotValue: Int -> InventorySortStrategy.getInventoryStack(slotValue).damageValue }
            .thenComparingInt { slotValue: Int -> -InventorySortStrategy.getInventoryStack(slotValue).count }
    }

    override fun getSourceSlots(scope: InventorySorterScopeChoice): Array<Int> {
        val startInclusive = scope.startInclusive
        val endExclusive = scope.endExclusive
        val occupiedSlots = IntStream.range(startInclusive, endExclusive).filter { slot: Int ->
            !InventorySortStrategy.getInventoryStack(slot).isEmpty
        }.boxed()
        return occupiedSlots.sorted(getSlotComparator()).toList().toTypedArray()
    }

    companion object {
        @JvmField
        val BY_TYPE: InventorySortStrategy = comparingStackProperty(Function { stack: ItemStack -> getCreativeTabIndex(stack) })

        @JvmField
        val BY_NAME: InventorySortStrategy = comparingStackProperty(Function { stack: ItemStack -> stack.hoverName.string })

        private fun getCreativeTabIndex(stack: ItemStack): Int {
            val tabs = CreativeModeTabs.allTabs()
            val firstTab = tabs.first()
            if (!firstTab.hasAnyItems()) {
                val connection = ClientAccess.collectiveMinecraft.player!!.connection
                val enabledFeatures = connection.enabledFeatures()
                val level = ClientAccess.collectiveMinecraft.level!!
                val registries = level.registryAccess()
                CreativeModeTabs.tryRebuildTabContents(enabledFeatures, true, registries)
            }

            for (tabIndex in tabs.indices) {
                val tab = tabs[tabIndex]
                val item = stack.item
                val defaultStack = item.defaultInstance
                if (tab.contains(defaultStack)) {
                    return tabIndex
                }
            }

            return tabs.size
        }

        private fun comparingStackProperty(property: Function<ItemStack, Any?>): InventorySortStrategy {
            val comparator: Comparator<Int> = Comparator.comparing<Int, Comparable<Any>> { slotValue: Int ->
                @Suppress("UNCHECKED_CAST")
                property.apply(InventorySortStrategy.getInventoryStack(slotValue)) as Comparable<Any>
            }
            return ComparatorInventorySortStrategy(comparator)
        }
    }
}
