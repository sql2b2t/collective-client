package me.collective.client.settings.options

import me.collective.client.modules.Keybind
import me.collective.client.modules.misc.Macro

class SimpleMacro(name: String, keybind: Keybind) : Macro(name, MacroType.SIMPLE, keybind) {
    override fun run() {
        for (command in commands) {
            Macro.executeCommands(command as String)
        }
    }
}
