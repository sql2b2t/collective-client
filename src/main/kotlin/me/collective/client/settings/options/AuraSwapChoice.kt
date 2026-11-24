package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AuraSwapChoice(private val displayName: String) : NamedEntry {
    NORMAL("Normal"),
    SWAPBACK("SwapBack"),
    SILENT("Silent");

    override fun getName(): String = displayName
}
