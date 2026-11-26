package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.managers.Registry
import me.collective.client.settings.NamedEntry
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class RegistryArgumentType(private val registry: Registry<*>) : ArgumentType<NamedEntry> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): NamedEntry {
      val entryName = reader.readString()
      @Suppress("UNCHECKED_CAST")
      val registryEntries = this.registry.getRegistry() as List<NamedEntry>

      for (entry in registryEntries) {
         if (entry.getName().equals(entryName, ignoreCase = true)) {
            return entry
         }
      }

      val createdExceptionType = DynamicCommandExceptionType { invalidName ->
         val message = String.format(INVALID_ENTRY_TEMPLATE, invalidName)
         Component.literal(message)
      }
      throw createdExceptionType.create(entryName)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      @Suppress("UNCHECKED_CAST")
      val entries = this.registry.getRegistry() as List<NamedEntry>
      val names = entries.map { it.getName() }
      return SharedSuggestionProvider.suggest(names, builder)
   }

   companion object {
      private const val INVALID_ENTRY_TEMPLATE = "Item %s doesn't exists"

      @JvmStatic
      fun create(registry: Registry<*>): RegistryArgumentType {
         return RegistryArgumentType(registry)
      }
   }
}
