package me.collective.client.bots

enum class BotTaskStatus(val displayName: String) {
    IDLE("Афк"),
    AFK("Афк"),
    WARDEN("Авто варден"),
    AUTOBUY("Авто скупка");

    fun next(): BotTaskStatus = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromString(name: String): BotTaskStatus {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IDLE
        }
    }
}
