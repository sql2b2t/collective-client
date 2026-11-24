package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class TracersModeChoice(private val displayName: String) : NamedEntry {
    LINES("Lines"),
    MARKER("Marker");

    override fun getName(): String = displayName
}
