package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.util.function.Predicate

class StringSetting : Setting<String> {

    @JvmOverloads
    constructor(
        name: String,
        defaultValue: String,
        visibilityPredicate: Predicate<in String>? = null
    ) : super(name, defaultValue, visibilityPredicate)

    override fun fromJson(json: JsonElement) {
        if (json.isJsonPrimitive) {
            this.setValue(json.asString)
        }
    }

    override fun toJson(): JsonElement {
        return JsonPrimitive(this.getValue())
    }

    override fun parseValue(text: String) {
        this.setValue(text)
    }

    override fun markVisibilityDependency(): StringSetting = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): StringSetting = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): StringSetting = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): StringSetting = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): StringSetting = apply { super.forceOpaqueColor() }
}
