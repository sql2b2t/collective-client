package me.collective.client.managers

import me.collective.client.utils.inventory.RegistryContainer
import java.util.Optional
import java.util.function.Predicate

abstract class AbstractRegistry<T : Any> protected constructor(
    @JvmField
    protected val entries: MutableCollection<T>
) : RegistryContainer {

    override fun getRegistry(): MutableCollection<T> = entries

    open fun findFirst(predicate: Predicate<T>): Optional<T> {
        for (entry in entries) {
            if (predicate.test(entry)) {
                return Optional.of(entry)
            }
        }
        return Optional.empty()
    }

    open fun findFirstOrNull(predicate: Predicate<T>): T? {
        for (entry in entries) {
            if (predicate.test(entry)) {
                return entry
            }
        }
        return null
    }
}
