package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.gui.panels.Panel
import me.collective.client.render.ColorUtils
import me.collective.client.render.FontRenderer
import me.collective.client.utils.math.MathConstants
import java.awt.Color

class TetrisGameComponent(panel: Panel, yOffset: Int) : GridGameComponent(panel, yOffset, 22, 22) {
   private var waitingAfterLineClear = false
   private var gameOver = false
   private var clearedLines = 0
   private var pieceX = 0
   private var pieceY = 0
   private var currentPiece: TetrisPiece? = null
   private var board: Array<TetrisShape>? = null

   override fun tickGame() {
      if (this.currentPiece == null) {
         this.resetGame()
      }

      if (this.waitingAfterLineClear) {
         this.waitingAfterLineClear = false
         this.spawnPiece()
      } else {
         this.stepDown()
      }
   }

   override fun getTickDelayMillis(): Int = 500

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poseStack, mouseX, mouseY)

      for (renderRow in 0 until this.gridRows) {
         for (column in 0 until this.gridColumns) {
            val shape = this.getBoardShape(column, this.gridRows - renderRow - 1)
            if (shape != TetrisShape.NoShape) {
               this.drawCell(poseStack, column, renderRow, shape.color)
            }
         }
      }

      val piece = this.currentPiece
      if (piece != null && piece.shape != TetrisShape.NoShape) {
         val ghostPiece = TetrisPiece()
         ghostPiece.setShape(piece.shape)

         for (blockIndex in 0 until 4) {
            ghostPiece.setX(blockIndex, piece.getX(blockIndex))
            ghostPiece.setY(blockIndex, piece.getY(blockIndex))
            val blockX = this.pieceX + piece.getX(blockIndex)
            val blockY = this.pieceY - piece.getY(blockIndex)
            val pieceColor = piece.shape.color
            this.drawCell(poseStack, blockX, this.gridRows - blockY - 1, pieceColor)
         }

         var ghostY = this.pieceY
         while (ghostY > 0 && this.canPlacePiece(ghostPiece, this.pieceX, ghostY - 1)) {
            ghostY--
         }

         for (ghostBlockIndex in 0 until 4) {
            val ghostBlockX = this.pieceX + ghostPiece.getX(ghostBlockIndex)
            val ghostBlockY = ghostY - ghostPiece.getY(ghostBlockIndex)
            val ghostColor = piece.shape.color
            this.drawCell(
               poseStack,
               ghostBlockX,
               this.gridRows - ghostBlockY - 1,
               ColorUtils.withAlpha(ghostColor, 50)
            )
         }
      }

      if (this.gameOver) {
         val score = this.clearedLines
         val gameOverText = "Score $score. Press R."
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            gameOverText,
            (this.panel.x + this.panel.width * MathConstants.HALF - FontRenderer.INSTANCE.getWidth(gameOverText) * MathConstants.HALF).toFloat(),
            (this.panel.y + this.yOffset + this.height * MathConstants.HALF).toFloat(),
            Color.white
         )
      } else {
         FontRenderer.INSTANCE.drawWithShadow(
            poseStack,
            this.clearedLines.toString(),
            this.panel.x + 1.5f,
            (this.panel.y + this.yOffset + 1).toFloat(),
            0.75f,
            Color.white
         )
      }
   }

   override fun keyPressed(keyCode: Int) {
      val piece = this.currentPiece ?: return
      when (keyCode) {
         32 -> this.hardDrop()
         68 -> this.stepDown()
         82 -> this.resetGame()
         262 -> this.tryMovePiece(piece, this.pieceX + 1, this.pieceY)
         263 -> this.tryMovePiece(piece, this.pieceX - 1, this.pieceY)
         264 -> this.tryMovePiece(piece.rotateRight(), this.pieceX, this.pieceY)
         265 -> this.tryMovePiece(piece.rotateLeft(), this.pieceX, this.pieceY)
      }
   }

   private fun resetGame() {
      this.gameOver = false
      this.currentPiece = TetrisPiece()
      this.board = Array(this.gridColumns * this.gridRows) { TetrisShape.NoShape }
      this.clearBoard()
      this.spawnPiece()
   }

   private fun getBoardShape(column: Int, row: Int): TetrisShape {
      val b = this.board ?: return TetrisShape.NoShape
      return b[row * this.gridColumns + column]
   }

   private fun hardDrop() {
      val piece = this.currentPiece ?: return
      var dropY = this.pieceY
      while (dropY > 0 && this.tryMovePiece(piece, this.pieceX, dropY - 1)) {
         dropY--
      }
      this.lockPiece()
   }

   private fun stepDown() {
      val piece = this.currentPiece ?: return
      if (!this.tryMovePiece(piece, this.pieceX, this.pieceY - 1)) {
         this.lockPiece()
      }
   }

   private fun clearBoard() {
      this.clearedLines = 0
      val b = this.board ?: return
      for (cellIndex in 0 until this.gridRows * this.gridColumns) {
         b[cellIndex] = TetrisShape.NoShape
      }
   }

   private fun lockPiece() {
      val piece = this.currentPiece ?: return
      val b = this.board ?: return
      for (blockIndex in 0 until 4) {
         val blockX = this.pieceX + piece.getX(blockIndex)
         val blockY = this.pieceY - piece.getY(blockIndex)
         b[blockY * this.gridColumns + blockX] = piece.shape
      }

      this.removeFullLines()
      if (!this.waitingAfterLineClear) {
         this.spawnPiece()
      }
   }

   private fun spawnPiece() {
      val piece = this.currentPiece ?: return
      piece.setRandomShape()
      this.pieceX = this.gridColumns / 2 + 1
      this.pieceY = this.gridRows - 1 + piece.getMinimumY()
      if (!this.tryMovePiece(piece, this.pieceX, this.pieceY)) {
         piece.setShape(TetrisShape.NoShape)
         this.gameOver = true
      }
   }

   private fun canPlacePiece(piece: TetrisPiece, targetX: Int, targetY: Int): Boolean {
      for (blockIndex in 0 until 4) {
         val blockX = targetX + piece.getX(blockIndex)
         val blockY = targetY - piece.getY(blockIndex)
         if (blockX < 0 || blockX >= this.gridColumns || blockY < 0 || blockY >= this.gridRows) {
            return false
         }

         if (this.getBoardShape(blockX, blockY) != TetrisShape.NoShape) {
            return false
         }
      }
      return true
   }

   private fun tryMovePiece(piece: TetrisPiece, targetX: Int, targetY: Int): Boolean {
      if (!this.canPlacePiece(piece, targetX, targetY)) {
         return false
      }

      this.currentPiece = piece
      this.pieceX = targetX
      this.pieceY = targetY
      return true
   }

   private fun removeFullLines() {
      val b = this.board ?: return
      var removedLines = 0

      for (row in this.gridRows - 1 downTo 0) {
         var fullRow = true
         for (column in 0 until this.gridColumns) {
            if (this.getBoardShape(column, row) == TetrisShape.NoShape) {
               fullRow = false
               break
            }
         }

         if (fullRow) {
            removedLines++
            for (shiftRow in row until this.gridRows - 1) {
               for (shiftColumn in 0 until this.gridColumns) {
                  b[shiftRow * this.gridColumns + shiftColumn] = this.getBoardShape(shiftColumn, shiftRow + 1)
               }
            }
         }
      }

      if (removedLines > 0) {
         this.clearedLines += removedLines
         this.waitingAfterLineClear = true
         this.currentPiece?.setShape(TetrisShape.NoShape)
      }
   }

   override val height: Int
      get() = this.panel.width
}
