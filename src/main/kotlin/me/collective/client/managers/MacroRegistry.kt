package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.config.JsonUtils
import me.collective.client.events.EventHandler
import me.collective.client.events.InputPressEvent
import me.collective.client.events.MacroTriggerEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.modules.Keybind
import me.collective.client.modules.misc.HoldMacro
import me.collective.client.modules.misc.Macro
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.options.MacroType
import me.collective.client.utils.KeyUtils

class MacroRegistry : Registry<Macro>(), ClientAccess, JsonConfigurable {

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    override fun fromJson(json: JsonElement) {
        if (!json.isJsonObject) {
            return
        }
        val rootObject = json.asJsonObject
        if (!rootObject.has("macros")) {
            return
        }
        val macroArray = rootObject.getAsJsonArray("macros")
        for (macroElement in macroArray) {
            if (!macroElement.isJsonObject) {
                continue
            }
            val macroJson = macroElement.asJsonObject
            if (!JsonUtils.hasProperties(macroJson, "name", "type", "key", "mouse", "commands")) {
                continue
            }
            try {
                val typeName = macroJson.get("type").asString
                val type = MacroType.fromName(typeName)
                if (type == null) {
                    ClientAccess.logger.warn("Unknown macro type '{}' in macros.json, skipping entry", typeName)
                    continue
                }
                val name = macroJson.get("name").asString
                val keybind = Keybind(
                    macroJson.get("key").asInt,
                    Keybind.Mode.TOGGLE,
                    macroJson.get("mouse").asBoolean
                )
                val macro = type.create(name, keybind)
                if (macroJson.has("toggled")) {
                    macro.setToggled(macroJson.get("toggled").asBoolean)
                }
                val commandsArray = macroJson.get("commands").asJsonArray
                val commandsList = macro.commands
                for (commandElement in commandsArray) {
                    commandsList.add(commandElement.asString)
                }
                if (type == MacroType.HOLD) {
                    while (commandsList.size > 2) {
                        commandsList.removeAt(commandsList.lastIndex)
                    }
                }
                registerEntry(macro)
            } catch (loadFailure: Exception) {
                // Раньше исключение молча глоталось и макрос тихо пропадал при загрузке.
                ClientAccess.logger.warn("Failed to load macro entry from macros.json, skipping it", loadFailure)
            }
        }
    }

    override fun toJson(): JsonElement {
        val macrosArray = JsonArray()
        val rootObject = JsonObject()
        for (macro in entries) {
            val macroJson = JsonObject()
            macroJson.addProperty("name", macro.getName())
            macroJson.addProperty("toggled", macro.isToggled())
            macroJson.addProperty("type", macro.type.getName())
            val keybind = macro.keybind
            macroJson.addProperty("key", keybind.code())
            macroJson.addProperty("mouse", keybind.mouse())
            val commandsArray = JsonArray()
            for (command in macro.commands) {
                if (command is String) {
                    commandsArray.add(command)
                }
            }
            macroJson.add("commands", commandsArray)
            macrosArray.add(macroJson)
        }
        rootObject.add("macros", macrosArray)
        return rootObject
    }

    override fun getConfigName(): String = "macros.json"

    @EventHandler
    private fun onInputPress(event: InputPressEvent) {
        if (ClientAccess.collectiveMinecraft.screen == null && !isWorldUnavailable()) {
            for (macro in entries) {
                if (macro.isToggled()) {
                    val keybind = macro.keybind
                    if (keybind.mouse() == event.isMouse && keybind.code() == event.code) {
                        macro.run()
                        ClientAccess.eventBus.post(MacroTriggerEvent(macro))
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onTick(event: WorldRenderEvent.Main) {
        if (ClientAccess.collectiveMinecraft.screen == null && !isWorldUnavailable()) {
            for (macro in entries) {
                if (macro is HoldMacro && macro.isToggled()) {
                    val physicallyDown = KeyUtils.isPhysicallyDown(macro.keybind)
                    macro.updatePressed(physicallyDown)
                }
            }
        }
    }
}
