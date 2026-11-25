package me.collective.client.events

import net.minecraft.client.GuiMessage.Line
import net.minecraft.client.GuiMessageTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MessageSignature

class ChatMessageEvent private constructor(
    val stage: EventStage,
    val tag: GuiMessageTag?,
    line: Line?,
    signature: MessageSignature?,
    message: Component?
) : CancellableEvent() {
    var line: Line? = line
    var signature: MessageSignature? = signature
    var message: Component? = message

    val originalMessage: Component?
    val originalText: String?

    init {
        if (message != null) {
            this.originalMessage = Component.empty().append(message)
            this.originalText = message.getString()
        } else {
            this.originalMessage = null
            this.originalText = null
        }
    }

    constructor(tag: GuiMessageTag?, signature: MessageSignature?, message: Component?) :
        this(EventStage.Pre, tag, null, signature, message)

    constructor(line: Line) : this(EventStage.Post, null, line, null, null)
}
