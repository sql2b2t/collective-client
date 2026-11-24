package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class ChestStealerLookForChoice(private val displayName: String) : NamedEntry {
    NAME("Name"),
    ITEM("Item");

    override fun getName(): String = displayName
}
