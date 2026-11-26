package me.collective.client.modules.misc

import me.collective.client.modules.Keybind
import me.collective.client.settings.options.MacroType

class HoldMacro(name: String, keybind: Keybind) : Macro(name, MacroType.HOLD, keybind) {
    private var pressed: Boolean = false

    override fun run() {
    }

    fun updatePressed(pressed: Boolean) {
        if (this.pressed == pressed) {
            return
        }
        this.pressed = pressed
        if (this.commands.isEmpty()) {
            return
        }
        val command = if (this.pressed) {
            this.commands.first() as String
        } else {
            this.commands.last() as String
        }
        Macro.executeCommands(command)
    }
}
