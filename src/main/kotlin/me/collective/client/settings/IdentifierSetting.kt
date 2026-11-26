package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.config.JsonUtils
import me.collective.client.utils.collections.NamedIdentifier
import java.util.function.Predicate

class IdentifierSetting : Setting<NamedIdentifier> {

    @JvmOverloads
    constructor(
        name: String,
        defaultValue: NamedIdentifier,
        visibility: Predicate<in NamedIdentifier>? = null
    ) : super(name, defaultValue, visibility)

    override fun fromJson(element: JsonElement) {
        if (!element.isJsonObject) {
            return
        }
        val jsonObject = element.asJsonObject
        if (JsonUtils.hasProperties(jsonObject, "name", "category")) {
            try {
                val name = jsonObject.get("name").asString
                val category = jsonObject.get("category").asString
                val identifier = NamedIdentifier(category, name)
                this.setValue(identifier)
            } catch (ignored: Exception) {
            }
        }
    }

    override fun toJson(): JsonElement {
        val jsonObject = JsonObject()
        val identifier = this.getValue()
        jsonObject.addProperty("name", identifier.name)
        jsonObject.addProperty("category", identifier.category)
        return jsonObject
    }

    override fun parseValue(value: String) {
        val parts = value.split(":")
        if (parts.size == 1) {
            val identifier = NamedIdentifier(value)
            this.setValue(identifier)
        } else if (parts.size == 2) {
            val identifier = NamedIdentifier(parts[0], parts[1])
            this.setValue(identifier)
        }
    }

    override fun markVisibilityDependency(): IdentifierSetting = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): IdentifierSetting = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): IdentifierSetting = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): IdentifierSetting = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): IdentifierSetting = apply { super.forceOpaqueColor() }
}
