package me.collective.client.modules

import me.collective.client.settings.NamedEntry

enum class ModuleCategory(private val displayName: String) : NamedEntry {
    COMBAT("Combat"),
    MISC("Misc"),
    RENDER("Render"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    EXPLOIT("Exploit"),
    CLIENT("Client"),
    HUD("HUD");

    override fun getName(): String = displayName
}
