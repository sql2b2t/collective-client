package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AutoCrystalInstantChoice(private val displayName: String) : NamedEntry {
    ALWAYS("Always"),
    STRICT("Strict"),
    NONE("None");

    fun isEnabled(): Boolean = this != NONE

    override fun getName(): String = displayName
}
