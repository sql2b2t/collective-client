package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.settings.NamedEntry
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.concurrent.CompletableFuture

class EnumArgumentType<T : Enum<*>> @JvmOverloads constructor(
   private val enumClass: Class<T>,
   private val argumentLabel: String = "Enum"
) : ArgumentType<T> {

   private fun getEnumName(value: Enum<*>): String {
      if (value is NamedEntry) {
         return value.name
      }
      return value.name.lowercase(Locale.ROOT)
   }

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): T {
      val requestedName = reader.readString()
      val enumConstants = this.enumClass.enumConstants ?: emptyArray()

      for (enumConstant in enumConstants) {
         val candidateName = this.getEnumName(enumConstant)
         if (candidateName.equals(requestedName, ignoreCase = true)) {
            return enumConstant
         }
      }

      val unknownValueException = DynamicCommandExceptionType { invalidValue ->
         val errorMessage = "${this.argumentLabel} $invalidValue doesn't exist"
         Component.literal(errorMessage)
      }
      throw unknownValueException.create(requestedName)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val enumConstants = this.enumClass.enumConstants ?: emptyArray()
      val enumNames = enumConstants.map { this.getEnumName(it) }
      return SharedSuggestionProvider.suggest(enumNames, builder)
   }
}
