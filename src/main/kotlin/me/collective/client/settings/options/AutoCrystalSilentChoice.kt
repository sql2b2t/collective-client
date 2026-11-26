package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AutoCrystalSilentChoice(private val displayName: String) : NamedEntry {
    NORMAL("Normal"),
    ALTERNATIVE("Alternative");

    override fun getName(): String = displayName
}
