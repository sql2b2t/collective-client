package me.collective.client.render.text

import me.collective.client.utils.inventory.CharacterConsumer

class ExplicitCharacterRange(vararg characters: Char) : CharacterRange {
    private val characters: CharArray = characters

    override fun forEachCharacter(consumer: CharacterConsumer) {
        for (character in characters) {
            consumer.accept(character)
        }
    }
}
