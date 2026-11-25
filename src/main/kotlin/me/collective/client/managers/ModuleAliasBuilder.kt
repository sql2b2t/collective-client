package me.collective.client.managers

import com.mojang.brigadier.RedirectModifier
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import me.collective.client.ClientContext
import me.collective.client.commands.Command
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate

open class ModuleAliasBuilder(literal: String) : LiteralArgumentBuilder<Any?>(literal) {

    override fun build(): LiteralCommandNode<Any?> {
        val node = ModuleAliasNode(
            literal,
            command,
            requirement,
            redirect,
            redirectModifier,
            isFork
        )
        for (child in arguments) {
            node.addChild(child)
        }
        return node
    }

    open class ModuleAliasNode(
        literal: String,
        command: com.mojang.brigadier.Command<Any?>?,
        requirement: Predicate<Any?>?,
        redirect: CommandNode<Any?>?,
        redirectModifier: RedirectModifier<Any?>?,
        forks: Boolean
    ) : LiteralCommandNode<Any?>(literal, command, requirement, redirect, redirectModifier, forks) {

        override fun listSuggestions(
            context: CommandContext<Any?>,
            builder: SuggestionsBuilder
        ): CompletableFuture<Suggestions> {
            val registry = ClientContext.commandRegistry
            val remaining = builder.remainingLowerCase
            for (entry in registry.getRegistry()) {
                val command = entry as? Command ?: continue
                if (command.getName().startsWith(remaining)) {
                    return Suggestions.empty()
                }
            }
            return super.listSuggestions(context, builder)
        }
    }
}
