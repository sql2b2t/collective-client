package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.config.PresetData
import me.collective.client.settings.options.PresetCategory
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class PresetArgumentType : ArgumentType<String> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): String {
      return CommandStringReader.readString(reader)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      return CompletableFuture.supplyAsync {
         try {
            val configurationManager = ClientContext.configurationManager
            val category = context.getArgument("category", PresetCategory::class.java)
            val presetRegistry = configurationManager.getPresetRegistry(category)

            try {
               presetRegistry.loadPresets()
            } catch (ignoredReloadFailure: Throwable) {
            }

            // Снимок имён под локом реестра: async-чтение не должно рваться
            // при параллельной перезагрузке/сохранении пресетов.
            val presetNames = synchronized(presetRegistry.getRegistry()) {
               presetRegistry.getRegistry().map { it.name }
            }

            SharedSuggestionProvider.suggest(presetNames, builder).get()
         } catch (ignoredSuggestionFailure: Throwable) {
            Suggestions.empty().get()
         }
      }
   }

   companion object {
      @JvmStatic
      @Throws(CommandSyntaxException::class)
      fun getPreset(context: CommandContext<*>, categoryArgumentName: String, presetArgumentName: String): PresetData {
         val category = context.getArgument(categoryArgumentName, PresetCategory::class.java)
         val presetName = context.getArgument(presetArgumentName, String::class.java)
         val configurationManager = ClientContext.configurationManager
         val registryForLookup = configurationManager.getPresetRegistry(category)
         val presetResult = registryForLookup.findFirst { candidate ->
            candidate.name.equals(presetName, ignoreCase = true)
         }

         if (presetResult.isEmpty) {
            val notFoundException = DynamicCommandExceptionType { missingName ->
               Component.nullToEmpty("Preset not found $missingName")
            }
            throw notFoundException.create(presetName)
         } else {
            return presetResult.get()
         }
      }
   }
}
