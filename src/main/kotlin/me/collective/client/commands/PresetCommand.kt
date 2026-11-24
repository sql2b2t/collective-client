package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.config.LegacyBranding
import me.collective.client.config.PresetData
import me.collective.client.config.PresetUndoAction
import me.collective.client.gui.screens.PresetScreen
import me.collective.client.managers.ConfigurationManager
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.text.ReverseStringFormatter
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.chat.ChatUtils

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils

class PresetCommand : Command("preset") {
   init {
      this.setAliases("config", "cfg")
   }

   @Suppress("UNCHECKED_CAST")
   override fun exec(root: LiteralArgumentBuilder<*>) {
      registerPresetCommands(this, root as LiteralArgumentBuilder<Any?>)
   }

   private fun joinSelectedModuleNames(matchesModule: Predicate<Module>): String {
      val moduleRegistry = ClientContext.moduleRegistry
      val moduleNames = (moduleRegistry.getRegistry() as List<Module>).stream()
         .filter(matchesModule)
         .map { module -> module.name }
         .collect(Collectors.joining(", "))

      return moduleNames
   }

   private fun loadMatchingModules(preset: PresetData, matchesModule: Predicate<Module>) {
      val moduleRegistry = ClientContext.moduleRegistry
      val modules = (moduleRegistry.getRegistry() as List<Module>).iterator()

      while (modules.hasNext()) {
         val module = modules.next()
         if (module !is HudWidgetModule && matchesModule.test(module)) {
            try {
               val presetContents = preset.contents
               val contentsObject = presetContents.getAsJsonObject()
               val moduleConfigName = module.getConfigName()
               val moduleConfig = LegacyBranding.findModuleConfig(contentsObject, moduleConfigName)
               if (moduleConfig != null) {
                  module.fromJson(moduleConfig)
               }
            } catch (ignoredLoadFailure: Exception) {
            }
         }
      }
   }

   private fun suggestPresetNames(
      context: CommandContext<Any?>, builder: SuggestionsBuilder
   ): CompletableFuture<Suggestions> {
      return PresetArgumentType().listSuggestions(context, builder) as CompletableFuture<Suggestions>
   }

