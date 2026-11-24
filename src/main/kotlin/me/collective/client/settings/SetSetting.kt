package me.collective.client.settings

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import java.util.function.Predicate

abstract class SetSetting<E> : Setting<@JvmSuppressWildcards Set<E>> {
    var lastChangedEntry: E? = null
        @JvmName("getLastChangedEntryField") get
        internal set

    fun getLastChangedEntry(): E? = this.lastChangedEntry

    val formattedValues: Set<String>
        @JvmName("getFormattedValuesProp") get() = getFormattedValues()

    @SafeVarargs
    constructor(name: String, vararg defaultEntries: E) : super(name, ObjectOpenHashSet()) {
        this.initializeEntries(*defaultEntries)
    }

    @SafeVarargs
    constructor(name: String, visibility: Predicate<in Set<E>>?, vararg defaultEntries: E) : super(
        name,
        ObjectOpenHashSet(),
        visibility
    ) {
        this.initializeEntries(*defaultEntries)
    }

    private fun initializeEntries(vararg defaultEntries: E) {
        val initialValues = ObjectOpenHashSet<E>()
        this.setValue(initialValues)
        (this.getDefaultValue() as MutableSet<E>).addAll(defaultEntries)
        (this.getValue() as MutableSet<E>).addAll(defaultEntries)
    }

    fun getFormattedValues(): Set<String> {
        val formattedValues = ObjectOpenHashSet<String>()
        val values = this.getValue()
        for (entry in values) {
            val formattedEntry = this.formatEntry(entry)
            if (formattedEntry != null) {
                formattedValues.add(formattedEntry)
            }
        }
        return formattedValues
    }

    override fun reset() {
        val values = this.getValue() as MutableSet<E>
        values.clear()
        val defaults = this.getDefaultValue()
        values.addAll(defaults)
    }

    fun clear() {
        (this.getValue() as MutableSet<E>).clear()
        this.runChangeCallback()
    }

    abstract fun parseEntry(text: String): E?

    fun removeEntry(entry: E?) {
        if (entry != null) {
            val values = this.getValue() as MutableSet<E>
            values.remove(entry)
            this.lastChangedEntry = entry
            this.runChangeCallback()
        }
    }

    abstract fun getAvailableEntries(): Collection<E>

    override fun toJson(): JsonElement {
        val json = JsonArray()
        val values = this.getValue()
        for (entry in values) {
            val formattedEntry = this.formatEntry(entry)
            json.add(formattedEntry)
        }
        return json
    }

    fun containsEntry(entry: Any?): Boolean {
        if (entry == null) {
            return false
        }
        val values = this.getValue()
        return (values as Collection<Any?>).contains(entry)
    }

    fun removeParsedEntry(text: String?) {
        if (text != null) {
            val entry = this.parseEntry(text)
            this.removeEntry(entry)
        }
    }

    fun containsParsedEntry(text: String?): Boolean {
        if (text == null) {
            return false
        }
        val entry = this.parseEntry(text)
        return this.containsEntry(entry)
    }

    abstract fun formatEntry(entry: E): String?

    override fun parseValue(text: String) {
        val entry = this.parseEntry(text) ?: throw IllegalArgumentException()
        if (this.containsEntry(entry)) {
            this.removeEntry(entry)
        } else {
            this.addEntry(entry)
        }
    }

    fun addEntry(entry: E?) {
        if (entry != null) {
            val values = this.getValue() as MutableSet<E>
            values.add(entry)
            this.lastChangedEntry = entry
            this.runChangeCallback()
        }
    }

    val formattedAvailableEntries: Collection<String>
        @JvmName("getFormattedAvailableEntriesProp") get() = getFormattedAvailableEntries()

    fun getFormattedAvailableEntries(): Collection<String> {
        val formattedEntries = HashSet<String>()
        val availableEntries = this.getAvailableEntries()
        for (entry in availableEntries) {
            val formattedEntry = this.formatEntry(entry)
            if (formattedEntry != null) {
                formattedEntries.add(formattedEntry)
            }
        }
        return formattedEntries
    }

    fun addParsedEntry(text: String?) {
        if (text != null) {
            val entry = this.parseEntry(text)
            this.addEntry(entry)
        }
    }

    override fun markVisibilityDependency(): SetSetting<E> = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): SetSetting<E> = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): SetSetting<E> = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): SetSetting<E> = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): SetSetting<E> = apply { super.forceOpaqueColor() }
}
