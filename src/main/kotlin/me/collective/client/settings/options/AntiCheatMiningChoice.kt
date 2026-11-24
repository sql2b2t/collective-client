package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AntiCheatMiningChoice(private val displayName: String) : NamedEntry {
    VANILLA("Vanilla"),
    GRIM("Grim"),
    GRIM_STRICT("GrimStrict");

    override fun getName(): String = displayName
}
