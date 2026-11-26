package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.modules.Module
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class ModuleArgumentType : ArgumentType<Module> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Module {
      val moduleName = reader.readString()
      val registry = ClientContext.moduleRegistry
      val matchingModule = registry.findFirst { module ->
         for (alias in module.aliases) {
            if (alias.equals(moduleName, ignoreCase = true)) {
               return@findFirst true
            }
         }
         false
      }

      if (matchingModule.isEmpty) {
         throw UNKNOWN_MODULE.create(moduleName)
      } else {
         return matchingModule.get()
      }
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val modules = ClientContext.moduleRegistry
      val moduleNames = modules.getRegistry().map { it.name }
      return SharedSuggestionProvider.suggest(moduleNames, builder)
   }

   companion object {
      private val UNKNOWN_MODULE = DynamicCommandExceptionType { argument ->
         Component.nullToEmpty("Module not found $argument")
      }
   }
}
