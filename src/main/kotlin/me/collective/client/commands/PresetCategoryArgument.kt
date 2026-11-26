package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientContext
import me.collective.client.managers.PresetRegistry
import me.collective.client.settings.options.PresetCategory
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.CompletableFuture

class PresetCategoryArgument : ArgumentType<PresetCategory> {

   @Throws(CommandSyntaxException::class)
   fun parseCategory(reader: StringReader): PresetCategory {
      val categoryName = reader.readString()
      val category = PresetCategory.findByName(categoryName)
      if (category == null) {
         val createdExceptionType = DynamicCommandExceptionType { invalidName ->
            Component.nullToEmpty(INVALID_CATEGORY_PREFIX + invalidName)
         }
         throw createdExceptionType.create(categoryName)
      } else {
         return category
      }
   }

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): PresetCategory {
      return this.parseCategory(reader)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val categories = PresetCategory.values()
      val categoryNames = Arrays.stream(categories).map { it.name.lowercase(Locale.ROOT) }
      return SharedSuggestionProvider.suggest(categoryNames, builder)
   }

   companion object {
      private const val INVALID_CATEGORY_PREFIX = "Preset category not found "

      @JvmStatic
      fun getManager(context: CommandContext<*>, argumentName: String): PresetRegistry {
         val category = context.getArgument(argumentName, PresetCategory::class.java)
         val presetManagers = ClientContext.configurationManager
         return presetManagers.getPresetRegistry(category)
      }
   }
}
