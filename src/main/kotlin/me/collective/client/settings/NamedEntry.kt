package me.collective.client.settings

import net.minecraft.network.chat.Component

interface NamedEntry {
    fun getName(): String

    fun withName(name: String): NamedEntry {
        return NamedEntry { name }
    }

    fun getDisplayComponent(): Component {
        return Component.literal(this.getName())
    }

    companion object {
        inline operator fun invoke(crossinline nameSupplier: () -> String): NamedEntry =
            object : NamedEntry {
                override fun getName(): String = nameSupplier()
            }
    }
}

val NamedEntry.name: String
    get() = getName()

val NamedEntry.displayComponent: Component
    get() = getDisplayComponent()
