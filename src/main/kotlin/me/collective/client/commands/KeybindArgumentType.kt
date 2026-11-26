package me.collective.client.commands

import com.mojang.blaze3d.platform.InputConstants.Key
import com.mojang.blaze3d.platform.InputConstants.Type
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.modules.Keybind
import me.collective.client.utils.KeyUtils
import me.collective.mixin.InputConstantsTypeAccessor
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.concurrent.CompletableFuture

class KeybindArgumentType : ArgumentType<Keybind> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Keybind {
      val name = reader.readString().trim()
      try {
         return KeyUtils.parseKeybind(name)
      } catch (failure: IllegalArgumentException) {
         throw UNKNOWN_KEY.create(name)
      }
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val remaining = builder.remaining
      var uppercaseSuggestions = false
      for (characterIndex in remaining.length downTo 1) {
         val character = remaining[characterIndex - 1]
         if (Character.isLetter(character)) {
            uppercaseSuggestions = Character.isUpperCase(character)
            break
         }
      }

      val keyboardType = Type.KEYSYM as Any as InputConstantsTypeAccessor
      val keyMap = keyboardType.keyMap
      val keyboardSuggestions = keyMap.values.iterator()
      while (keyboardSuggestions.hasNext()) {
         val key = keyboardSuggestions.next() as? com.mojang.blaze3d.platform.InputConstants.Key ?: continue
         val suggestionText = key.name
         val normalizedKeyName = suggestionText.replace("key.keyboard.", "").replace(".", "_")
         val uppercaseKeyName = normalizedKeyName.uppercase(Locale.ROOT)
         val uppercaseQuery = remaining.uppercase(Locale.ROOT)
         if (SharedSuggestionProvider.matchesSubStr(uppercaseQuery, uppercaseKeyName)) {
            builder.suggest(if (uppercaseSuggestions) uppercaseKeyName else uppercaseKeyName.lowercase(Locale.ROOT))
         }
      }

      val aliases = KeyUtils.getKeyAliases().keys.iterator()
      while (aliases.hasNext()) {
         val alias = aliases.next()
         val uppercaseQuery = remaining.uppercase(Locale.ROOT)
         if (SharedSuggestionProvider.matchesSubStr(uppercaseQuery, alias)) {
            builder.suggest(if (uppercaseSuggestions) alias else alias.lowercase(Locale.ROOT))
         }
      }

      var mouseIndex = 0
      while (mouseIndex < 5) {
         val mouseName = "MOUSE$mouseIndex"
         if (SharedSuggestionProvider.matchesSubStr(remaining, mouseName)) {
            builder.suggest(if (uppercaseSuggestions) mouseName else mouseName.lowercase(Locale.ROOT))
         }
         mouseIndex++
      }
      return builder.buildFuture()
   }

   companion object {
      private val UNKNOWN_KEY = DynamicCommandExceptionType { argument ->
         val errorMessage = "Key not found: $argument"
         Component.literal(errorMessage)
      }
   }
}
