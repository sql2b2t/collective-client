package me.collective.client.render.text

import me.collective.client.utils.inventory.CharacterConsumer

interface CharacterRange {
    fun addTo(characters: MutableCollection<Char>) {
        forEachCharacter { characters.add(it) }
    }

    fun forEachCharacter(consumer: CharacterConsumer)
}
