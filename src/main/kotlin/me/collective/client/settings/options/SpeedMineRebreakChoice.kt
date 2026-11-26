package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class SpeedMineRebreakChoice(private val displayName: String) : NamedEntry {
    NONE("None"),
    FAST("Fast"),
    INSTANT("Instant");

    fun isNone(): Boolean {
        return this == NONE
    }

    fun isInstant(): Boolean {
        return this == INSTANT
    }

    fun isFast(): Boolean {
        return this == FAST
    }

    override fun getName(): String = displayName
}
