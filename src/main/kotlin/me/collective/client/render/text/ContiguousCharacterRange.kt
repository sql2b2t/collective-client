package me.collective.client.render.text

import me.collective.client.utils.inventory.CharacterConsumer

class ContiguousCharacterRange(
    private val firstCodePoint: Int,
    private val lastCodePoint: Int,
) : CharacterRange {
    override fun forEachCharacter(consumer: CharacterConsumer) {
        for (codePoint in firstCodePoint..lastCodePoint) {
            consumer.accept(Char(codePoint))
        }
    }
}
