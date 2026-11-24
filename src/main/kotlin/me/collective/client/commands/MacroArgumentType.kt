package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.misc.Macro
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class MacroArgumentType : ArgumentType<Macro> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Macro {
      val requestedName = reader.readString()
      val macroRegistry = ClientContext.macroRegistry
      val macros = macroRegistry.getRegistry()

      for (macro in macros) {
         if (macro.name.equals(requestedName, ignoreCase = true)) {
            return macro
         }
      }

      val unknownMacroException = DynamicCommandExceptionType { invalidName ->
         val errorMessage = String.format(UNKNOWN_MACRO_FORMAT, invalidName)
         Component.literal(errorMessage)
      }
      throw unknownMacroException.create(requestedName)
   }

   override fun <S> listSuggestions(unusedContext: CommandContext<S>, suggestions: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val macroRegistry = ClientContext.macroRegistry
      val names = macroRegistry.getRegistry().map { it.name }
      return SharedSuggestionProvider.suggest(names, suggestions)
   }

   companion object {
      private const val UNKNOWN_MACRO_FORMAT = "Macro %s doesn't exists"
   }
}
