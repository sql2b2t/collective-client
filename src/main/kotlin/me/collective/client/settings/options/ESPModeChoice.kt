package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class ESPModeChoice(private val displayName: String) : NamedEntry {
    PLAIN("Plain"),
    TEXT("Text"),
    BOTH("Both");

    override fun getName(): String = displayName
}
