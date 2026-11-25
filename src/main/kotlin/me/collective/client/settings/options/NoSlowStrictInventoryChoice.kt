package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class NoSlowStrictInventoryChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    NCP("NCP"),
    GRIM_STRICT("GrimStrict");

    override fun getName(): String = displayName
}
