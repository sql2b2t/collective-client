package me.collective.client.gui.components

import java.util.Arrays
import java.util.Random

class TetrisPiece {
   private val coordinates = Array(4) { IntArray(2) }
   private val random = Random()
   var shape: TetrisShape = TetrisShape.NoShape
      private set

   init {
      this.setShape(TetrisShape.NoShape)
   }

   fun setShape(shape: TetrisShape) {
      this.shape = shape
      val source = SHAPES[shape.ordinal]
      for (block in this.coordinates.indices) {
         System.arraycopy(source[block], 0, this.coordinates[block], 0, 2)
      }
   }

   fun setX(blockIndex: Int, x: Int) {
      this.coordinates[blockIndex][0] = x
   }

   fun setY(blockIndex: Int, y: Int) {
      this.coordinates[blockIndex][1] = y
   }

   fun getX(blockIndex: Int): Int {
      return this.coordinates[blockIndex][0]
   }

   fun getY(blockIndex: Int): Int {
      return this.coordinates[blockIndex][1]
   }

   fun setRandomShape() {
      val shapes = TetrisShape.values()
      this.setShape(shapes[this.random.nextInt(1, shapes.size)])
   }

   fun getMinimumY(): Int {
      return Arrays.stream(this.coordinates).mapToInt { it[1] }.min().orElse(0)
   }

   fun rotateLeft(): TetrisPiece {
      return this.rotated(true)
   }

   fun rotateRight(): TetrisPiece {
      return this.rotated(false)
   }

   private fun rotated(left: Boolean): TetrisPiece {
      if (this.shape == TetrisShape.SquareShape) {
         return this
      }
      val result = TetrisPiece()
      result.shape = this.shape
      for (block in this.coordinates.indices) {
         val x = this.getX(block)
         val y = this.getY(block)
         result.setX(block, if (left) y else -y)
         result.setY(block, if (left) -x else x)
      }
      return result
   }

   companion object {
      private val SHAPES = arrayOf(
         arrayOf(intArrayOf(0, 0), intArrayOf(0, 0), intArrayOf(0, 0), intArrayOf(0, 0)),
         arrayOf(intArrayOf(0, -1), intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, 1)),
         arrayOf(intArrayOf(0, -1), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1)),
         arrayOf(intArrayOf(0, -1), intArrayOf(0, 0), intArrayOf(0, 1), intArrayOf(0, 2)),
         arrayOf(intArrayOf(-1, 0), intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1)),
         arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1)),
         arrayOf(intArrayOf(-1, -1), intArrayOf(0, -1), intArrayOf(0, 0), intArrayOf(0, 1)),
         arrayOf(intArrayOf(1, -1), intArrayOf(0, -1), intArrayOf(0, 0), intArrayOf(0, 1))
      )
   }
}
