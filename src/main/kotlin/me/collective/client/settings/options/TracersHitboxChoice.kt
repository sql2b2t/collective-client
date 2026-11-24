package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class TracersHitboxChoice(
    private val displayName: String,
    val heightFraction: Float
) : NamedEntry {
    HEAD("Head", 0.95F),
    BODY("Body", 0.5F),
    LEGS("Legs", 0.0F);

    override fun getName(): String = displayName
}