   companion object {
      @JvmStatic
      fun reportPresetBinds() {
         val message = Component.empty()

         message.append("Preset binds: ")
         val moduleRegistry = ClientContext.moduleRegistry
         val modules = (moduleRegistry.getRegistry() as List<Module>).iterator()

         while (modules.hasNext()) {
            val module = modules.next()
            val keybind = module.getKeybind()

            if (!keybind.isUnbound()) {
               message.append("\n")
               val moduleName = module.name
               message.append(moduleName)

               message.append(" - ")
               val keybindName = keybind.getDisplayName()
               message.append(keybindName)
               message.append(" ")

               val keybindMode = keybind.mode()
               val modeName = keybindMode.getName()

               message.append(ChatUtils.bracketedText(modeName))
            }
         }

         ChatUtils.printChat(message, -2)
      }

      private fun registerPresetCommands(command: PresetCommand, root: LiteralArgumentBuilder<Any?>) {
         var categoryArgument: RequiredArgumentBuilder<Any?, *> =
            argument("category", PresetCategoryArgument())

         var operationLiteral: LiteralArgumentBuilder<Any?> = literal("save")

         val saveNameBranch: ArgumentBuilder<Any?, *> = (argument("preset", FancyStringArgument.fancyString()))
            .suggests { context: CommandContext<Any?>, builder: SuggestionsBuilder ->
               command.suggestPresetNames(context, builder)
            }
            .executes { context: CommandContext<Any?> ->
               savePreset(context)
            }
         val saveTree = operationLiteral.then(saveNameBranch)
         categoryArgument = categoryArgument.then(saveTree) as RequiredArgumentBuilder<Any?, *>

         operationLiteral = literal("delete")

         val deletePresetBranch: ArgumentBuilder<Any?, *> = argument("preset", PresetArgumentType())
            .executes { context: CommandContext<Any?> ->
               deletePreset(context)
            }
         val deleteTree = operationLiteral.then(deletePresetBranch)
         categoryArgument = categoryArgument.then(deleteTree) as RequiredArgumentBuilder<Any?, *>

         operationLiteral = literal("rename")

         var operationArgument: RequiredArgumentBuilder<Any?, *> = argument("preset", PresetArgumentType())

         val renameTargetBranch: ArgumentBuilder<Any?, *> = argument("name", StringArgumentType.string())
            .executes { context: CommandContext<Any?> ->
               renamePreset(context)
            }
         val renamePresetBranch = operationArgument.then(renameTargetBranch)
         val renameTree = operationLiteral.then(renamePresetBranch)
         categoryArgument = categoryArgument.then(renameTree) as RequiredArgumentBuilder<Any?, *>

         operationLiteral = literal("load")

         val loadPresetBranch: ArgumentBuilder<Any?, *> = argument("preset", PresetArgumentType())
            .executes { context: CommandContext<Any?> ->
               val configurationManager = ClientContext.configurationManager
               loadPreset(command, context, configurationManager)
            }
         val loadTree = operationLiteral.then(loadPresetBranch)
         categoryArgument = categoryArgument.then(loadTree) as RequiredArgumentBuilder<Any?, *>

         operationLiteral = literal("specific")

         operationArgument = argument("preset", PresetArgumentType())

         val moduleCategoryBranch: ArgumentBuilder<Any?, *> =
            argument("module_category", EnumArgumentType(ModuleCategory::class.java, "Category"))
               .executes { context: CommandContext<Any?> ->
                  val configurationManager = ClientContext.configurationManager
                  loadCategoryPreset(command, context, configurationManager)
               }
         operationArgument = operationArgument.then(moduleCategoryBranch) as RequiredArgumentBuilder<Any?, *>

         val modulesBranch: ArgumentBuilder<Any?, *> =
            argument("modules", ArrayArgumentType(ModuleArgumentType(), Module::class.java))
               .executes { context: CommandContext<Any?> ->
                  val configurationManager = ClientContext.configurationManager
                  loadSelectedModulePresets(command, context, configurationManager)
               }
         val specificTree = operationArgument.then(modulesBranch) as ArgumentBuilder<Any?, *>
         val categoryTree = operationLiteral.then(specificTree)
         categoryArgument = categoryArgument.then(categoryTree) as RequiredArgumentBuilder<Any?, *>

         val listBranch: ArgumentBuilder<Any?, *> = literal("list")
            .executes { context: CommandContext<Any?> ->
               listPresets(context)
            }
         val listTree = categoryArgument.then(listBranch) as ArgumentBuilder<Any?, *>
         var rootWithCategories: LiteralArgumentBuilder<Any?> = root.then(listTree)

         val exportLiteralBuilder: LiteralArgumentBuilder<Any?> = literal("export")

         val exportModuleBranch: ArgumentBuilder<Any?, *> = argument("module", ModuleArgumentType())
            .executes { context: CommandContext<Any?> ->
               exportModulePreset(context)
            }
         val exportTree = exportLiteralBuilder.then(exportModuleBranch)
         rootWithCategories = rootWithCategories.then(exportTree)

         val importBranch: ArgumentBuilder<Any?, *> = literal("import").executes { context: CommandContext<Any?> ->
            try {
               val keyboard = ClientAccess.collectiveMinecraft.keyboardHandler
               val clipboardConfig = keyboard.clipboard
               val configurationManager = ClientContext.configurationManager
               importModulePreset(configurationManager, clipboardConfig)
            } catch (importFailure: Exception) {
               reportImportFailure(importFailure)
            }
            return@executes 1
         }
         rootWithCategories.then(importBranch)

         val restoreBranch: ArgumentBuilder<Any?, *> = literal("restore").executes { context: CommandContext<Any?> ->
            val configurationManager = ClientContext.configurationManager
            restorePreset(command, configurationManager)
         }
         root.then(restoreBranch)
         root.then(literal("undo").executes(restoreBranch.getCommand()))
         root.executes { context: CommandContext<Any?> ->
            command.scheduleRotationTask {
               val minecraft = ClientAccess.collectiveMinecraft
               minecraft.setScreen(PresetScreen())
            }
            return@executes 1
         }
      }

      private fun savePreset(context: CommandContext<Any?>): Int {
         val presetName = context.getArgument("preset", String::class.java)

         val presetManager = PresetCategoryArgument.getManager(context, "category")
         val undoAction = PresetUndoAction.beforeSave(presetManager, presetName)

         try {
            presetManager.savePreset(presetName)
            ClientContext.configurationManager.getPresetUndoState().push(undoAction)
         } catch (saveFailure: IOException) {
            saveFailure.printStackTrace()
         }

         val savedMessage = "Preset " + presetName + " has been saved."
         val savedMessageComponent = Component.literal(savedMessage)

         ChatUtils.printChat(savedMessageComponent, ChatUtils.createSignature(-1))
         return 1
      }

      private fun deletePreset(context: CommandContext<Any?>): Int {
         val preset = PresetArgumentType.getPreset(context, "category", "preset")

         val presetManager = PresetCategoryArgument.getManager(context, "category")

         val presetName = preset.name

         val undoAction = PresetUndoAction.beforeDelete(presetManager, presetName)
         if (presetManager.deletePreset(presetName)) {
            ClientContext.configurationManager.getPresetUndoState().push(undoAction)
         }

         val deletedMessage = "Preset " + preset.name + " has been deleted."
         val deletedMessageComponent = Component.literal(deletedMessage)

         ChatUtils.printChat(deletedMessageComponent, ChatUtils.createSignature(-1))
         return 1
      }

      private fun renamePreset(context: CommandContext<Any?>): Int {
         val preset = PresetArgumentType.getPreset(context, "category", "preset")

         val presetManager = PresetCategoryArgument.getManager(context, "category")

         val oldName = preset.name

         val newName = context.getArgument("name", String::class.java)

         val undoAction = PresetUndoAction.beforeRename(presetManager, oldName, newName)
         if (presetManager.renamePreset(oldName, newName)) {
            ClientContext.configurationManager.getPresetUndoState().push(undoAction)
         }

         val reportedOldName = preset.name

         val renamedMessage = "The name of " + reportedOldName + " has been set to " +
            context.getArgument("name", String::class.java) + "."
         val renamedMessageComponent = Component.literal(renamedMessage)

         ChatUtils.printChat(renamedMessageComponent, ChatUtils.createSignature(-1))
         return 1
      }

      private fun loadPreset(
         owner: PresetCommand, context: CommandContext<Any?>, configurationManager: ConfigurationManager
      ): Int {
         val category = context.getArgument("category", PresetCategory::class.java)

         val preset = PresetArgumentType.getPreset(context, "category", "preset")

         val presetManager = PresetCategoryArgument.getManager(context, "category")
         val undoAction = PresetUndoAction.beforeLoad(preset.name)

         presetManager.loadPresets()

         val presetName = preset.name

         if (presetManager.applyPreset(presetName)) {
            configurationManager.getPresetUndoState().push(undoAction)
         }

         val loadedMessage = "Preset " + preset.name + " has been loaded."
         val loadedMessageComponent = Component.literal(loadedMessage)

         ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1))
         if (category == PresetCategory.ALL) {
            owner.scheduleRotationTask { reportPresetBinds() }
         }

