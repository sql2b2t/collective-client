package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.utils.inventory.Kit
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class KitArgumentType : ArgumentType<Kit> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Kit {
      val requestedKitName = CommandStringReader.readString(reader)
      val kitRegistry = ClientContext.kitRegistry
      val kits = kitRegistry.getRegistry()

      for (kit in kits) {
         if (kit.name.equals(requestedKitName, ignoreCase = true)) {
            return kit
         }
      }

      val unknownKitException = DynamicCommandExceptionType { argument ->
         val errorMessage = String.format(UNKNOWN_KIT_FORMAT, argument)
         Component.literal(errorMessage)
      }
      throw unknownKitException.create(requestedKitName)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val kitRegistry = ClientContext.kitRegistry
      val kitNames = kitRegistry.getRegistry().map { it.name }
      return SharedSuggestionProvider.suggest(kitNames, builder)
   }

   companion object {
      private const val UNKNOWN_KIT_FORMAT = "Kit %s doesn't exist"
   }
}
