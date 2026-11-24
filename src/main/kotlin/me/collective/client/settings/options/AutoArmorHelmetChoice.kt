package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class AutoArmorHelmetChoice(private val displayName: String) : NamedEntry {
    BEST("Best"),
    TURTLE("Turtle"),
    GOLD("Golden"),
    NONE("None");

    val isNone: Boolean get() = this == NONE
    val isTurtle: Boolean get() = this == TURTLE
    val isGold: Boolean get() = this == GOLD

    override fun getName(): String = displayName
}

fun AutoArmorHelmetChoice.isNone(): Boolean = this.isNone
fun AutoArmorHelmetChoice.isTurtle(): Boolean = this.isTurtle
fun AutoArmorHelmetChoice.isGold(): Boolean = this.isGold
