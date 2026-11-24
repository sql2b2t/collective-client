package me.collective.client.access

import me.collective.client.utils.ClampedInterpolatedFloat
import net.minecraft.network.chat.MessageSignature
import net.minecraft.util.FormattedCharSequence

interface ChatLineAccess {
    fun setContent(content: FormattedCharSequence)

    fun setSignature(signature: MessageSignature)

    fun `collective$setAddTime`(addTime: Long)

    fun `collective$getAddTime`(): Long

    fun getProgress(): ClampedInterpolatedFloat

    fun getSignature(): MessageSignature?

    fun getContent(): FormattedCharSequence
}

val ChatLineAccess.signature: MessageSignature?
    get() = getSignature()
