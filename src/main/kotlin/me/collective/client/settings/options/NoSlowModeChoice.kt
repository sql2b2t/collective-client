package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class NoSlowModeChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    NCP("NCP"),
    GRIMOLD("GrimOld"),
    GRIMV3("Slow"),
    _2B2T("2b2t"),
    CONSTANTIAM("Constantiam");

    override fun getName(): String = displayName
}
