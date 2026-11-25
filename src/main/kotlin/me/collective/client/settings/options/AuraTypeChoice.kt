package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AuraTypeChoice(private val displayName: String) : NamedEntry {
    BOX("Box"),
    CIRCLE("Circle");

    override fun getName(): String = displayName
}
