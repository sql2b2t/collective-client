package me.collective.client.bots

enum class BotRole(val displayName: String) {
    NONE("Нет"),
    BUYER("Скупщик"),
    SCOUT("Разведчик");

    companion object {
        fun fromString(name: String): BotRole {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: NONE
        }
    }
}
