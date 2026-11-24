package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AutoArmorBlastChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    LEGS("Legs"),
    SMART("1.21"),
    ALL("All");

    override fun getName(): String = displayName
}
