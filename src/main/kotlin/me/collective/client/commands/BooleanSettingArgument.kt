package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.settings.BooleanSetting
import java.util.concurrent.CompletableFuture

class BooleanSettingArgument(private val setting: BooleanSetting) : ArgumentType<Boolean> {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): Boolean {
      val input = reader.readString()
      if (input == "0" || input.equals("false", ignoreCase = true)) {
         return false
      }
      if (input == "1" || input.equals("true", ignoreCase = true)) {
         return true
      }
      if (input.equals("toggle", ignoreCase = true)) {
         return !this.setting.value
      }
      val invalidBoolean = CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidBool()
      throw invalidBoolean.createWithContext(reader, input)
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val remaining = builder.remainingLowerCase
      if ("true".startsWith(remaining)) {
         builder.suggest("true")
      }
      if ("false".startsWith(remaining)) {
         builder.suggest("false")
      }
      if ("toggle".startsWith(remaining)) {
         builder.suggest("toggle")
      }
      return builder.buildFuture()
   }

   override fun getExamples(): Collection<String> {
      return EXAMPLES
   }

   companion object {
      private val EXAMPLES: Collection<String> = listOf("true", "false", "toggle")

      @JvmStatic
      fun bool(setting: BooleanSetting): BooleanSettingArgument {
         return BooleanSettingArgument(setting)
      }

      @JvmStatic
      fun getBool(context: CommandContext<*>, name: String): Boolean {
         return context.getArgument(name, Boolean::class.javaObjectType)
      }
   }
}
