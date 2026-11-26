package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AntiCheatPlacementsChoice(private val displayName: String) : NamedEntry {
    VANILLA("Vanilla"),
    PAPER("Paper");

    override fun getName(): String = displayName
}
