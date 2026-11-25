package me.collective.client.commands

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.Module
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.SetSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ExperimentalFeatures
import me.collective.client.utils.collections.RegistryGroupMatcher
import net.minecraft.commands.SharedSuggestionProvider
import java.util.Arrays
import java.util.concurrent.CompletableFuture

class SettingValueArgument(
   private val module: Module,
   private val settingArgumentName: String
) : ArgumentType<String>, ClientAccess {

   override fun parse(reader: StringReader): String {
      val remaining = reader.remaining
      val inputLength = reader.totalLength
      reader.cursor = inputLength
      return remaining
   }

   override fun <S> listSuggestions(context: CommandContext<S>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
      val setting: Setting<*> = try {
         ModuleSettingArgumentType.getSetting(context, this.module, this.settingArgumentName)
      } catch (optionFailure: Exception) {
         return Suggestions.empty()
      }

      if (setting is BooleanSetting) {
         val booleanArgument = BooleanSettingArgument.bool(setting)
         return booleanArgument.listSuggestions(context, builder)
      } else if (setting is EnumSetting<*>) {
         val defaultEnumValue = setting.defaultValue as Enum<*>
         val enumClass = defaultEnumValue.declaringJavaClass
         val enumConstants = enumClass.enumConstants ?: emptyArray()
         val suggestedEnumNames = Arrays.stream(enumConstants)
            .filter { !ExperimentalFeatures.isEnumConstantHidden(it) }
            .map { getEnumSuggestionName(it) }
         return SharedSuggestionProvider.suggest(suggestedEnumNames, builder)
      } else if (setting is NumberSetting<*>) {
         val currentNumber = setting.value
         val remainingInput = builder.remaining
         if (remainingInput.isBlank()) {
            val defaultNumber = setting.defaultValue
            val defaultSuggestion = defaultNumber.toString()
            return builder.suggest(defaultSuggestion).buildFuture()
         } else if (currentNumber is Float) {
            val floatMinimum = (setting.minimumValue as Number).toFloat()
            val floatMaximum = (setting.maximumValue as Number).toFloat()
            val numberArgument = FloatArgumentType.floatArg(floatMinimum, floatMaximum)
            return numberArgument.listSuggestions(context, builder)
         } else if (currentNumber is Double) {
            val doubleMinimum = (setting.minimumValue as Number).toDouble()
            val doubleMaximum = (setting.maximumValue as Number).toDouble()
            val numberArgument = DoubleArgumentType.doubleArg(doubleMinimum, doubleMaximum)
            return numberArgument.listSuggestions(context, builder)
         } else {
            val integerMinimum = (setting.minimumValue as Number).toInt()
            val integerMaximum = (setting.maximumValue as Number).toInt()
            val numberArgument = IntegerArgumentType.integer(integerMinimum, integerMaximum)
            return numberArgument.listSuggestions(context, builder)
         }
      } else if (setting is SetSetting<*>) {
         val suggestedValues = ArrayList<String>(setting.formattedAvailableEntries)
         if (setting is RegistrySetSetting<*>) {
            val registry = setting.registry
            val registrySuggestions = RegistryGroupMatcher.forRegistry(registry)
            if (registrySuggestions != RegistryGroupMatcher.EMPTY) {
               val groupNames = registrySuggestions.getGroupNames()
               suggestedValues.addAll(groupNames)
            }
         }
         return SharedSuggestionProvider.suggest(suggestedValues, builder)
      } else if (setting is IdentifierSetting) {
         val capturedIdentifierRegistry = ClientContext.soundManager
         val soundIds = capturedIdentifierRegistry.soundIds
         val suggestedIdentifiers = soundIds.stream().map { identifier ->
            "${identifier.category}:${identifier.name}"
         }
         return SharedSuggestionProvider.suggest(suggestedIdentifiers, builder)
      } else {
         return super.listSuggestions(context, builder)
      }
   }

   companion object {
      @JvmStatic
      fun value(module: Module, settingArgumentName: String): SettingValueArgument {
         return SettingValueArgument(module, settingArgumentName)
      }

      @JvmStatic
      private fun getEnumSuggestionName(value: Enum<*>): String {
         return if (value is NamedEntry) {
            value.name.lowercase()
         } else {
            value.name.lowercase()
         }
      }
   }
}
