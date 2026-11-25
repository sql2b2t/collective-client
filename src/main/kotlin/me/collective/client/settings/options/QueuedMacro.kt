package me.collective.client.settings.options

import me.collective.client.modules.Keybind
import me.collective.client.modules.misc.Macro

class QueuedMacro(name: String, keybind: Keybind) : Macro(name, MacroType.QUEUE, keybind) {
    private var nextCommandIndex = 0

    override fun run() {
        if (commands.isEmpty()) {
            return
        }
        nextCommandIndex %= commands.size
        val commandGroup = commands[nextCommandIndex] as String
        val splitCommands = commandGroup.split(";")
        for (command in splitCommands) {
            Macro.executeCommands(command)
        }
        nextCommandIndex++
    }
}
