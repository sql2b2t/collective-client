package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.modules.hud.WelcomerModule
import me.collective.client.settings.NamedEntry

import net.minecraft.client.Minecraft
import net.minecraft.client.User
import net.minecraft.network.chat.Component

enum class WelcomerMode(private val displayName: String) : ClientAccess, NamedEntry {
    NAME("Name") {
        override fun createText(ignoredWelcomer: WelcomerModule): Component? {
            val minecraft: Minecraft = ClientAccess.collectiveMinecraft
            val user: User = minecraft.getUser()
            val username = user.getName()

            val greeting = "Hello " + username + " :')"
            return Component.literal(greeting)
        }
    },

    CUSTOM("Custom") {
        override fun createText(welcomer: WelcomerModule): Component? {
            val textSetting = welcomer.textSetting
            var text = textSetting.getValue()

            val minecraft: Minecraft = ClientAccess.collectiveMinecraft
            val user: User = minecraft.getUser()
            text = text.replace("{name}", user.getName())
            return Component.literal(text)
        }
    };

    override fun getName(): String = displayName

    open fun createText(ignoredWelcomer: WelcomerModule): Component? {
        return null
    }
}
