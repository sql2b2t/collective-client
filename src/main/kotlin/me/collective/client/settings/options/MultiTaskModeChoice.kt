package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class MultiTaskModeChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    STRICT("Strict"),
    OFFHAND("Offhand"),
    ALL("Always");

    override fun getName(): String = displayName
}
