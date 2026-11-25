package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.modules.exploit.IllegalDisconnectModule
import net.minecraft.network.chat.Component

class DisconnectCommand : Command("disconnect") {

    init {
        setAliases("dc")
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val illegal = literal<Any?>("illegal")
        val disconnectIllegally = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            if (!isWorldUnavailable) {
                IllegalDisconnectModule.sendInvalidChatPacket()
            }
            1
        }
        val illegalCommand = illegal.executes(disconnectIllegally)
        val commandWithIllegal = root.then(illegalCommand) as LiteralArgumentBuilder<Any?>

        val disconnect = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            if (!isWorldUnavailable) {
                val listener = ClientAccess.collectiveMinecraft.player?.connection
                val connection = listener?.connection
                val reason = Component.nullToEmpty("Disconnected")
                connection?.disconnect(reason)
            }
            1
        }
        commandWithIllegal.executes(disconnect)
    }
}
