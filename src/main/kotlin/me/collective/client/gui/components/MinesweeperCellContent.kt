package me.collective.client.gui.components

enum class MinesweeperCellContent {
   BOMB,
   NUMBER_0,
   NUMBER_1,
   NUMBER_2,
   NUMBER_3,
   NUMBER_4,
   NUMBER_5,
   NUMBER_6,
   NUMBER_7,
   NUMBER_8;

   companion object {
      @JvmStatic
      fun fromAdjacentMines(adjacentMines: Int): MinesweeperCellContent {
         return entries[adjacentMines + 1]
      }
   }
}
