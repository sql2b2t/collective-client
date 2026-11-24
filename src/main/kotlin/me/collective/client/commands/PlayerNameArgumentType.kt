package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientAccess
import net.minecraft.commands.SharedSuggestionProvider
import java.util.concurrent.CompletableFuture

class PlayerNameArgumentType : ClientAccess, ReifiedArgumentType<String> {

   override fun getReifiedType(): Class<String> {
      return String::class.java
   }

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): String {
      return CommandStringReader.readString(reader)
   }

   override fun getExamples(): Collection<String> {
      return EXAMPLES
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val player = ClientAccess.collectiveMinecraft.player ?: return Suggestions.empty()
      val connection = player.connection
      val names = connection.onlinePlayers.stream().map { playerInfo ->
         val profile = playerInfo.profile
         profile.name
      }
      return SharedSuggestionProvider.suggest(names, builder)
   }

   companion object {
      private val EXAMPLES: List<String> = listOf("cat", "fit", "asphyxia1337")
   }
}
