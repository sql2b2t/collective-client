package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientAccess
import me.collective.client.modules.Module
import me.collective.client.settings.Setting
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.concurrent.CompletableFuture

class ModuleSettingArgumentType(private val module: Module) : ArgumentType<String>, ClientAccess {

   @Throws(CommandSyntaxException::class)
   override fun parse(reader: StringReader): String {
      return reader.readString()
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val settingNames = this.module.settings.stream()
         .filter { !it.isGroupOnly }
         .map { it.configName }
      return SharedSuggestionProvider.suggest(settingNames, builder)
   }

   companion object {
      private const val UNKNOWN_SETTING_PREFIX = "Setting not found "

      @JvmStatic
      @Throws(CommandSyntaxException::class)
      fun getSetting(context: CommandContext<*>, module: Module, argumentName: String): Setting<*> {
         val requestedName = context.getArgument(argumentName, String::class.java)
         val matchedSetting = module.settings.stream()
            .filter { it.configName.equals(requestedName, ignoreCase = true) }
            .findFirst()

         if (matchedSetting.isEmpty) {
            val unknownSettingError = DynamicCommandExceptionType { argument ->
               Component.literal(UNKNOWN_SETTING_PREFIX + argument)
            }
            throw unknownSettingError.create(requestedName)
         }
         return matchedSetting.get()
      }
   }
}
