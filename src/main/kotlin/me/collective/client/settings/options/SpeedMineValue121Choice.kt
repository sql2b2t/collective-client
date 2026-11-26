package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class SpeedMineValue121Choice(private val displayName: String) : NamedEntry {
    OFF("Off"),
    TICK("Tick"),
    SLOW("Slow");

    override fun getName(): String = displayName
}
