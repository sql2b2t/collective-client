package me.collective.client.managers

import com.mojang.brigadier.RedirectModifier
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.CommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate

class CommandAliasBuilder(literal: String) : LiteralArgumentBuilder<Any?>(literal) {

    override fun build(): LiteralCommandNode<Any?> {
        val node = CommandAliasNode(
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

    open class CommandAliasNode(
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
            val literalName = name
            val remaining = builder.remainingLowerCase
            if (literalName.startsWith(remaining) && remaining.length > 3) {
                return builder.suggest(literalName).buildFuture()
            }
            return Suggestions.empty()
        }
    }
}
