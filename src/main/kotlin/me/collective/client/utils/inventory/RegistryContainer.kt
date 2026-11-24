package me.collective.client.utils.inventory

interface RegistryContainer {
    fun register(entry: Any): Boolean

    fun unregister(entry: Any): Boolean

    fun getRegistry(): Collection<*>
}
