package me.collective.client.commands

import me.collective.client.ClientContext
import me.collective.client.utils.collections.NamedRegistryEntry
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.collections.RegistryGroupMatcher
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.client.NotificationsModule
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.IdentifierSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.SetSetting
import me.collective.client.settings.Setting

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.Collection
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.function.UnaryOperator
import net.minecraft.ChatFormatting
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ClickEvent.RunCommand
import net.minecraft.network.chat.ClickEvent.SuggestCommand
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.network.chat.MessageSignature
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

class ModuleCommand(private val module: Module) : Command(module.name) {
   private fun createModuleTitle(): MutableComponent {
      val moduleName = this.module.name
      val title = Component.literal(moduleName)
      title.withStyle(UnaryOperator<Style> { titleStyle ->
         val titleDynamicColor = Supplier {
            if (this.module.isToggled()) {
               ChatFormatting.GREEN.color!!
            } else {
               ChatFormatting.RED.color!!
            }
         }
         ChatUtils.withDynamicColor(titleStyle, titleDynamicColor)
      })
      title.withStyle(UnaryOperator<Style> { hoverStyle ->
         val descriptionHover = ShowText(moduleDescriptionText())
         hoverStyle.withHoverEvent(descriptionHover)
      })
      title.withStyle(UnaryOperator<Style> { clickStyle ->
         val toggleClick = RunCommand(toggleCommandText(this))
         clickStyle.withClickEvent(toggleClick)
      })
      return title
   }

   private fun moduleDescriptionText(): Component {
      val description = this.module.description
      val firstDescriptionLine = description.split("\n")[0]
      return Component.literal(firstDescriptionLine)
   }

