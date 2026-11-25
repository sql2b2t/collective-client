package me.collective.client.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientContext
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.client.UIModule
import me.collective.client.utils.chat.ChatUtils
import net.minecraft.network.chat.Component
import java.io.File

class HelpCommand : Command("help") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>

        val clientDirectory = File("collective")
        if (!clientDirectory.exists()) {
            val scheduler = ClientContext.taskScheduler
            val welcomeAction = Runnable {
                val welcomeMessage = Component.literal("Welcome to Collective!")
                ChatUtils.printChat(welcomeMessage, ChatUtils.createSignature(-1001))

                val uiKeybind = UIModule.INSTANCE.keybind
                val keybindName = uiKeybind.displayName
                val uiMessage = Component.literal("The UI bind is ").append(formatText(keybindName))
                ChatUtils.printChat(uiMessage, ChatUtils.createSignature(-1002))

                val commandPrefix = CommandRegistry.getPrefix()
                val prefixMessage = Component.literal("The command prefix is ").append(formatText(commandPrefix))
                ChatUtils.printChat(prefixMessage, ChatUtils.createSignature(-1003))
            }
            scheduler.scheduleRotationTask(welcomeAction, 0)
        }

        val helpAction = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            val commandNames = ArrayList<String>()
            val registry = ClientContext.commandRegistry
            for (command in registry.getRegistry()) {
                commandNames.add(command.name)
            }
            val message = Component.empty()

            val heading = "Commands (%d): ".format(commandNames.size)
            message.append(heading)

            val formattedNames = commandNames.joinToString(", ") + "."
            message.append(Component.nullToEmpty(formattedNames))

            ChatUtils.printChat(message, ChatUtils.createSignature(-1))
            1
        }
        root.executes(helpAction)
    }
}
