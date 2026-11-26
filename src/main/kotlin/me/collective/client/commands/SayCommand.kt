package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.utils.chat.ChatUtils

class SayCommand : Command("say") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val messageArgument = (argument("message", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val message = context.getArgument("message", String::class.java)
            ChatUtils.sendChatOrCommand(message)
            1
        }
        root.then(messageArgument)
    }
}