   @Suppress("UNCHECKED_CAST")
   override fun exec(builder: LiteralArgumentBuilder<*>) {
      val root = builder as LiteralArgumentBuilder<Any?>
      val stateArgumentType: ArgumentType<Boolean> = BoolArgumentType.bool()
      val stateSuggestionsBuilder: RequiredArgumentBuilder<Any?, *> = argument("state", stateArgumentType)
      val stateActionBuilder = stateSuggestionsBuilder.suggests { suggestionContext: CommandContext<Any?>, suggestionBuilder: SuggestionsBuilder ->
         suggestionBuilder.buildFuture()
      }
      val stateArgument: ArgumentBuilder<Any?, *> = stateActionBuilder.executes { stateContext: CommandContext<Any?> ->
         val moduleForStateChange = this.module

         val requestedState = stateContext.getArgument("state", Boolean::class.javaObjectType)
         moduleForStateChange.setToggled(requestedState)
         1
      }
      var rootWithState: LiteralArgumentBuilder<Any?> = root.then(stateArgument)

      val settingArgumentType = ModuleSettingArgumentType(this.module)
      var settingArgument: RequiredArgumentBuilder<Any?, *> = argument("setting", settingArgumentType)
      val clearSuggestionsBuilder: RequiredArgumentBuilder<Any?, *> = aliasedLiteral("clear")
      val clearActionBuilder = clearSuggestionsBuilder.suggests { suggestionContext: CommandContext<Any?>, suggestionBuilder: SuggestionsBuilder ->
         this.suggestClearOperation(suggestionContext, suggestionBuilder)
      }
      val clearSettingBranch: ArgumentBuilder<Any?, *> = clearActionBuilder.executes { clearContext: CommandContext<Any?> ->
         val moduleForLookup = this.module
         val setting = ModuleSettingArgumentType.getSetting(clearContext, moduleForLookup, "setting")
         if (setting is SetSetting<*>) {
            val setSetting = setting
            val valuesToClear = setSetting.value as MutableSet<Any?>
            valuesToClear.clear()

            setSetting.runChangeCallback()
            val notificationsForCheck = notifications
            if (NotificationsModule.shouldNotifyCommands(notificationsForCheck)) {
               val moduleForName = this.module
               val moduleName = moduleForName.name
               val moduleNameText = Component.literal(moduleName)
               val spaceText = Component.literal(" ")
               val settingName = setting.name
               val clearedSettingNameText: MutableComponent = spaceText.append(settingName)
               val clearedSettingName: Component = clearedSettingNameText.withStyle(UnaryOperator { grayStyle ->
                  grayStyle.applyFormat(ChatFormatting.GRAY)
               })
               val settingHeader: MutableComponent = moduleNameText.append(clearedSettingName)

               val clearedMessage: MutableComponent = settingHeader.append(": Cleared")
               val clearedMessageSignature = ChatUtils.moduleSignature(this.module)
               val clearedMessagePriority = ChatUtils.MessagePriority.LOW
               ChatUtils.printChat(clearedMessage, clearedMessageSignature, clearedMessagePriority)
            }
         }

         1
      }
      settingArgument = settingArgument.then(clearSettingBranch) as RequiredArgumentBuilder<Any?, *>

      val valueArgumentType = SettingValueArgument(this.module, "setting")
      val valueActionBuilder = argument("value", valueArgumentType)
      val valueSettingBranch: ArgumentBuilder<Any?, *> = valueActionBuilder.executes { valueContext: CommandContext<Any?> ->
         val moduleForLookup = this.module
         val setting = ModuleSettingArgumentType.getSetting(valueContext, moduleForLookup, "setting")

         val requestedValue = valueContext.getArgument("value", String::class.java)

         try {
            var changeFormatting: ChatFormatting? = null
            val registrySetSetting = setting as? RegistrySetSetting<*>
            if (registrySetSetting != null) {
               val registry = registrySetSetting.registry
               val registryMatcher = RegistryGroupMatcher.forRegistry(registry)
               val matchingEntries = registryMatcher.getGroupEntries(requestedValue) as Collection<NamedRegistryEntry>
               if (!matchingEntries.isEmpty()) {
                  val registryEntriesPresent = Predicate<NamedRegistryEntry> { entry ->
                     val entryName = entry.name()

                     registrySetSetting.containsParsedEntry(entryName)
                  }
                  if (matchingEntries.stream().anyMatch(registryEntriesPresent)) {
                     matchingEntries.forEach { entry ->
                        val entryName = entry.name()

                        registrySetSetting.removeParsedEntry(entryName)
                     }
                     changeFormatting = ChatFormatting.RED
                  } else {
                     matchingEntries.forEach { entry ->
                        val entryName = entry.name()

                        registrySetSetting.addParsedEntry(entryName)
                     }
                     changeFormatting = ChatFormatting.GREEN
                  }
               }
            }

            if (changeFormatting == null) {
               setting.parseValue(requestedValue)
            }

            var valueText = this.createSettingValueText(setting)
            val setSetting = setting as? SetSetting<*>
            if (setSetting != null) {
               if (changeFormatting == null) {
                  changeFormatting = if (setSetting.containsParsedEntry(requestedValue)
                  ) ChatFormatting.GREEN
                  else ChatFormatting.RED
               }

               val capturedChangeFormatting = changeFormatting
               val changedEntryText = Component.literal(requestedValue)
               valueText = changedEntryText.withStyle(UnaryOperator { entryStyle ->
                  entryStyle.applyFormat(capturedChangeFormatting)
               })
            }

            val notificationsForCheck = notifications
            if (NotificationsModule.shouldNotifyCommands(notificationsForCheck)) {
               val moduleForName = this.module
               val moduleName = moduleForName.name
               val moduleNameText = Component.literal(moduleName)
               val spaceText = Component.literal(" ")
               val settingName = setting.name
               val changedSettingNameText: MutableComponent = spaceText.append(settingName)
               val changedSettingName: Component = changedSettingNameText.withStyle(UnaryOperator { grayStyle ->
                  grayStyle.applyFormat(ChatFormatting.GRAY)
               })
               val settingHeader: MutableComponent = moduleNameText.append(changedSettingName)

               val changedValueHeader: MutableComponent = settingHeader.append(": ")
               val changedMessage: MutableComponent = changedValueHeader.append(valueText)
               val changedMessageSignature = ChatUtils.moduleSignature(this.module)
               val changedMessagePriority = ChatUtils.MessagePriority.LOW
               ChatUtils.printChat(changedMessage, changedMessageSignature, changedMessagePriority)
            }
         } catch (valueFailure: Throwable) {
            var invalidValueMessage: MutableComponent = Component.literal("Invalid value: ")
            invalidValueMessage = invalidValueMessage.append(requestedValue)
            val invalidValueMessageSignature = ChatUtils.moduleSignature(this.module)
            val invalidValueMessagePriority = ChatUtils.MessagePriority.HIGH
            ChatUtils.printChat(invalidValueMessage, invalidValueMessageSignature, invalidValueMessagePriority)
         }

         1
      }
      settingArgument = settingArgument.then(valueSettingBranch) as RequiredArgumentBuilder<Any?, *>

      val resetSettingActionBuilder = literal("reset")
      val resetSettingBranch: ArgumentBuilder<Any?, *> = resetSettingActionBuilder.executes { resetSettingContext: CommandContext<Any?> ->
         val moduleForLookup = this.module
         val setting = ModuleSettingArgumentType.getSetting(resetSettingContext, moduleForLookup, "setting")
         setting.reset()
         1
      }
      val querySettingActionBuilder = settingArgument.then(resetSettingBranch)
      val settingQueryBranch: ArgumentBuilder<Any?, *> = querySettingActionBuilder.executes { querySettingContext: CommandContext<Any?> ->
         val moduleForLookup = this.module
         val setting = ModuleSettingArgumentType.getSetting(querySettingContext, moduleForLookup, "setting")
         val moduleForName = this.module
         val moduleName = moduleForName.name
         var queryMessage: MutableComponent = Component.literal(moduleName)
         val spaceText = Component.literal(" ")
         val settingName = setting.name
         val querySettingNameText: MutableComponent = spaceText.append(settingName)
         val querySettingName: Component = querySettingNameText.withStyle(UnaryOperator { grayStyle ->
            grayStyle.applyFormat(ChatFormatting.GRAY)
         })
         queryMessage = queryMessage.append(querySettingName)

         queryMessage = queryMessage.append(": ")
         val querySettingValue = this.createSettingValueText(setting)
         queryMessage = queryMessage.append(querySettingValue)
         val queryMessageSignature = ChatUtils.moduleSignature(this.module)
         ChatUtils.printChat(queryMessage, queryMessageSignature)
         1
      }
      rootWithState = rootWithState.then(settingQueryBranch)

      val enabledBuilder = literal("Enabled")

      val enabledStateArgumentType: ArgumentType<Boolean> = BoolArgumentType.bool()
      val enabledStateActionBuilder = argument("state", enabledStateArgumentType)
      val enabledStateArgument: ArgumentBuilder<Any?, *> = enabledStateActionBuilder.executes { enabledContext: CommandContext<Any?> ->
         val moduleForStateChange = this.module

         val requestedState = enabledContext.getArgument("state", Boolean::class.javaObjectType)
         moduleForStateChange.setToggled(requestedState)
         1
      }
      val enabledQueryActionBuilder: LiteralArgumentBuilder<Any?> = enabledBuilder.then(enabledStateArgument)
      val enabledQueryBranch: ArgumentBuilder<Any?, *> = enabledQueryActionBuilder.executes { queryEnabledContext: CommandContext<Any?> ->
         var enabledMessage: MutableComponent = Component.literal("Module ")
         val moduleForName = this.module
         val moduleName = moduleForName.name
         val enabledModuleNameText = Component.literal(moduleName)
         val enabledModuleName: Component = enabledModuleNameText.withStyle(UnaryOperator { grayStyle ->
            grayStyle.applyFormat(ChatFormatting.GRAY)
         })
         enabledMessage = enabledMessage.append(enabledModuleName)

         enabledMessage = enabledMessage.append(" is ")
         val moduleForState = this.module
         val stateText = if (moduleForState.isToggled()) {
            "enabled"
         } else {
            "disabled"
         }

         val enabledStateValueText = Component.literal(stateText)
         val enabledStateText: Component = enabledStateValueText.withStyle(UnaryOperator { stateStyle ->
            val moduleForStateColor = this.module
            val formattedStyle: Style
            if (moduleForStateColor.isToggled()) {
               formattedStyle = stateStyle.applyFormat(ChatFormatting.GREEN)
            } else {
               formattedStyle = stateStyle.applyFormat(ChatFormatting.RED)
            }

            formattedStyle
         })
         enabledMessage = enabledMessage.append(enabledStateText)
         val enabledMessageSignature = ChatUtils.moduleSignature(this.module)
         ChatUtils.printChat(enabledMessage, enabledMessageSignature)
         1
      }
      val overviewActionBuilder: LiteralArgumentBuilder<Any?> = rootWithState.then(enabledQueryBranch)
      overviewActionBuilder.executes { overviewContext: CommandContext<Any?> ->
         val overviewText = Component.empty()

         val titleForKeybind: MutableComponent = overviewText.append(this.createModuleTitle())

         val keybindForName = this.module.keybind
         val keybindMode = keybindForName.mode()
         val keybindText = " [%s - %s]".format(keybindForName.displayName, keybindMode.getName())
         titleForKeybind.append(keybindText)
         val moduleForSettings = this.module
         for (setting in moduleForSettings.settings) {
            val settingLabel = "\n" + setting.name + ": "
            val settingLine = Component.literal(settingLabel)
            when (setting) {
               is EnumSetting<*> -> {
                  val enumValue = setting.value as Enum<*>
                  val enumName = FontRenderer.capitalize(
                     enumValue.name
                  )
                  val enumText = Component.literal(enumName)
                  val enumFormatting = ChatFormatting.GRAY
                  val overviewEnumValue: Component = enumText.withStyle(enumFormatting)
                  settingLine.append(overviewEnumValue)
               }
               is SetSetting<*> -> {
                  val setNames = setting.formattedValues.joinToString(", ")
                  val setText = Component.literal(setNames)
                  val setFormatting = ChatFormatting.GRAY
                  val overviewSetValue: Component = setText.withStyle(setFormatting)
                  settingLine.append(overviewSetValue)
               }
               is ColorSetting -> {
                  val colorString = ColorUtils.formatRgb(
                     setting.color,
                     !setting.opaqueColor
                  )
                  val overviewColorText = Component.literal(colorString)
                  val overviewColorValue: Component = overviewColorText.withStyle(UnaryOperator { colorStyle ->
                     val currentColor = setting.color
                     val colorRgb = currentColor.hashCode()
                     colorStyle.withColor(colorRgb)
                  })
                  settingLine.append(overviewColorValue)
               }
               else -> {
                  val settingValueString = setting.value!!.toString()
                  val settingValueText = Component.literal(settingValueString)
                  val valueFormatting = ChatFormatting.GRAY
                  val overviewOtherValue: Component = settingValueText.withStyle(valueFormatting)
                  settingLine.append(overviewOtherValue)
               }
            }

            val commandPrefix = CommandRegistry.getPrefix()
            val moduleName = this.module.name
            val lowercaseModuleName = moduleName.lowercase(Locale.ROOT)

            val settingCommand = commandPrefix.toString() + lowercaseModuleName + " " + setting.name + " "
            settingLine.withStyle(UnaryOperator { suggestionStyle ->
               val settingClick = SuggestCommand(settingCommand)
               suggestionStyle.withClickEvent(settingClick)
            })
            overviewText.append(settingLine)
         }

         val overviewMessageSignature = ChatUtils.createSignature(-1)
         ChatUtils.printChat(overviewText, overviewMessageSignature)
         1
      }

      val resetModuleActionBuilder = literal("reset")
      val resetModuleBranch: ArgumentBuilder<Any?, *> = resetModuleActionBuilder.executes { resetModuleContext: CommandContext<Any?> ->
         val moduleForSettings = this.module
         for (setting in moduleForSettings.settings) {
            setting.reset()
         }

         1
      }
      root.then(resetModuleBranch)
   }

