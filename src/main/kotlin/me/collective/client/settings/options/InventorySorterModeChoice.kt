package me.collective.client.settings.options

import me.collective.client.utils.inventory.InventorySortStrategy
import me.collective.client.settings.NamedEntry

enum class InventorySorterModeChoice(
    private val displayName: String,
    val strategy: InventorySortStrategy
) : NamedEntry {
    TYPE("Type", ComparatorInventorySortStrategy.BY_TYPE),
    NAME("Name", ComparatorInventorySortStrategy.BY_NAME),
    KIT("Kit", KitInventorySortStrategy);

    override fun getName(): String = displayName
}
