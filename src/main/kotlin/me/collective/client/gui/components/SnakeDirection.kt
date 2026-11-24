package me.collective.client.gui.components

import me.collective.client.utils.collections.IntPair

enum class SnakeDirection(xOffset: Int, yOffset: Int) {
   UP(0, -1),
   RIGHT(1, 0),
   DOWN(0, 1),
   LEFT(-1, 0);

   val offset: IntPair = IntPair(xOffset, yOffset)

   fun opposite(): SnakeDirection {
      return when (this) {
         UP -> DOWN
         RIGHT -> LEFT
         DOWN -> UP
         LEFT -> RIGHT
      }
   }
}
