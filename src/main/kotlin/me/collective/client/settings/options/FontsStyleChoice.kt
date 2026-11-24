package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class FontsStyleChoice(private val displayName: String) : NamedEntry {
    PLAIN("Plain"),
    BOLD("Bold"),
    ITALIC("Italic"),
    BOLDITALIC("BoldItalic");

    override fun getName(): String = displayName
}
