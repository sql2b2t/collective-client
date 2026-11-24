package me.collective.client.settings.options

import me.collective.client.settings.NamedEntry

enum class MinesweeperDifficulty(
    private val displayName: String,
    val columns: Int,
    val rows: Int,
    val mineCount: Int,
    val initialScore: Int
) : NamedEntry {
    EASY("Easy", 10, 10, 15, 600),
    MEDIUM("Medium", 16, 16, 40, 2400),
    HARD("Hard", 16, 30, 99, 5940);

    override fun getName(): String = displayName
}
