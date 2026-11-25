package me.collective.client.settings.options

import me.collective.client.modules.Keybind
import me.collective.client.modules.misc.Macro
import me.collective.client.utils.ElapsedTimer

class DoubleTapMacro(name: String, keybind: Keybind) : Macro(name, MacroType.DOUBLE_TAP, keybind) {
    private val tapTimer = ElapsedTimer()

    override fun run() {
        if (tapTimer.hasElapsed(300L)) {
            tapTimer.reset()
        } else {
            for (command in commands) {
                Macro.executeCommands(command as String)
            }
            tapTimer.clear()
        }
    }
}
