package me.collective.client.gui.panels

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.components.ButtonComponent
import me.collective.client.gui.components.GridGameComponent
import me.collective.client.gui.components.MinesweeperGameComponent
import me.collective.client.gui.components.SnakeGameComponent
import me.collective.client.gui.components.TetrisGameComponent
import me.collective.client.settings.options.GameType
import me.collective.client.settings.options.MinesweeperDifficulty

class GamePanel : Panel("Game") {
    private var game: GridGameComponent = TetrisGameComponent(this, 0)
    private var gameType: GameType = GameType.TETRIS
    private var gameReplacementPending: Boolean = false

    init {
        this.expansionAnimation.value = 0.0f
        this.isExpanded = false
        this.disableHeightLimit()
        this.registerChild(this.game)
        this.registerChild(
            object : ButtonComponent(this, { this@GamePanel.gameType.name }) {
                override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
                    super.mouseClicked(mouseX, mouseY, button)
                    if (this.isMouseOver(mouseX, mouseY) && button == 0) {
                        val values = GameType.values()
                        val next = values[(this@GamePanel.gameType.ordinal + 1) % values.size]
                        this@GamePanel.switchGame(next)
                    }
                }
            }
        )
    }

    fun switchGame(gameType: GameType) {
        val newGame: GridGameComponent = when (gameType) {
            GameType.TETRIS -> TetrisGameComponent(this, 0)
            GameType.SNAKE -> SnakeGameComponent(this, 0)
            GameType.MINESWEEPER -> MinesweeperGameComponent(this, MinesweeperDifficulty.EASY)
        }
        this.switchGame(gameType, newGame)
    }

    fun switchGame(gameType: GameType, game: GridGameComponent) {
        this.gameType = gameType
        this.game = game
        this.gameReplacementPending = true
    }

    override fun render(matrices: PoseStack, mouseX: Double, mouseY: Double) {
        super.render(matrices, mouseX, mouseY)
        if (this.gameReplacementPending) {
            synchronized(this.children) {
                this.children[0] = this.game
            }
            this.gameReplacementPending = false
        }
    }

    override fun getWidth(): Int {
        val currentGame = this.game
        return if (currentGame is MinesweeperGameComponent) {
            currentGame.preferredWidth
        } else {
            110
        }
    }
}
