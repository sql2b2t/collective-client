package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.InputPressEvent
import me.collective.client.events.EventHandler
import me.collective.client.modules.misc.Macro
import me.collective.client.render.FontRenderer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.config.JsonUtils
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Keybind
import me.collective.client.settings.options.MacroType

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.suggestion.Suggestion
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.function.UnaryOperator
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent.SuggestCommand
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent.ShowText
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

class MacroCommand : Command("macro") {
   private var waitingForKey: Boolean = false
   private var pendingMacroName: String? = null
   private var pendingMacroType: MacroType? = null

   private fun commandArgument() =
      argument("command", StringArgumentType.greedyString())
         .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
            suggestCommands(context, builder)
         }

   @Suppress("UNCHECKED_CAST")
   override fun exec(builder: LiteralArgumentBuilder<*>) {
      val root = builder as LiteralArgumentBuilder<Any?>
      val deleteBranch = buildMacroBranch("delete") { deleteArgument ->
         deleteArgument.executes { deleteContext: CommandContext<Any?> ->
            val deletedMacro = deleteContext.getArgument("macro", Macro::class.java)
            ClientContext.macroRegistry.unregisterEntry(deletedMacro)
            val deletedName = FontRenderer.capitalize(
               deletedMacro.name
            )
            var deletedMessage: MutableComponent = Component.literal(deletedName)

            deletedMessage = deletedMessage.append(" has been deleted")

            ChatUtils.printChat(deletedMessage, ChatUtils.DEFAULT_SIGNATURE)
            1
         }
      }
      var registrationBuilder: LiteralArgumentBuilder<Any?> = root.then(deleteBranch)

      val commandsBranch = buildMacroBranch("commands") { this.configureCommands(it) }
      registrationBuilder = registrationBuilder.then(commandsBranch)

      val newLiteral = literal("new")

      val nameArgument: RequiredArgumentBuilder<Any?, *> = argument("name", FancyStringArgument.fancyString())

      val typeArgument: RequiredArgumentBuilder<Any?, *> = argument("type", EnumArgumentType(MacroType::class.java, "Macro"))

      val keyArgument: RequiredArgumentBuilder<Any?, *> = argument("key", KeybindArgumentType())

      val createWithCommand: ArgumentBuilder<Any?, *> = commandArgument().executes { createContext: CommandContext<Any?> ->
         val createdName = createContext.getArgument("name", String::class.java)

         val createdCommand = createContext.getArgument("command", String::class.java)

         val createdType = createContext.getArgument("type", MacroType::class.java)

         val createdKeybind = createContext.getArgument("key", Keybind::class.java)
         val createdMacro = createdType.create(createdName, createdKeybind)
         createdMacro.commands.add(createdCommand)
         ClientContext.macroRegistry.registerEntry(createdMacro)

         var createNamedMessage: MutableComponent = Component.literal("Macro ").append(createdMacro.getDisplayComponent())

         val createdMessage = createNamedMessage.append(" has been created")

         ChatUtils.printChat(createdMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
      val createWithKey: ArgumentBuilder<Any?, *> = (keyArgument.then(createWithCommand)).executes { emptyCreateContext: CommandContext<Any?> ->
         val emptyMacroName = emptyCreateContext.getArgument("name", String::class.java)

         val emptyMacroType = emptyCreateContext.getArgument("type", MacroType::class.java)

         val emptyMacroKeybind = emptyCreateContext.getArgument("key", Keybind::class.java)
         val emptyMacro = emptyMacroType.create(emptyMacroName, emptyMacroKeybind)
         ClientContext.macroRegistry.registerEntry(emptyMacro)

         var emptyNamedMessage: MutableComponent = Component.literal("Macro ").append(emptyMacro.getDisplayComponent())

         val emptyCreatedMessage = emptyNamedMessage.append(" has been created")

         ChatUtils.printChat(emptyCreatedMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
      val captureKey: ArgumentBuilder<Any?, *> = (typeArgument.then(createWithKey)).executes { captureContext: CommandContext<Any?> ->
         this.pendingMacroName = captureContext.getArgument("name", String::class.java)

         this.pendingMacroType = captureContext.getArgument("type", MacroType::class.java)
         this.waitingForKey = true
         // Без подписки onInputPress никогда не сработает и макрос не создастся.
         ClientAccess.eventBus.subscribe(this)

         val capturePromptMessage = Component.literal("Press a key")

         ChatUtils.printChat(capturePromptMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
      val newArguments: ArgumentBuilder<Any?, *> = nameArgument.then(captureKey)
      val newBranch: ArgumentBuilder<Any?, *> = newLiteral.then(newArguments)
      registrationBuilder = registrationBuilder.then(newBranch)

      val listBranch: ArgumentBuilder<Any?, *> = literal("list").executes { listContext: CommandContext<Any?> ->
         var listIndex = 0

         val listHeaderMessage = Component.literal("Macro list:")

         ChatUtils.printChat(listHeaderMessage, ChatUtils.DEFAULT_SIGNATURE)
         val listRegistry = ClientContext.macroRegistry

         for (listedMacro in listRegistry.getRegistry() as List<Macro>) {
            val listedKeybind = listedMacro.keybind
            var listLine = "%s (%s)".format(listedMacro.name, listedKeybind.displayName)
            if (!listedMacro.isToggled()) {
               val disabledColor = ChatFormatting.GRAY
               listLine = disabledColor.toString() + listLine
            }

            val listLineMessage = Component.literal(listLine)
            val listSignatureId = -listIndex - 2

            ChatUtils.printChat(listLineMessage, ChatUtils.createSignature(listSignatureId))

            listIndex++
         }

         0
      }
      registrationBuilder.then(listBranch)

      val bindBranch = buildMacroBranch("bind") { this.configureBind(it) }
      root.then(bindBranch)

      val toggleBranch = buildMacroBranch("toggle") { this.configureToggle(it) }
      root.then(toggleBranch)

      val exportLiteral = literal("export")

      val exportArgument: ArgumentBuilder<Any?, *> = argument("macro", MacroArgumentType()).executes { context: CommandContext<Any?> ->
         this.exportMacro(context)
      }
      val exportBranch: ArgumentBuilder<Any?, *> = exportLiteral.then(exportArgument)
      root.then(exportBranch)

      val importBranch: ArgumentBuilder<Any?, *> = literal("import").executes { context: CommandContext<Any?> ->
         this.importMacro(context)
      }
      root.then(importBranch)
   }

   private fun exportMacro(context: CommandContext<Any?>): Int {
      val macro = context.getArgument("macro", Macro::class.java)
      val config = JsonObject()

      val macroName = macro.name
      config.addProperty("name", macroName)
      val type = macro.type
      val typeName = type.getName()
      config.addProperty("type", typeName)
      val keybind = macro.keybind

      config.addProperty("key", keybind.code())
      val mouse = keybind.mouse()
      config.addProperty("mouse", mouse)
      val commandsJson = JsonArray()

      for (command in macro.commands) {
         commandsJson.add(command as String)
      }

      config.add("commands", commandsJson)
      val gson = ClientAccess.gson
      val json = gson.toJson(config)
      val jsonBytes = json.toByteArray(StandardCharsets.UTF_8)
      val encodedConfig = Base64.getEncoder().encodeToString(jsonBytes)
      val keyboard = ClientAccess.collectiveMinecraft.keyboardHandler
      keyboard.setClipboard(encodedConfig)

      var message: MutableComponent = Component.literal("Successfully copied ")
      val nameForMessage = macro.name
      val nameComponent = Component.literal(nameForMessage)
      val nameColor = ChatFormatting.GRAY
      val namedMessage = message.append(nameComponent.withStyle(nameColor))

      val completedMessage = namedMessage.append(" macro to your clipboard")

      ChatUtils.printChat(completedMessage, ChatUtils.DEFAULT_SIGNATURE)
      return 1
   }

   private fun buildMacroBranch(
      name: String, configureArgument: (RequiredArgumentBuilder<Any?, *>) -> Unit
   ): LiteralArgumentBuilder<Any?> {
      val macroArgument: RequiredArgumentBuilder<Any?, *> = argument("macro", MacroArgumentType())
      configureArgument(macroArgument)
      val literalBuilder = literal(name)
      return literalBuilder.then(macroArgument)
   }

   private fun configureCommands(macroArgument: RequiredArgumentBuilder<Any?, *>) {
      var literalBuilder = literal("add")

      val addCommand: ArgumentBuilder<Any?, *> = commandArgument().executes { addContext: CommandContext<Any?> ->
         val addMacro = addContext.getArgument("macro", Macro::class.java)

         val addedCommand = addContext.getArgument("command", String::class.java)
         if (addMacro.type == MacroType.HOLD) {
            val holdCommandCount = addMacro.commands.size

            if (holdCommandCount >= 2) {
               val holdErrorColor = ChatFormatting.RED
               val holdError = holdErrorColor.toString() + "You've reached the command limit of Hold Macro"
               val holdErrorMessage = Component.literal(holdError)

               ChatUtils.printChat(holdErrorMessage, ChatUtils.DEFAULT_SIGNATURE)
               return@executes 1
            }
         }
         addMacro.commands.add(addedCommand)
         1
      }
      val addBranch: ArgumentBuilder<Any?, *> = literalBuilder.then(addCommand)
      var commandBranches: RequiredArgumentBuilder<Any?, *> = macroArgument.then(addBranch) as RequiredArgumentBuilder<Any?, *>

      literalBuilder = literal("remove")

      val removeIndexArgument: ArgumentBuilder<Any?, *> = argument("index", IntegerArgumentType.integer(1)).executes { removeContext: CommandContext<Any?> ->
         val removeMacro = removeContext.getArgument("macro", Macro::class.java)

         val removeOneBasedIndex: Int = removeContext.getArgument("index", Integer::class.java).toInt()
         val removeIndex = removeOneBasedIndex - 1
         if (removeIndex < removeMacro.commands.size &&
         removeIndex >= 0 &&
         !removeMacro.commands.isEmpty()
         ) {
            removeMacro.commands.removeAt(removeIndex)
            1
         } else {
            val removeErrorColor = ChatFormatting.RED
            val removeErrorColorText = removeErrorColor.toString()
            val removeResetColor = ChatFormatting.WHITE
            val removeResetColorText = removeResetColor.toString()
            val removeError = removeErrorColorText + "Invalid index" + removeResetColorText + "\n"
            val removeErrorMessage = Component.literal(removeError)
            removeErrorMessage.append(this.formatCommands(removeMacro))
            ChatUtils.printChat(removeErrorMessage, ChatUtils.DEFAULT_SIGNATURE)
            1
         }
      }
      val removeBranch: ArgumentBuilder<Any?, *> = literalBuilder.then(removeIndexArgument)
      commandBranches = commandBranches.then(removeBranch) as RequiredArgumentBuilder<Any?, *>

      val clearBranch: ArgumentBuilder<Any?, *> = literal("clear").executes { clearContext: CommandContext<Any?> ->
         val clearMacro = clearContext.getArgument("macro", Macro::class.java)
         clearMacro.commands.clear()
         1
      }
      commandBranches = commandBranches.then(clearBranch) as RequiredArgumentBuilder<Any?, *>

      literalBuilder = literal("edit")

      val editIndexArgument: RequiredArgumentBuilder<Any?, *> = argument("index", IntegerArgumentType.integer(1))

      val editCommandArgument: ArgumentBuilder<Any?, *> = commandArgument().executes { editContext: CommandContext<Any?> ->
         val editMacro = editContext.getArgument("macro", Macro::class.java)

         val editedCommand = editContext.getArgument("command", String::class.java)

         val editOneBasedIndex: Int = editContext.getArgument("index", Integer::class.java).toInt()
         val editIndex = editOneBasedIndex - 1
         if (editIndex >= editMacro.commands.size) {
            val editErrorColor = ChatFormatting.RED
            val editErrorColorText = editErrorColor.toString()
            val editResetColor = ChatFormatting.WHITE
            val editResetColorText = editResetColor.toString()
            val editError = editErrorColorText + "Invalid index" + editResetColorText + "\n"
            val editErrorMessage = Component.literal(editError)
            editErrorMessage.append(this.formatCommands(editMacro))
            ChatUtils.printChat(editErrorMessage, ChatUtils.DEFAULT_SIGNATURE)
            1
         } else {
            editMacro.commands[editIndex] = editedCommand
            1
         }
      }
      val editArguments: ArgumentBuilder<Any?, *> = editIndexArgument.then(editCommandArgument)
      val editBranch: ArgumentBuilder<Any?, *> = literalBuilder.then(editArguments)
      (commandBranches.then(editBranch)).executes { showContext: CommandContext<Any?> ->
         val shownMacro = showContext.getArgument("macro", Macro::class.java)
         val commandListMessage = this.formatCommands(shownMacro)

         ChatUtils.printChat(commandListMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
   }

   private fun formatCommands(macro: Macro): Component {
      val header = macro.name + "' command list: \n"
      val message = Component.literal(header)
      var lineNumber = 1

      while (lineNumber <= macro.commands.size) {
         val lineNumberText = lineNumber.toString()
         message.append(lineNumberText)

         message.append(". ")
         val command = macro.commands[lineNumber - 1] as String
         message.append(command)
         message.append(" ")
         message.append(this.formatEditAction(macro, lineNumber))
         message.append(" ")
         message.append(this.formatRemoveAction(macro, lineNumber))
         if (lineNumber != macro.commands.size) {
            message.append("\n")
         }

         lineNumber++
      }
      return message
   }

   private fun formatEditAction(macro: Macro, lineNumber: Int): Component {
      val suggestedCommand = CommandRegistry.getPrefix() + "macro commands %s edit %d ".format(macro.name, lineNumber)

      val hoverText = "click to edit"

      val labelComponent = Component.literal("[Edit]")
      val labelColor = ChatFormatting.GRAY
      val color = labelColor.color!!
      return labelComponent.withColor(color).withStyle(UnaryOperator { style: Style ->
         style.withClickEvent(SuggestCommand(suggestedCommand))
      }).withStyle(UnaryOperator { hoverStyle: Style ->
         hoverStyle.withHoverEvent(ShowText(Component.literal(hoverText)))
      })
   }

   private fun configureToggle(macroArgument: RequiredArgumentBuilder<Any?, *>) {
      val setStateArgument: ArgumentBuilder<Any?, *> = argument("state", BoolArgumentType.bool()).executes { setContext: CommandContext<Any?> ->
         val setMacro = setContext.getArgument("macro", Macro::class.java)

         val requestedState = BoolArgumentType.getBool(setContext, "state")
         setMacro.setToggled(requestedState)
         val setStateText = if (setMacro.isToggled()) "enabled" else "disabled"

         val setMessageText = "Macro %s has been %s".format(setMacro.name, setStateText)
         val setMessage = Component.literal(setMessageText)

         ChatUtils.printChat(setMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
      (macroArgument.then(setStateArgument)).executes { toggleContext: CommandContext<Any?> ->
         val toggledMacro = toggleContext.getArgument("macro", Macro::class.java)
         val nextState = !toggledMacro.isToggled()
         toggledMacro.setToggled(nextState)
         val toggleStateText = if (toggledMacro.isToggled()) "enabled" else "disabled"

         val toggleMessageText = "Macro %s has been %s".format(toggledMacro.name, toggleStateText)
         val toggleMessage = Component.literal(toggleMessageText)

         ChatUtils.printChat(toggleMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
   }

   private fun suggestCommands(
      context: CommandContext<Any?>, builder: SuggestionsBuilder
   ): CompletableFuture<Suggestions> {
      return try {
         var absoluteStart = builder.start
         var relativeCursor = 0
         val commandText = StringBuilder()
         val remaining = builder.remaining

         for (character in remaining.toCharArray()) {
            relativeCursor++

            if (isCommandPrefix(character) || character == COMMAND_SEPARATOR || (relativeCursor == 1 && character == ' ')) {
               absoluteStart += relativeCursor
               relativeCursor = 0
               commandText.setLength(0)
            } else {
               commandText.append(character)
            }
         }

         val completionCursor = relativeCursor
         val completionOffset = absoluteStart
         val completionDispatcher = CommandRegistry.commandDispatcher as CommandDispatcher<Any?>
         val command = commandText.toString()
         val commandSource: Any? = null
         val parseResults = completionDispatcher.parse(command, commandSource)
         completionDispatcher.getCompletionSuggestions(parseResults, completionCursor).thenApply { suggestions ->
            var range = suggestions.range
            range = StringRange(range.start + completionOffset, range.end + completionOffset)
            val shiftedSuggestions = suggestions.list.map { suggestion ->
               Suggestion(
                  StringRange(
                     suggestion.range.start + completionOffset,
                     suggestion.range.end + completionOffset
                  ),
                  suggestion.text,
                  suggestion.tooltip
               )
            }
            Suggestions(range, shiftedSuggestions)
         }
      } catch (suggestionFailure: Exception) {
         builder.buildFuture()
      }
   }

   @EventHandler
   private fun onInputPress(event: InputPressEvent) {
      val pendingType = this.pendingMacroType
      val pendingName = this.pendingMacroName
      if (this.waitingForKey && pendingType != null && pendingName != null) {
         val keybind = Keybind(
            event.code, Keybind.Mode.TOGGLE, event.isMouse
         )
         val macro = pendingType.create(pendingName, keybind)
         ClientContext.macroRegistry.registerEntry(macro)

         var namedMessage: MutableComponent = Component.literal("Macro ").append(macro.getDisplayComponent())

         val createdMessage = namedMessage.append(" has been created")

         ChatUtils.printChat(createdMessage, ChatUtils.DEFAULT_SIGNATURE)
         this.waitingForKey = false
         this.pendingMacroName = null
         this.pendingMacroType = null
         ClientAccess.eventBus.unsubscribe(this)
      }
   }

   private fun formatRemoveAction(macro: Macro, lineNumber: Int): Component {
      val suggestedCommand = CommandRegistry.getPrefix() + "macro commands %s remove %d ".format(macro.name, lineNumber)

      val hoverText = "click to remove"

      val labelComponent = Component.literal("[Remove]")
      val labelColor = ChatFormatting.GRAY
      val color = labelColor.color!!
      return labelComponent.withColor(color).withStyle(UnaryOperator { style: Style ->
         style.withClickEvent(SuggestCommand(suggestedCommand))
      }).withStyle(UnaryOperator { hoverStyle: Style ->
         hoverStyle.withHoverEvent(ShowText(Component.literal(hoverText)))
      })
   }

   private fun configureBind(macroArgument: RequiredArgumentBuilder<Any?, *>) {
      val keyArgument: ArgumentBuilder<Any?, *> = argument("key", KeybindArgumentType()).executes { bindContext: CommandContext<Any?> ->
         val previousMacro = bindContext.getArgument("macro", Macro::class.java)

         val newKeybind = bindContext.getArgument("key", Keybind::class.java)
         val previousType = previousMacro.type
         val previousName = previousMacro.name
         val replacementMacro = previousType.create(previousName, newKeybind)
         val previousToggleState = previousMacro.isToggled()
         replacementMacro.setToggled(previousToggleState)
         previousMacro.commands.forEach { copiedCommand ->
            replacementMacro.commands.add(copiedCommand)
         }
         ClientContext.macroRegistry.unregisterEntry(previousMacro)
         ClientContext.macroRegistry.registerEntry(replacementMacro)

         val bindMessageText = "Macro %s key has been changed to %s".format(previousMacro.name, newKeybind.displayName)
         val bindMessage = Component.literal(bindMessageText)

         ChatUtils.printChat(bindMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
      (macroArgument.then(keyArgument)).executes { describeContext: CommandContext<Any?> ->
         val describedMacro = describeContext.getArgument("macro", Macro::class.java)

         var description = "%s (%s)".format(describedMacro.name, describedMacro.keybind.displayName)
         if (!describedMacro.isToggled()) {
            val disabledColor = ChatFormatting.GRAY
            description = disabledColor.toString() + description
         }

         description = "Macro: " + description
         val descriptionMessage = Component.literal(description)

         ChatUtils.printChat(descriptionMessage, ChatUtils.DEFAULT_SIGNATURE)
         1
      }
   }

   private fun importMacro(context: CommandContext<Any?>): Int {
      val keyboard = ClientAccess.collectiveMinecraft.keyboardHandler
      val clipboard = keyboard.getClipboard()

      try {
         val decodedJson = String(Base64.getDecoder().decode(clipboard.toByteArray()))
         val config = JsonParser.parseString(decodedJson) as JsonObject

         if (!JsonUtils.hasProperties(config, "name", "type", "key", "mouse", "commands")) {
            throw RuntimeException()
         }

         val typeName = config.get("type")!!.getAsString()
         val type = MacroType.fromName(typeName)!!

         val name = config.get("name")!!.getAsString()

         val keyCode = config.get("key")!!.getAsInt()

         val mouse = config.get("mouse")!!.getAsBoolean()
         val keybind = Keybind.toggle(
            keyCode, mouse
         )
         val macro = type.create(name, keybind)

         val commandsJson = config.get("commands")!!.getAsJsonArray()

         for (commandElement in commandsJson) {
            macro.commands.add(commandElement.getAsString())
         }

         if (type == MacroType.HOLD) {
            while (true) {
               val holdCommandCount = macro.commands.size

               if (holdCommandCount <= 2) {
                  break
               }
               macro.commands.removeLast()
            }
         }

         val existingMacro = ClientContext.macroRegistry.findFirst(Predicate<Macro> { candidateMacro ->
            candidateMacro.name.equals(macro.name, ignoreCase = true)
         }) as Optional<Macro>
         existingMacro.ifPresent { existingMacroEntry ->
            ClientContext.macroRegistry.unregisterEntry(existingMacroEntry)
         }
         ClientContext.macroRegistry.registerEntry(macro)

         var message: MutableComponent = Component.literal("Successfully loaded ")
         val nameForMessage = macro.name
         val nameComponent = Component.literal(nameForMessage)
         val nameColor = ChatFormatting.GRAY
         val namedMessage = message.append(nameComponent.withStyle(nameColor))

         val completedMessage = namedMessage.append(" config from your clipboard")

         ChatUtils.printChat(completedMessage, ChatUtils.DEFAULT_SIGNATURE)
      } catch (importFailure: Throwable) {
         val errorMessage = Component.literal("Failed to load macro from your clipboard")

         ChatUtils.printChat(errorMessage, ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID)
      }

      return 1
   }

   companion object {
      // Разделитель команд в макросе — см. Macro.executeCommands.
      private const val COMMAND_SEPARATOR = ';'

      private fun isCommandPrefix(character: Char): Boolean {
         return character == CommandRegistry.getPrefixCharacter()
      }
   }
}