         return 1
      }

      private fun loadCategoryPreset(
         owner: PresetCommand, context: CommandContext<Any?>, configurationManager: ConfigurationManager
      ): Int {
         val preset = PresetArgumentType.getPreset(context, "category", "preset")

         val presetManager = PresetCategoryArgument.getManager(context, "category")

         val moduleCategory = context.getArgument("module_category", ModuleCategory::class.java)

         presetManager.loadPresets()
         val undoAction = PresetUndoAction.beforeLoad(preset.name)
         owner.loadMatchingModules(preset, Predicate<Module> { candidate ->
            candidate.category == moduleCategory
         })
         configurationManager.getPresetUndoState().push(undoAction)

         val presetName = preset.name

         val loadedMessage = "Preset " + presetName + " for " + moduleCategory.getName() + " has been loaded."
         val loadedMessageComponent = Component.literal(loadedMessage)

         ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1))
         return 1
      }

      private fun loadSelectedModulePresets(
         owner: PresetCommand, context: CommandContext<Any?>, configurationManager: ConfigurationManager
      ): Int {
         val preset = PresetArgumentType.getPreset(context, "category", "preset")

         val presetManager = PresetCategoryArgument.getManager(context, "category")

         val modules = context.getArgument("modules", Array<Module>::class.java)
         val moduleClasses: Set<Class<*>> = modules.map { module -> module.javaClass }.toSet()

         presetManager.loadPresets()
         val matchesSelectedModule = Predicate<Module> { candidate -> moduleClasses.contains(candidate.javaClass) }
         val undoAction = PresetUndoAction.beforeLoad(preset.name)
         owner.loadMatchingModules(preset, matchesSelectedModule)
         configurationManager.getPresetUndoState().push(undoAction)

         val presetName = preset.name

         val loadedMessage = "Preset " + presetName + " for " +
            owner.joinSelectedModuleNames(matchesSelectedModule) + " has been loaded."
         val loadedMessageComponent = Component.literal(loadedMessage)

         ChatUtils.printChat(loadedMessageComponent, ChatUtils.createSignature(-1))
         return 1
      }

      private fun listPresets(context: CommandContext<Any?>): Int {
         val presetManager = PresetCategoryArgument.getManager(context, "category")

         var listMessage = Component.literal("Preset list: ")
         listMessage = listMessage.append(
            ComponentUtils.formatList(
               presetManager.getRegistry() as List<PresetData>,
               Function<PresetData, Component> { preset ->
                  val presetName = preset.name
                  Component.literal(presetName)
               }
            )
         )
         listMessage = listMessage.append(".")

         ChatUtils.printChat(listMessage, ChatUtils.createSignature(-1))
         return 1
      }

      private fun exportModulePreset(context: CommandContext<Any?>): Int {
         val module = context.getArgument("module", Module::class.java)
         val configurationManager = ClientContext.configurationManager
         val moduleConfigCodec = configurationManager.getModulePresetCodec()
         val keyboard = ClientAccess.collectiveMinecraft.keyboardHandler

         val encodedModuleConfig = moduleConfigCodec.encode(module)
         keyboard.setClipboard(encodedModuleConfig)

         val messagePrefix = Component.literal("Successfully copied ")
         val moduleName = module.name
         val moduleNameComponent = Component.literal(moduleName)
         val messageWithModuleName = messagePrefix.append(moduleNameComponent.withStyle(ChatFormatting.GRAY))

         val exportedMessage = messageWithModuleName.append(" config to your clipboard")

         ChatUtils.printChat(exportedMessage, ChatUtils.createSignature(-1))
         return 1
      }

      private fun importModulePreset(configurationManager: ConfigurationManager, clipboardConfig: String) {
         val moduleConfigCodec = configurationManager.getModulePresetCodec()
         val decodedConfig = moduleConfigCodec.decode(clipboardConfig)
         val module = decodedConfig.module
         val moduleConfig = decodedConfig.json
         module.fromJson(moduleConfig)

         val messagePrefix = Component.literal("Successfully loaded ")
         val moduleName = module.name
         val moduleNameComponent = Component.literal(moduleName)
         val messageWithModuleName = messagePrefix.append(moduleNameComponent.withStyle(ChatFormatting.GRAY))

         val importedMessage = messageWithModuleName.append(" config from your clipboard")

         ChatUtils.printChat(importedMessage, ChatUtils.createSignature(-1))
      }

      private fun reportImportFailure(importFailure: Exception) {
         var errorMessage = Component.literal("Failed to load module config")
         val failureLocation = importFailure.stackTrace[0]
         val failureLocationText = failureLocation.toString()

         if (!failureLocationText.contains("Base64") && importFailure !is IllegalArgumentException) {
            errorMessage = errorMessage.append(" due to an unknown reason")
         } else {
            errorMessage = errorMessage.append(" due to invalid input")
         }

         ChatUtils.printChat(errorMessage, ChatUtils.createSignature(-1), ChatUtils.MessagePriority.MID)
         importFailure.printStackTrace()
      }

      private fun restorePreset(owner: PresetCommand, configurationManager: ConfigurationManager): Int {
         val restoreManager = configurationManager.getPresetUndoState()
         val action: PresetUndoAction?
         try {
            action = restoreManager.undo()
         } catch (undoFailure: IOException) {
            undoFailure.printStackTrace()
            ChatUtils.printChat(
               Component.literal("Failed to undo preset change."),
               ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID
            )
            return 1
         }
         if (action == null) {
            ChatUtils.printChat(
               Component.literal("Nothing to undo."),
               ChatUtils.DEFAULT_SIGNATURE, ChatUtils.MessagePriority.MID
            )
            return 1
         }

         owner.scheduleRotationTask {
            ChatUtils.printChat(Component.literal(formatUndoAction(action)), ChatUtils.DEFAULT_SIGNATURE)
         }
         return 1
      }

      private fun formatUndoAction(action: PresetUndoAction): String {
         return when (action) {
            is PresetUndoAction.Load -> ReverseStringFormatter().prepend(action.name)
               .format("Restored config before loading \u0001.")
            is PresetUndoAction.Save -> ReverseStringFormatter().prepend(action.name)
               .format("Restored state before saving \u0001.")
            is PresetUndoAction.Delete -> ReverseStringFormatter().prepend(action.preset.name)
               .format("Restored deleted preset \u0001.")
            is PresetUndoAction.Rename -> ReverseStringFormatter().prepend(action.newName).prepend(action.oldName)
               .format("Restored name \u0001 for \u0001.")
         }
      }
   }
}
