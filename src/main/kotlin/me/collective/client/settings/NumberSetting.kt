package me.collective.client.settings

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import java.util.function.Predicate

class NumberSetting<N : Number> : Setting<N>, CyclingTextSetting {
    var unit: String = ""

    @JvmOverloads
    constructor(
        name: String,
        defaultValue: N,
        minimumValue: N,
        maximumValue: N,
        visibility: Predicate<in N>? = null
    ) : super(name, defaultValue, minimumValue, maximumValue, visibility)

    override fun matchesText(text: String): Boolean {
        return try {
            val currentValue = this.getValue().toDouble()
            java.lang.Double.compare(currentValue, text.toDouble()) == 0
        } catch (_: NumberFormatException) {
            false
        }
    }

    override fun fromJson(json: JsonElement) {
        try {
            when (this.getValue()) {
                is Double -> this.setNumber(json.asDouble)
                is Int -> this.setNumber(json.asInt)
                is Float -> this.setNumber(json.asFloat)
                is Long -> this.setNumber(json.asLong)
                else -> this.setNumber(json.asDouble)
            }
        } catch (ignored: Exception) {
        }
    }

    override fun toJson(): JsonElement {
        return JsonPrimitive(this.getValue())
    }

    override fun setValueFromText(text: String) {
        try {
            when (this.getValue()) {
                is Double -> this.setNumber(text.toDouble())
                is Int -> this.setNumber(text.toInt())
                is Float -> this.setNumber(text.toFloat())
            }
        } catch (_: NumberFormatException) {
        }
    }

    var number: Number
        @JvmName("getNum") get() = getValue()
        @JvmName("setNum") set(v) = setNumber(v)

    fun setNumber(number: Number) {
        super.setValue(this.convertToValueType(number))
    }

    @Suppress("UNCHECKED_CAST")
    private fun convertToValueType(number: Number): N {
        val converted: Number = when (this.getValue()) {
            is Double -> number.toDouble()
            is Int -> number.toInt()
            is Float -> number.toFloat()
            else -> number.toLong()
        }
        return converted as N
    }

    override fun parseValue(text: String) {
        super<CyclingTextSetting>.parseValue(text)
    }

    override fun setValue(value: N) {
        this.setNumber(value)
    }

    fun withUnit(unit: String): NumberSetting<N> {
        this.unit = unit
        return this
    }

    override fun markVisibilityDependency(): NumberSetting<N> = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): NumberSetting<N> = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): NumberSetting<N> = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): NumberSetting<N> = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): NumberSetting<N> = apply { super.forceOpaqueColor() }
}
