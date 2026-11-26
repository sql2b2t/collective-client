package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class ChestStealerModeChoice(private val displayName: String) : NamedEntry {
    STEAL("Steal"),
    FILL("Fill"),
    DROP("Drop"),
    REFILL("Refill"),
    REKIT("Rekit");

    override fun getName(): String = displayName
}
