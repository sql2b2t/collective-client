package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import java.util.Objects

enum class ListFilterMode(private val displayName: String) : NamedEntry {
    ANY("Any"),
    WHITELIST("WhiteList"),
    BLACKLIST("BlackList");

    override fun getName(): String = displayName

    fun isFiltering(): Boolean = !isAny()

    fun isAny(): Boolean = this === ANY

    companion object {
        @JvmStatic
        fun matches(mode: ListFilterMode, value: Any?, setting: Setting<*>): Boolean {
            Objects.requireNonNull(mode)
            return matches(mode, value, setting.value as Collection<*>)
        }

        @JvmStatic
        fun matches(mode: ListFilterMode, value: Any?, entries: Collection<*>): Boolean = when (mode) {
            ANY -> true
            WHITELIST -> entries.contains(value)
            BLACKLIST -> !entries.contains(value)
        }
    }
}
