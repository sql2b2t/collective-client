package me.collective.client.settings

import com.google.gson.JsonElement
import java.util.Locale
import java.util.function.Predicate

class StringSetSetting : SetSetting<String> {

    @SafeVarargs
    constructor(name: String, vararg defaultEntries: String) : super(name, *defaultEntries)

    @SafeVarargs
    constructor(
        name: String,
        visibility: Predicate<in Set<String>>?,
        vararg defaultEntries: String
    ) : super(name, visibility, *defaultEntries)

    override fun parseEntry(text: String): String {
        return text.lowercase(Locale.ROOT)
    }

    override fun getAvailableEntries(): Collection<String> {
        return emptyList()
    }

    override fun fromJson(json: JsonElement) {
        if (json.isJsonArray) {
            val values = this.getValue() as MutableSet<String>
            values.clear()
            val array = json.asJsonArray
            for (entry in array) {
                if (!entry.isJsonPrimitive) {
                    continue
                }
                // Normalize like parseEntry/formatEntry so lookup is case-consistent.
                values.add(entry.asString.lowercase(Locale.ROOT))
            }
        }
    }

    override fun formatEntry(entry: String): String {
        return entry.lowercase(Locale.ROOT)
    }

    override fun markVisibilityDependency(): StringSetSetting = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): StringSetSetting = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): StringSetSetting = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): StringSetSetting = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): StringSetSetting = apply { super.forceOpaqueColor() }
}
