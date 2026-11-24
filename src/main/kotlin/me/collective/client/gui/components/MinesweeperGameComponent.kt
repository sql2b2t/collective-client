package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat.Mode
import me.collective.client.gui.panels.GamePanel
import me.collective.client.gui.panels.Panel
import me.collective.client.managers.RenderStateManager
import me.collective.client.render.ClientRenderTypes
import me.collective.client.render.FontRenderer
import me.collective.client.render.MeshBuffer
import me.collective.client.render.MeshDrawContext
import me.collective.client.settings.options.GameType
import me.collective.client.settings.options.MinesweeperDifficulty
import me.collective.client.utils.math.FloatBounds
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import java.awt.Color
import java.util.ArrayDeque
import java.util.HashSet
import java.util.concurrent.ThreadLocalRandom

class MinesweeperGameComponent(
    panel: Panel,
    private val difficulty: MinesweeperDifficulty
) : GridGameComponent(panel, 0, difficulty.columns, difficulty.rows) {

    private var board: Array<Array<MinesweeperCell>> = Array(this.gridRows - 1) {
        Array(this.gridColumns) { MinesweeperCell(MinesweeperCellContent.NUMBER_0) }
    }
    private var boardGenerated = false
    private var won = false
    private var resetAtMillis = 0L
    private var score = 0
    private var flagCount = 0

    init {
        resetBoard()
    }

    override fun tickGame() {
        if (this.resetAtMillis >= 0L && System.currentTimeMillis() > this.resetAtMillis) {
            this.resetBoard()
        }
        if (!this.isResetPending() && this.boardGenerated) {
            this.score = Math.max(this.score - 1, 0)
        }
    }

    override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        super.render(poseStack, mouseX, mouseY)
        this.renderHeader(poseStack)
        this.renderBoard(poseStack, mouseX, mouseY)
        if (this.won) {
            this.renderWinOverlay(poseStack)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) {
        super.mouseClicked(mouseX, mouseY, button)
        if (this.isHeaderHovered(mouseX, mouseY)) {
            val nextDifficulty = MinesweeperDifficulty.values()[(this.difficulty.ordinal + 1) % MinesweeperDifficulty.values().size]
            (this.panel as GamePanel).switchGame(
                GameType.MINESWEEPER,
                MinesweeperGameComponent(this.panel, nextDifficulty)
            )
            return
        }
        if (this.isResetPending()) {
            return
        }
        for (column in 0 until this.gridColumns) {
            for (displayRow in 1 until this.gridRows) {
                if (this.isCellHovered(column, displayRow, mouseX, mouseY)) {
                    if (!this.boardGenerated) {
                        this.generateBoard(displayRow - 1, column)
                    }
                    if (button == 0) {
                        this.openCell(displayRow - 1, column)
                    } else if (button == 1) {
                        this.toggleFlag(displayRow - 1, column)
                    }
                }
            }
        }

        if (button == 0 && this.hasWon() && !this.isResetPending()) {
            this.resetAtMillis = System.currentTimeMillis() + 3000L
            this.won = true
        }
    }

    override fun getTickDelayMillis(): Int = 1000

    override val height: Int
        get() = 8 * this.gridRows

    val preferredWidth: Int @JvmName("preferredWidthProp") get() = getPreferredWidth()

    fun getPreferredWidth(): Int = 8 * this.gridColumns

    private fun renderHeader(poseStack: PoseStack) {
        val displayedScore = this.score
        FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            displayedScore.toString(),
            (this.panel.x + 1).toFloat(),
            this.panel.y + this.yOffset + 1.5f,
            0.8f,
            Color.WHITE
        )
        val remainingMines = (this.difficulty.mineCount - this.flagCount).toString()
        var textWidth = FontRenderer.INSTANCE.getWidth(remainingMines) * 0.8f
        FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            remainingMines,
            this.panel.x + this.panel.width - textWidth - this.cellWidth,
            this.panel.y + this.yOffset + 1.5f,
            0.8f,
            Color.WHITE
        )
        val difficultyName = this.difficulty.name
        textWidth = FontRenderer.INSTANCE.getWidth(difficultyName) * 0.8f
        FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            difficultyName,
            this.panel.x + this.panel.width / 2.0f - textWidth / 2.0f,
            this.panel.y + this.yOffset + 1.5f,
            0.8f,
            Color.WHITE
        )
    }

    private fun renderBoard(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
        RenderStateManager.addStates(RenderStateManager.THROUGH_WALLS)
        SPRITE_BUFFER.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        this.drawCellSprite(poseStack, MinesweeperSprite.FLAG_NO_BG, this.gridColumns - 1, 0)

        for (column in 0 until this.gridColumns) {
            for (displayRow in 1 until this.gridRows) {
                var opacity = if (this.isCellHovered(column, displayRow, mouseX, mouseY)) 210 else 255
                if (this.won) {
                    opacity = 150
                }
                this.drawCellSprite(
                    poseStack,
                    this.board[displayRow - 1][column].sprite,
                    column,
                    displayRow,
                    opacity
                )
            }
        }

        MeshDrawContext.setRenderType(ClientRenderTypes.image(SPRITE_ATLAS, false))
        SPRITE_BUFFER.draw()
        RenderStateManager.reset()
    }

    private fun renderWinOverlay(poseStack: PoseStack) {
        var textY = (this.height / 2).toFloat()
        val lines = arrayOf("You done it!", "Score " + this.score)

        for (line in lines) {
            val textX = this.panel.width / 2.0f - FontRenderer.INSTANCE.getWidth(line) / 2.0f
            FontRenderer.INSTANCE.drawWithShadow(
                poseStack,
                line,
                this.panel.x + textX,
                this.panel.y + textY,
                Color.WHITE
            )
            textY += FontRenderer.INSTANCE.getHeight() + 1
        }
    }

    private fun resetBoard() {
        this.board = Array(this.gridRows - 1) {
            Array(this.gridColumns) { MinesweeperCell(MinesweeperCellContent.NUMBER_0) }
        }
        this.boardGenerated = false
        this.resetAtMillis = -1L
        this.flagCount = 0
        this.won = false
        this.score = this.difficulty.initialScore
    }

    private fun generateBoard(firstRow: Int, firstColumn: Int) {
        this.board = Array(this.gridRows - 1) {
            Array(this.gridColumns) { MinesweeperCell(MinesweeperCellContent.NUMBER_0) }
        }
        val minePositions = HashSet<Int>()
        val cellCount = (this.gridRows - 1) * this.gridColumns

        while (minePositions.size != this.difficulty.mineCount) {
            val position = ThreadLocalRandom.current().nextInt(cellCount)
            if (!this.isInFirstClickNeighborhood(firstRow, firstColumn, position)) {
                minePositions.add(position)
            }
        }

        for (column in 0 until this.gridColumns) {
            for (row in 0 until this.gridRows - 1) {
                val position = row * this.gridColumns + column
                val content = if (minePositions.contains(position)) {
                    MinesweeperCellContent.BOMB
                } else {
                    MinesweeperCellContent.NUMBER_0
                }
                this.board[row][column] = MinesweeperCell(content)
                this.board[row][column].state = MinesweeperCellState.COVERED
            }
        }

        for (numberRow in 0 until this.gridRows - 1) {
            for (numberColumn in 0 until this.gridColumns) {
                if (this.board[numberRow][numberColumn].content != MinesweeperCellContent.BOMB) {
                    val adjacentMines = this.countAdjacentMines(numberRow, numberColumn)
                    this.board[numberRow][numberColumn].content = MinesweeperCellContent.fromAdjacentMines(adjacentMines)
                }
            }
        }

        this.boardGenerated = true
    }

    fun toggleFlag(row: Int, column: Int) {
        val cell = this.board[row][column]
        if (cell.state == MinesweeperCellState.OPEN) {
            return
        }
        if (cell.state == MinesweeperCellState.FLAGGED) {
            cell.state = MinesweeperCellState.COVERED
            this.flagCount--
        } else if (cell.state == MinesweeperCellState.COVERED && this.flagCount < this.difficulty.mineCount) {
            cell.state = MinesweeperCellState.FLAGGED
            this.flagCount++
        }
    }

    fun openCell(row: Int, column: Int) {
        val cell = this.board[row][column]
        if (cell.state == MinesweeperCellState.OPEN) {
            this.openNeighbors(row, column)
            return
        }
        if (cell.state != MinesweeperCellState.COVERED) {
            return
        }
        if (cell.content == MinesweeperCellContent.BOMB) {
            this.loseGame(cell)
        } else {
            if (cell.content == MinesweeperCellContent.NUMBER_0) {
                this.openEmptyRegion(row, column)
            }
            cell.state = MinesweeperCellState.OPEN
        }
    }

    fun openNeighbors(row: Int, column: Int) {
        val rowCount = this.board.size
        val columnCount = this.board[0].size

        for (rowOffset in -1..1) {
            for (columnOffset in -1..1) {
                val neighborRow = row + rowOffset
                val neighborColumn = column + columnOffset
                if (neighborRow in 0 until rowCount && neighborColumn in 0 until columnCount) {
                    val neighbor = this.board[neighborRow][neighborColumn]
                    if (neighbor.state == MinesweeperCellState.COVERED) {
                        this.openCell(neighborRow, neighborColumn)
                    }
                }
            }
        }
    }

    fun loseGame(explodedCell: MinesweeperCell) {
        for (row in this.board) {
            for (cell in row) {
                if (cell.content == MinesweeperCellContent.BOMB) {
                    cell.state = MinesweeperCellState.OPEN
                }
            }
        }
        explodedCell.state = MinesweeperCellState.BLOWN
        this.resetAtMillis = System.currentTimeMillis() + 2000L
        this.score = 0
    }

    private fun openEmptyRegion(row: Int, column: Int) {
        val rowCount = this.board.size
        val columnCount = this.board[0].size
        val pendingCells = ArrayDeque<IntArray>()
        pendingCells.add(intArrayOf(row, column))

        while (!pendingCells.isEmpty()) {
            val position = pendingCells.remove()
            val currentRow = position[0]
            val currentColumn = position[1]
            if (this.board[currentRow][currentColumn].state == MinesweeperCellState.OPEN) {
                continue
            }
            this.board[currentRow][currentColumn].state = MinesweeperCellState.OPEN
            if (this.board[currentRow][currentColumn].content == MinesweeperCellContent.NUMBER_0) {
                for (rowOffset in -1..1) {
                    for (columnOffset in -1..1) {
                        if (rowOffset != 0 || columnOffset != 0) {
                            val neighborRow = currentRow + rowOffset
                            val neighborColumn = currentColumn + columnOffset
                            if (neighborRow in 0 until rowCount && neighborColumn in 0 until columnCount &&
                                this.board[neighborRow][neighborColumn].state != MinesweeperCellState.FLAGGED &&
                                this.board[neighborRow][neighborColumn].state != MinesweeperCellState.OPEN
                            ) {
                                pendingCells.add(intArrayOf(neighborRow, neighborColumn))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun countAdjacentMines(row: Int, column: Int): Int {
        var mineCount = 0
        for (rowOffset in -1..1) {
            for (columnOffset in -1..1) {
                if (rowOffset != 0 || columnOffset != 0) {
                    val neighborRow = row + rowOffset
                    val neighborColumn = column + columnOffset
                    if (neighborRow in 0 until (this.gridRows - 1) &&
                        neighborColumn in 0 until this.gridColumns &&
                        this.board[neighborRow][neighborColumn].content == MinesweeperCellContent.BOMB
                    ) {
                        mineCount++
                    }
                }
            }
        }
        return mineCount
    }

    private fun isInFirstClickNeighborhood(firstRow: Int, firstColumn: Int, position: Int): Boolean {
        for (columnOffset in -1..1) {
            for (rowOffset in -1..1) {
                val neighborPosition = (firstRow + rowOffset) * this.gridColumns + firstColumn + columnOffset
                if (neighborPosition == position) {
                    return true
                }
            }
        }
        return false
    }

    private fun isResetPending(): Boolean = this.resetAtMillis != -1L

    private fun hasWon(): Boolean {
        for (row in this.board) {
            for (cell in row) {
                if (cell.state == MinesweeperCellState.COVERED && cell.content != MinesweeperCellContent.BOMB) {
                    return false
                }
            }
        }
        return true
    }

    private fun drawCellSprite(poseStack: PoseStack, sprite: MinesweeperSprite, column: Int, displayRow: Int) {
        this.drawCellSprite(poseStack, sprite, column, displayRow, 255)
    }

    private fun drawCellSprite(poseStack: PoseStack, sprite: MinesweeperSprite, column: Int, displayRow: Int, opacity: Int) {
        val bounds = this.getCellBounds(column, displayRow)
        val left = bounds.minX
        val top = bounds.minY
        val right = bounds.maxX
        val bottom = bounds.maxY
        val matrix = poseStack.last().pose()
        val color = ARGB.color(opacity, -1)
        SPRITE_BUFFER.addVertex(matrix, left, bottom, 0.0f).setUv(sprite.minU, sprite.maxV).setColor(color)
        SPRITE_BUFFER.addVertex(matrix, right, bottom, 0.0f).setUv(sprite.maxU, sprite.maxV).setColor(color)
        SPRITE_BUFFER.addVertex(matrix, right, top, 0.0f).setUv(sprite.maxU, sprite.minV).setColor(color)
        SPRITE_BUFFER.addVertex(matrix, left, top, 0.0f).setUv(sprite.minU, sprite.minV).setColor(color)
    }

    private fun isCellHovered(column: Int, displayRow: Int, mouseX: Double, mouseY: Double): Boolean {
        val bounds = this.getCellBounds(column, displayRow)
        return mouseX >= bounds.minX && mouseX < bounds.maxX && mouseY >= bounds.minY && mouseY < bounds.maxY
    }

    private fun isHeaderHovered(mouseX: Double, mouseY: Double): Boolean {
        return mouseX > this.panel.x &&
            mouseX < this.panel.x + this.panel.width &&
            mouseY > this.panel.y + this.yOffset &&
            mouseY < this.panel.y + this.yOffset + this.cellWidth
    }

    private fun getCellBounds(column: Int, displayRow: Int): FloatBounds {
        val x1 = (this.panel.x + column * this.cellWidth).toFloat()
        val y1 = (this.panel.y + this.yOffset + displayRow * this.cellHeight).toFloat()
        val x2 = (x1 + this.cellWidth)
        val y2 = (y1 + this.cellHeight)
        return FloatBounds(x1, y1, x2, y2)
    }

    companion object {
        private val SPRITE_ATLAS = Identifier.fromNamespaceAndPath("collective", "textures/game/atlas.png")
        private val SPRITE_BUFFER = MeshBuffer.create()
    }
}
