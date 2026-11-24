package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class SpeedMineAutoSwapChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    NORMAL("Normal"),
    SILENT("Silent");

    override fun getName(): String = displayName
}
