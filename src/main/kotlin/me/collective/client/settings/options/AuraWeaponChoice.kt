package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AuraWeaponChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    REQUIRE("Require"),
    SWAP("Swap");

    override fun getName(): String = displayName
}
