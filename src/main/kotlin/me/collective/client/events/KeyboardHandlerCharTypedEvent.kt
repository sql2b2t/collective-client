package me.collective.client.events

class KeyboardHandlerCharTypedEvent(val codePoint: Int) : CancellableEvent() {
    val text: String
        get() = Character.toString(codePoint)

    val character: Char
        get() = text[0]
}
