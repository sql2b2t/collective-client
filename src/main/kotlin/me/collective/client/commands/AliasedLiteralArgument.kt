package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.Arrays
import java.util.concurrent.CompletableFuture
import net.minecraft.commands.SharedSuggestionProvider

class AliasedLiteralArgument(vararg val aliases: String) : ArgumentType<String> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): String {
      val input = reader.readString()
      for (alias in this.aliases) {
         if (alias == input) {
            return alias
         }
      }

      val incorrectLiteral = CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()
      val expectedAliases = Arrays.toString(this.aliases)
      throw incorrectLiteral.createWithContext(reader, expectedAliases)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val remaining = builder.remaining
      for (alias in this.aliases) {
         if (alias == remaining) {
            builder.suggest(alias)
            return builder.buildFuture()
         }
      }

      val remainingInput = builder.remaining
      val primaryAlias = this.aliases[0]
      if (SharedSuggestionProvider.matchesSubStr(remainingInput, primaryAlias)) {
         val primarySuggestion = this.aliases[0]
         builder.suggest(primarySuggestion)
      }
      return builder.buildFuture()
   }
}
