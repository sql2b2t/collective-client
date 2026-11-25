package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class PrintCommand : Command("print") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val messageArgument = (argument("message", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val chat = ClientAccess.collectiveMinecraft.gui.chat
            val message = context.getArgument("message", String::class.java)
            val component = Component.nullToEmpty(message)
            val random = Math.random()
            val signatureId = (random * 10000.0).toInt()
            val signature = ChatUtils.createSignature(signatureId)
            val tag = ChatUtils.MESSAGE_TAG
            chat.addMessage(component, signature, tag)
            1
        }
        root.then(messageArgument)

        root.executes {
            val component = Component.literal("Please enter a message to print.")
            val signature = ChatUtils.createSignature(-1)
            val priority = ChatUtils.MessagePriority.LOW
            ChatUtils.printChat(component, signature, priority)
            1
        }
    }
}
