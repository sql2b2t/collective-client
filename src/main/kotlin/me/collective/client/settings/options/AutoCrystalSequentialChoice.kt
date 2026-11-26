package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AutoCrystalSequentialChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    STRICT("Strict"),
    STRONG("Strong");

    val isEnabled: Boolean get() = this != NONE

    override fun getName(): String = displayName
}

fun AutoCrystalSequentialChoice.isEnabled(): Boolean = this.isEnabled
