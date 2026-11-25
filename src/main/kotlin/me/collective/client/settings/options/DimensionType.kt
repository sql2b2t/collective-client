package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class DimensionType(
    @get:JvmName("getConfigNameProp") val configName: String,
    @get:JvmName("getDisplayNameProp") val displayName: String
) : NamedEntry {
    OVERWORLD("overworld", "Overworld"),
    THE_NETHER("the_nether", "Nether"),
    THE_END("the_end", "End");

    fun isEnd(): Boolean = this == THE_END
    fun isOverworld(): Boolean = this == OVERWORLD
    fun isNether(): Boolean = this == THE_NETHER

    val isEnd: Boolean @JvmName("isEndProp") get() = isEnd()
    val isOverworld: Boolean @JvmName("isOverworldProp") get() = isOverworld()
    val isNether: Boolean @JvmName("isNetherProp") get() = isNether()

    fun getDisplayName(): String = displayName
    fun getConfigName(): String = configName

    override fun getName(): String = configName
}
