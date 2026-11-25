package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.InputPressEvent
import me.collective.client.modules.Keybind
import me.collective.client.modules.Module
import me.collective.client.utils.KeyUtils
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.util.function.Function

class BindCommand : Command("bind") {
    private var pendingModule: Module? = null

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        var branchBuilder = literal<Any?>("set")

        val moduleArgument = argument("module", ModuleArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val keyBuilder = argument("key", KeybindArgumentType()) as RequiredArgumentBuilder<Any?, *>

        val setAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val module = context.getArgument("module", Module::class.java)
            val keybind = context.getArgument("key", Keybind::class.java)
            applyKeybind(module, keybind)
            1
        }
        val setKeyAction = keyBuilder.executes(setAction)
        val captureKeyActionBuilder = moduleArgument.then(setKeyAction) as RequiredArgumentBuilder<Any?, *>

        val captureAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            pendingModule = context.getArgument("module", Module::class.java)
            ClientAccess.eventBus.subscribe(this)

            val prompt = Component.literal("Press a key")
            ChatUtils.printChat(prompt, ChatUtils.createSignature(-1))
            1
        }
        val captureKeyAction = captureKeyActionBuilder.executes(captureAction)
        val setCommand = branchBuilder.then(captureKeyAction)
        var configuredRoot = root.then(setCommand) as LiteralArgumentBuilder<Any?>

        branchBuilder = literal("clear")

        val clearModuleActionBuilder = argument("module", ModuleArgumentType()) as RequiredArgumentBuilder<Any?, *>
        val clearAction = { context: com.mojang.brigadier.context.CommandContext<Any?> ->
            val module = context.getArgument("module", Module::class.java)
            module.modifyKeybind(Function { binding -> binding.withCode(-1) })

            val message = Component.literal("Bind for ")
                .append(formatText(module.name))
                .append(" has been reset")

            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
            1
        }
        val clearModuleAction = clearModuleActionBuilder.executes(clearAction)
        branchBuilder = branchBuilder.then(clearModuleAction) as LiteralArgumentBuilder<Any?>

        val clearAllActionBuilder = literal<Any?>("all")
        val clearAllCallback = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            for (module in (ClientContext.moduleRegistry.getRegistry() as Collection<Module>)) {
                module.modifyKeybind { binding -> binding.withCode(-1) }
            }

            val message = Component.literal("Bind for all modules has been reset")
            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
            1
        }
        val clearAllAction = clearAllActionBuilder.executes(clearAllCallback)
        val clearCommand = branchBuilder.then(clearAllAction)
        configuredRoot = configuredRoot.then(clearCommand) as LiteralArgumentBuilder<Any?>

        val listActionBuilder = literal<Any?>("list")
        val listCallback = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            PresetCommand.reportPresetBinds()
            1
        }
        val listAction = listActionBuilder.executes(listCallback)
        configuredRoot.then(listAction)
    }

    private fun applyKeybind(module: Module, keybind: Keybind) {
        module.modifyKeybind { previousBinding ->
            previousBinding.withCode(keybind.code).withMouse(keybind.mouse)
        }

        val capturedKeyName = KeyUtils.getKeyName(keybind.code, keybind.mouse)
        val message = Component.literal("Bind for ")
            .append(formatText(module.name))
            .append(" has been set to ")
            .append(formatText(capturedKeyName))

        ChatUtils.printChat(message, ChatUtils.createSignature(-1))
    }

    @EventHandler
    private fun onInputPress(event: InputPressEvent) {
        val moduleForBind = pendingModule ?: return
        val currentKeybind = moduleForBind.keybind
        val keybind = Keybind(event.code, currentKeybind.mode, event.isMouse)
        applyKeybind(moduleForBind, keybind)
        pendingModule = null
        ClientAccess.eventBus.unsubscribe(this)
    }
}
