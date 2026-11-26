package me.collective.client.managers

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.collective.client.settings.NamedEntry

open class Registry<T : NamedEntry> : AbstractRegistry<T>(ObjectArrayList()) {

    override fun register(entry: Any): Boolean = registerEntry(entry as? NamedEntry)

    override fun unregister(entry: Any): Boolean = unregisterEntry(entry as? NamedEntry)

    @Suppress("UNCHECKED_CAST")
    open fun registerEntry(entry: NamedEntry?): Boolean {
        if (entry == null) {
            return false
        }
        for (registeredEntry in entries) {
            if (registeredEntry != null && registeredEntry.getName() == entry.getName()) {
                return false
            }
        }
        entries.add(entry as T)
        return true
    }

    open fun getByClass(entryClass: Class<*>): NamedEntry? =
        findFirstOrNull { candidateEntry -> candidateEntry.javaClass == entryClass }

    open fun unregisterEntry(entry: NamedEntry?): Boolean {
        if (entry == null) {
            return false
        }
        entries.remove(entry)
        return true
    }
}
