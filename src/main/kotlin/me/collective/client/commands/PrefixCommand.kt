package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.managers.CommandRegistry
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class PrefixCommand : Command("prefix") {

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val prefixArgument = (argument("prefix", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>).executes { context ->
            val prefix = context.getArgument("prefix", String::class.java)
            CommandRegistry.setPrefix(prefix)
            val currentPrefix = CommandRegistry.getPrefix()
            val completeMessage = Component.literal("Prefix set to ").append(formatText(currentPrefix))
            ChatUtils.printChat(completeMessage, -1)
            1
        }
        root.then(prefixArgument)

        root.executes {
            val currentPrefix = CommandRegistry.getPrefix()
            val completeMessage = Component.literal("The current prefix is ").append(formatText(currentPrefix))
            ChatUtils.printChat(completeMessage, -1)
            1
        }
    }
}
