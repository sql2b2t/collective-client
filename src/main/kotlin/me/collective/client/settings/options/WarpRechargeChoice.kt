package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class WarpRechargeChoice(private val displayName: String) : NamedEntry {
    HOLD("Hold"),
    INSTANT("Instant"),
    NONE("None");

    override fun getName(): String = displayName
}
