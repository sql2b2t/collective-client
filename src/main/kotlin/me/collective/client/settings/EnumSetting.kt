package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import me.collective.client.config.EnumJsonConverter
import java.util.function.Predicate

class EnumSetting<E> : Setting<E>, CyclingTextSetting where E : Enum<E>, E : NamedEntry {
    private val converter: EnumJsonConverter<E>

    @JvmOverloads
    constructor(
        name: String,
        defaultValue: E,
        visibility: Predicate<in E>? = null
    ) : super(name, defaultValue, visibility) {
        this.converter = EnumJsonConverter(defaultValue.declaringJavaClass)
        if (defaultValue !is NamedEntry) {
            throw IllegalArgumentException()
        }
    }

    override fun matchesText(text: String): Boolean {
        return this.toJson().asString.equals(text, ignoreCase = true)
    }

    override fun fromJson(json: JsonElement) {
        this.setValue(this.converter.fromJson(json))
    }

    override fun toJson(): JsonElement {
        return this.converter.toJson(this.getValue())
    }

    override fun setValueFromText(text: String) {
        this.fromJson(JsonPrimitive(text))
    }

    override fun parseValue(text: String) {
        super<CyclingTextSetting>.parseValue(text)
    }

    override fun markVisibilityDependency(): EnumSetting<E> = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): EnumSetting<E> = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): EnumSetting<E> = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): EnumSetting<E> = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): EnumSetting<E> = apply { super.forceOpaqueColor() }
}
