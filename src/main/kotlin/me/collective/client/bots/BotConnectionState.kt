package me.collective.client.bots

enum class BotConnectionState(val displayName: String, val isConnectingOrActive: Boolean) {
    IDLE("Оффлайн", false),
    DISCONNECTED("Отключен", false),
    QUEUED("В очереди", true),
    CONNECTING("Подключение", true),
    LOGGING_IN("Авторизация", true),
    CONFIGURING("Настройка", true),
    PLAYING("В игре", true),
    IN_GAME("В игре", true),
    CONTROLLED("Ты играешь", false),
    FAILED("Ошибка", false);

    val isInGame: Boolean get() = this == PLAYING || this == IN_GAME
    val isActive: Boolean get() = isConnectingOrActive
    fun isPlaying(): Boolean = isInGame
}