   private fun suggestClearOperation(
      context: CommandContext<Any?>, builder: SuggestionsBuilder
   ): CompletableFuture<Suggestions> {
      try {
         val moduleForLookup = this.module
         val setting = ModuleSettingArgumentType.getSetting(context, moduleForLookup, "setting")
         if (setting is SetSetting<*>) {
            return SharedSuggestionProvider.suggest(arrayOf("clear"), builder)
         }
      } catch (ignoredSyntaxFailure: CommandSyntaxException) {
      }

      return Suggestions.empty()
   }

   private fun createSettingValueText(setting: Setting<*>): Component {
      val valueString = setting.value!!.toString()
      var text: MutableComponent = Component.literal(valueString)
      val colorSetting = setting as? ColorSetting
      if (colorSetting != null) {
         val colorString = ColorUtils.toHex(
            colorSetting.color, true
         )
         val settingColorText = Component.literal(colorString)
         text = settingColorText.withStyle(UnaryOperator { colorStyle ->
            val colorRgb = setting.value!!.hashCode()
            colorStyle.withColor(colorRgb)
         })
      } else {
         val setSetting = setting as? SetSetting<*>
         if (setSetting != null) {
            val setValues = setSetting.formattedValues

            val settingSeparatorText = Component.literal(", ")
            val formattedSeparator: Component = settingSeparatorText.withStyle(UnaryOperator { separatorStyle ->
               separatorStyle.applyFormat(ChatFormatting.GRAY)
            })
            text = ComponentUtils.formatList(setValues, formattedSeparator, java.util.function.Function<String, Component> { value ->
               Component.literal(value)
            })
         } else {
            val identifierSetting = setting as? IdentifierSetting
            if (identifierSetting != null) {
               val identifier = identifierSetting.value

               val identifierName = identifier.name
               text = Component.literal(identifierName)
            }
         }
      }

      return text
   }

   companion object {
      private val notifications: NotificationsModule get() = ClientContext.getModule(NotificationsModule::class.java)

      private fun toggleCommandText(owner: ModuleCommand): String {
         val commandPrefix = CommandRegistry.getPrefix()
         val moduleForCommand = owner.module
         val moduleName = moduleForCommand.name
         return commandPrefix.toString() + "toggle " + moduleName
      }
   }
}
