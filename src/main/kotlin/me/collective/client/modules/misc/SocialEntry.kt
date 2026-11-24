package me.collective.client.modules.misc

import me.collective.client.settings.NamedEntry
import me.collective.client.settings.options.SocialRole

data class SocialEntry(
    @get:JvmName("socialName")
    val name: String,
    val role: SocialRole
) : NamedEntry {
    override fun getName(): String = this.name

    fun role(): SocialRole = this.role
}
