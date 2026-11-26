package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AntiCheatStrictDirectionChoice(private val displayName: String) : NamedEntry {
    OFF("Off"),
    NCP("NCP"),
    GRIM("Grim");

    override fun getName(): String = displayName
}
