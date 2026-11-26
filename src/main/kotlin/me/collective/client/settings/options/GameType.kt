package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class GameType(private val displayName: String) : NamedEntry {
    TETRIS("Tetris"),
    SNAKE("Snake"),
    MINESWEEPER("Minesweeper");

    override fun getName(): String = displayName
}
