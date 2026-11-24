package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AntiCheatRotationsChoice(private val displayName: String) : NamedEntry {
    MOTION("Motion"),
    SILENT("Silent");

    override fun getName(): String = displayName
}
