package me.collective.client.modules.misc

import me.collective.client.NamedFeature
import me.collective.client.managers.CommandRegistry
import me.collective.client.modules.Keybind
import me.collective.client.settings.options.MacroType
import me.collective.client.utils.chat.ChatUtils
import java.util.ArrayList
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

abstract class Macro(name: String, val type: MacroType, val keybind: Keybind) : NamedFeature(name) {
    val commands: MutableList<Any?> = ArrayList()

    private var toggled: Boolean = true

    abstract fun run()

    fun isToggled(): Boolean {
        return this.toggled
    }

    fun setToggled(toggled: Boolean) {
        this.toggled = toggled
    }

    companion object {
        @JvmStatic
        fun executeCommands(commandGroup: String) {
            for (command in commandGroup.split(";")) {
                val strippedCommand = stripCommandPrefix(command.trim())
                if (strippedCommand.isEmpty()) {
                    continue
                }
                if (strippedCommand.startsWith("/")) {
                    // Серверные команды (/kit, /server) уходят в чат, а не в клиентский диспетчер.
                    ChatUtils.sendChatOrCommand(strippedCommand)
                    continue
                }
                if (!CommandRegistry.executeCommand(strippedCommand)) {
                    val failedMessage = Component.literal(
                        ChatFormatting.RED.toString() + "Macro command failed: " + strippedCommand
                    )
                    ChatUtils.printChat(failedMessage, ChatUtils.DEFAULT_SIGNATURE)
                }
            }
        }

        private fun stripCommandPrefix(command: String): String {
            return CommandRegistry.stripPrefix(command).trim()
        }
    }
}
