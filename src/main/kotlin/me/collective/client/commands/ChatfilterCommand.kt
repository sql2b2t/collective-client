package me.collective.client.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.utils.chat.ChatFilterEntry
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component

class ChatfilterCommand : Command("chatfilter") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val commandBuilder = builder as LiteralArgumentBuilder<Any?>

        var branchBuilder = literal<Any?>("add")
        val idArgument = argument("id", FancyStringArgument.fancyString()) as RequiredArgumentBuilder<Any?, *>
        val filterBuilder = argument("filter", StringArgumentType.greedyString()) as RequiredArgumentBuilder<Any?, *>

        val addCommand = { addContext: com.mojang.brigadier.context.CommandContext<Any?> ->
            val filterId = addContext.getArgument("id", String::class.java)
            val filterText = addContext.getArgument("filter", String::class.java)
            val registry = ClientContext.chatFilterRegistry

            if (filterText.isBlank()) {
                val emptyMessageText = "Chat filter \"%s\" has an empty text and would hide the whole chat.".format(filterId)
                val emptyMessage = Component.nullToEmpty(emptyMessageText)
                val emptySignature = ChatUtils.createSignature(-3469856)
                ChatUtils.printChat(emptyMessage, emptySignature, ChatUtils.MessagePriority.MID)
            } else if (registry.containsId(filterId)) {
                val duplicateMessageText = "Chat filter \"%s\" already exists.".format(filterId)
                val duplicateMessage = Component.nullToEmpty(duplicateMessageText)
                val duplicateSignature = ChatUtils.createSignature(-3469856)
                ChatUtils.printChat(duplicateMessage, duplicateSignature, ChatUtils.MessagePriority.MID)
            } else {
                registry.add(filterId, filterText)
                val addedMessageText = "Chat filter \"%s\" has been added.".format(filterId)
                val addedMessage = Component.nullToEmpty(addedMessageText)
                val addedSignature = ChatUtils.createSignature(-3469857)
                ChatUtils.printChat(addedMessage, addedSignature)
            }
            1
        }
        val filterArgumentBranch = filterBuilder.executes(addCommand)
        val addArguments = idArgument.then(filterArgumentBranch)
        val addBranch = branchBuilder.then(addArguments)
        var parentBuilder = commandBuilder.then(addBranch) as LiteralArgumentBuilder<Any?>

        branchBuilder = literal("remove")
        val removeArgument = argument("id", ChatFilterArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val removeCommand = { removeContext: com.mojang.brigadier.context.CommandContext<Any?> ->
            val selectedEntry = removeContext.getArgument("id", ChatFilterEntry::class.java)
            ClientContext.chatFilterRegistry.remove(selectedEntry)
            val removedMessageText = "Chat filter \"%s\" has been removed.".format(selectedEntry.id())
            val removedMessage = Component.nullToEmpty(removedMessageText)
            val removedSignature = ChatUtils.createSignature(-3469858)
            ChatUtils.printChat(removedMessage, removedSignature)
            1
        }
        val removeArgumentBranch = removeArgument.executes(removeCommand)
        val removeBranch = branchBuilder.then(removeArgumentBranch)
        parentBuilder = parentBuilder.then(removeBranch) as LiteralArgumentBuilder<Any?>

        val listBuilder = literal<Any?>("list")
        val listCommand = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            val registry = ClientContext.chatFilterRegistry
            val filterCount = registry.size()
            val countMessageText = "%d chat filter(s)".format(filterCount)
            val countMessage = Component.nullToEmpty(countMessageText)
            val countSignature = ChatUtils.createSignature(-3569855)
            ChatUtils.printChat(countMessage, countSignature)

            var rowSignatureId = -3469852
            for (description in registry.describeFilters()) {
                val descriptionMessage = Component.nullToEmpty(description)
                rowSignatureId++
                val descriptionSignature = ChatUtils.createSignature(rowSignatureId)
                ChatUtils.printChat(descriptionMessage, descriptionSignature)
            }
            1
        }
        val listBranch = listBuilder.executes(listCommand)
        parentBuilder.then(listBranch)
    }
}
