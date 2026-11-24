package me.collective.client.settings

import com.google.gson.JsonElement
import java.util.function.Consumer
import java.util.function.Predicate
import net.minecraft.core.Holder.Reference
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier

class RegistrySetSetting<E : Any> : SetSetting<E> {
    val registry: Registry<E>

    @SafeVarargs
    constructor(name: String, entryRegistry: Registry<E>, vararg defaultEntries: E) : super(name, *defaultEntries) {
        this.registry = entryRegistry
    }

    @SafeVarargs
    constructor(
        name: String,
        entryRegistry: Registry<E>,
        visibility: Predicate<in Set<E>>?,
        vararg defaultEntries: E
    ) : super(name, visibility, *defaultEntries) {
        this.registry = entryRegistry
    }

    override fun parseEntry(text: String): E? {
        val lookupRegistry = this.registry
        val identifier = Identifier.tryParse(text) ?: return null
        val holder = lookupRegistry.get(identifier)
        return holder.map(Reference<E>::value).orElse(null)
    }

    override fun getAvailableEntries(): Collection<E> {
        return this.registry.stream().toList()
    }

    override fun fromJson(json: JsonElement) {
        if (json.isJsonArray) {
            val values = this.getValue() as MutableSet<E>
            values.clear()
            val array = json.asJsonArray
            for (element in array) {
                val lookupRegistry = this.registry
                val identifierText = element.asString
                val identifier = Identifier.tryParse(identifierText) ?: continue
                val entry = lookupRegistry.getValue(identifier)
                if (entry != null) {
                    values.add(entry)
                }
            }
        }
    }

    fun modifyEntries(editor: Consumer<MutableSet<E>>) {
        editor.accept(this.getValue() as MutableSet<E>)
        this.runChangeCallback()
    }

    override fun formatEntry(entry: E): String? {
        val entryRegistry = this.registry
        val identifier = entryRegistry.getKey(entry) ?: return null
        return identifier.toShortLanguageKey()
    }

    override fun markVisibilityDependency(): RegistrySetSetting<E> = apply { super.markVisibilityDependency() }
    override fun markAsGroup(): RegistrySetSetting<E> = apply { super.markAsGroup() }
    override fun dependsOn(vararg dependencies: Setting<*>?): RegistrySetSetting<E> = apply { super.dependsOn(*dependencies) }
    override fun setBoundaryLabel(label: String, condition: SettingLabelCondition): RegistrySetSetting<E> = apply { super.setBoundaryLabel(label, condition) }
    override fun forceOpaqueColor(): RegistrySetSetting<E> = apply { super.forceOpaqueColor() }
}

fun <E : Any> RegistrySetSetting<E>.getRegistry(): Registry<E> = this.registry
