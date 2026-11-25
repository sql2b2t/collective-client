package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.utils.chat.ChatFilterEntry
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class ChatFilterArgumentType : ArgumentType<ChatFilterEntry> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): ChatFilterEntry {
      val filterId = CommandStringReader.readString(reader)
      val filterRegistry = ClientContext.chatFilterRegistry
      val matchingFilter = filterRegistry.findById(filterId)
      if (matchingFilter.isEmpty) {
         val unknownFilter = DynamicCommandExceptionType { argument ->
            val errorMessage = UNKNOWN_FILTER_PREFIX + argument
            Component.nullToEmpty(errorMessage)
         }
         throw unknownFilter.create(filterId)
      }
      return matchingFilter.get()
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val filterRegistry = ClientContext.chatFilterRegistry
      val filterIds = filterRegistry.streamIds()
      return SharedSuggestionProvider.suggest(filterIds, builder)
   }

   companion object {
      private const val UNKNOWN_FILTER_PREFIX = "Filter not found "
   }
}
