package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class BlockerModeChoice(private val displayName: String) : NamedEntry {
    DAMAGE("Damage"),
    DESTROY("Destroy"),
    INSTANT("Instant");

    override fun getName(): String = displayName
}
