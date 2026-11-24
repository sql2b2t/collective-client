package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.util.function.Predicate

class BooleanSetting : Setting<Boolean> {

    @JvmOverloads
    constructor(
        name: String,
        defaultValue: Boolean,
        visibilityPredicate: Predicate<in Boolean>? = null
    ) : super(name, defaultValue, visibilityPredicate)

    override fun fromJson(json: JsonElement) {
        if (json.isJsonPrimitive) {
            this.setValue(json.asBoolean)
        }
    }

    override fun toJson(): JsonElement {
        return JsonPrimitive(this.getValue())
    }

    override fun parseValue(text: String) {
        if ("toggle".equals(text, ignoreCase = true)) {
            this.setValue(!this.getValue())
        } else if (text == "0" || text.equals("false", ignoreCase = true)) {
            this.setValue(false)
        } else if (text == "1" || text.equals("true", ignoreCase = true)) {
            this.setValue(true)
        }
    }

    override fun markVisibilityDependency(): BooleanSetting = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): BooleanSetting = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): BooleanSetting = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): BooleanSetting = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): BooleanSetting = apply { super.forceOpaqueColor() }
}
