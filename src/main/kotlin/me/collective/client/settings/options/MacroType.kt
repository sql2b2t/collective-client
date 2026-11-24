package me.collective.client.settings.options

import me.collective.client.modules.misc.HoldMacro
import me.collective.client.modules.misc.Macro
import me.collective.client.modules.Keybind
import me.collective.client.settings.NamedEntry

enum class MacroType(
    private val displayName: String,
    private val factory: (name: String, keybind: Keybind) -> Macro
) : NamedEntry {
    SIMPLE("simple", { name, keybind -> SimpleMacro(name, keybind) }),
    QUEUE("queue", { name, keybind -> QueuedMacro(name, keybind) }),
    DOUBLE_TAP("double_tap", { name, keybind -> DoubleTapMacro(name, keybind) }),
    HOLD("hold", { name, keybind -> HoldMacro(name, keybind) });

    override fun getName(): String = displayName

    fun create(name: String, keybind: Keybind): Macro = factory.invoke(name, keybind)

    companion object {
        @JvmStatic
        fun fromName(name: String?): MacroType? {
            for (type in entries) {
                if (type.getName().equals(name, ignoreCase = true)) {
                    return type
                }
            }

            return null
        }
    }
}
