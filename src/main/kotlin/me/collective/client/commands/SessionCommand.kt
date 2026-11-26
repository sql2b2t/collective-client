package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import me.collective.mixin.UserAccessor
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.network.chat.Component
import java.util.UUID

class SessionCommand : Command("session") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val nameArgument = (argument("name", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val name = context.getArgument("name", String::class.java)
            setSessionName(name)
            1
        }
        root.then(nameArgument)

        root.executes {
            val randomName = createRandomName()
            setSessionName(randomName)
            1
        }
    }

    private fun createRandomName(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16)
    }

    private fun setSessionName(name: String) {
        if (name.isEmpty() || name.length > 16) {
            ChatUtils.printChat(Component.literal("Session names must contain 1 to 16 characters."), -1)
            return
        }
        val client = ClientAccess.collectiveMinecraft
        val listener = client.connection
        val server = listener?.serverData
        val user = client.user as UserAccessor
        user.setUsername(name)
        user.setUuid(UUID.randomUUID())
        ChatUtils.printChat(Component.literal("Session's name has been set to $name."), -1)
        if (server != null) {
            listener.connection.disconnect(Component.empty())
            ConnectScreen.startConnecting(
                JoinMultiplayerScreen(TitleScreen()),
                client,
                ServerAddress.parseString(server.ip),
                server,
                true,
                null
            )
        }
    }
}
