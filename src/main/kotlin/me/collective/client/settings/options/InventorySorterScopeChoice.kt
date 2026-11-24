package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class InventorySorterScopeChoice(
    private val displayName: String,
    val startInclusive: Int,
    val endExclusive: Int
) : NamedEntry {
    INVENTORY("Inventory", 9, 36),
    HOTBAR("Hotbar", 0, 9),
    BOTH("Both", 0, 36);

    override fun getName(): String = displayName
}
