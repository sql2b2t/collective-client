package me.collective.client.render.text

import me.collective.client.utils.inventory.CharacterConsumer
import java.io.InputStream
import java.nio.ByteBuffer

class DeltaEncodedCharacterRange(
    private val baseCodePoint: Int,
    input: InputStream?,
) : CharacterRange {
    private val deltas: ShortArray

    init {
        try {
            val bytes = input!!.readAllBytes()
            deltas = readShorts(bytes)
        } catch (_: Throwable) {
            throw RuntimeException("Couldn't accumulate character range.")
        }
    }

    override fun forEachCharacter(consumer: CharacterConsumer) {
        var codePoint = baseCodePoint
        for (delta in deltas) {
            codePoint += delta
            consumer.accept(Char(codePoint))
        }
    }

    private companion object {
        fun readShorts(bytes: ByteArray): ShortArray {
            val shorts = ByteBuffer.wrap(bytes).asShortBuffer()
            val values = ShortArray(shorts.remaining())
            shorts.get(values)
            return values
        }
    }
}
