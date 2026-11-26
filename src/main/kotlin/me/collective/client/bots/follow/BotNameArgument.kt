package me.collective.client.bots.follow

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.bots.BotManager
import me.collective.client.bots.session.BotSessionManager
import net.minecraft.commands.SharedSuggestionProvider
import java.util.*
import java.util.concurrent.CompletableFuture

class BotNameArgument : ArgumentType<String> {
    companion object {
        const val ALL: String = "all"
    }

    override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val suggestions = ArrayList<String>()
        suggestions.add(ALL)

        val controlledBot = BotSessionManager.controlledBot
        for (bot in BotManager.bots) {
            if (bot !== controlledBot && bot.connectionState.isInGame) {
                suggestions.add(bot.profile.username)
            }
        }

        return SharedSuggestionProvider.suggest(suggestions, builder)
    }

    override fun parse(reader: StringReader): String {
        return reader.readUnquotedString()
    }
}
