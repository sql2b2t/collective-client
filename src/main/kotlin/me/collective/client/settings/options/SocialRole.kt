package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class SocialRole(
    private val displayName: String,
    val pluralName: String
) : NamedEntry {
    FRIEND("friend", "friends"),
    ENEMY("enemy", "enemies");

    override fun getName(): String = displayName
}
