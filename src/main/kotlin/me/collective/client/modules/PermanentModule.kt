package me.collective.client.modules

import com.google.gson.JsonElement
import com.google.gson.JsonObject

abstract class PermanentModule(
    name: String,
    description: String,
    category: ModuleCategory,
    vararg aliases: String
) : Module(name, description, category, *aliases) {

    override fun isToggled(): Boolean = true

    override fun isDrawn(): Boolean = false

    override fun getKeybind(): Keybind = Keybind.UNBOUND

    override fun toJson(): JsonElement {
        val serialized = super.toJson()
        val properties = serialized.asJsonObject
        properties.remove("enabled")
        properties.remove("key")
        properties.remove("state")
        return properties
    }

    override fun disable() {}
}
