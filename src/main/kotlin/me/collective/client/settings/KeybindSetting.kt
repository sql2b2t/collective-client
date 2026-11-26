package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import me.collective.client.modules.Keybind
import java.util.Locale

class KeybindSetting : Setting<Keybind> {

    constructor(name: String) : super(name, Keybind.UNBOUND)

    constructor(name: String, defaultValue: Keybind) : super(name, defaultValue)

    constructor(name: String, defaultCode: Int) : super(name, Keybind.toggle(defaultCode, false))

    override fun fromJson(json: JsonElement) {
        if (json is JsonPrimitive && json.isNumber) {
            this.setValue(Keybind.toggle(json.asInt, false))
            return
        }
        if (json !is JsonObject || !json.has("key")) {
            return
        }

        try {
            val code = json.get("key").asInt
            val mouse = json.has("mouse") && (json.get("mouse")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false)
            val mode = if (json.has("state")) {
                modeFromSerializedName(json.get("state").asString)
            } else {
                Keybind.Mode.TOGGLE
            }
            this.setValue(Keybind(code, mode, mouse))
        } catch (ignored: Exception) {
        }
    }

    override fun toJson(): JsonElement {
        val keybind = this.getValue()
        val jsonObject = JsonObject()
        jsonObject.addProperty("key", keybind.code)
        jsonObject.addProperty("state", keybind.mode.serializedName)
        jsonObject.addProperty("mouse", keybind.mouse)
        return jsonObject
    }

    override fun parseValue(text: String) {
        try {
            this.setValue(Keybind.toggle(text.trim().toInt(), false))
        } catch (_: NumberFormatException) {
        }
    }

    override fun markVisibilityDependency(): KeybindSetting = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): KeybindSetting = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): KeybindSetting = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): KeybindSetting = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): KeybindSetting = apply { super.forceOpaqueColor() }

    companion object {
        private fun modeFromSerializedName(value: String): Keybind.Mode {
            val normalized = value.trim().lowercase(Locale.ROOT)
            for (mode in Keybind.Mode.values()) {
                if (mode.serializedName == normalized) {
                    return mode
                }
            }
            return Keybind.Mode.TOGGLE
        }
    }
}
