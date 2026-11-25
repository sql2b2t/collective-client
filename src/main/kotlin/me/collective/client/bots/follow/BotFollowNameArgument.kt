package me.collective.client.bots.follow

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientAccess
import net.minecraft.commands.SharedSuggestionProvider
import java.util.*
import java.util.concurrent.CompletableFuture

class BotFollowNameArgument : ArgumentType<String>, ClientAccess {
    companion object {
        const val OFF: String = "off"
    }

    override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        val suggestions = ArrayList<String>()
        suggestions.add(OFF)

        val player = ClientAccess.collectiveMinecraft.player
        if (player != null) {
            for (info in player.connection.onlinePlayers) {
                suggestions.add(info.profile.name)
            }
        }

        return SharedSuggestionProvider.suggest(suggestions, builder)
    }

    override fun parse(reader: StringReader): String {
        return reader.readUnquotedString()
    }
}
