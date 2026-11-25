package me.collective.client.gui.components

class MinesweeperCell(var content: MinesweeperCellContent) {
   var state: MinesweeperCellState = MinesweeperCellState.COVERED

   fun getSprite(): MinesweeperSprite {
      return when (this.state) {
         MinesweeperCellState.COVERED -> MinesweeperSprite.UNCOVERED
         MinesweeperCellState.FLAGGED -> MinesweeperSprite.FLAG
         MinesweeperCellState.BLOWN -> MinesweeperSprite.BOMB_BLEW
         MinesweeperCellState.OPEN -> when (this.content) {
            MinesweeperCellContent.BOMB -> MinesweeperSprite.BOMB
            MinesweeperCellContent.NUMBER_0 -> MinesweeperSprite.EMPTY
            MinesweeperCellContent.NUMBER_1 -> MinesweeperSprite.ONE
            MinesweeperCellContent.NUMBER_2 -> MinesweeperSprite.TWO
            MinesweeperCellContent.NUMBER_3 -> MinesweeperSprite.THREE
            MinesweeperCellContent.NUMBER_4 -> MinesweeperSprite.FOUR
            MinesweeperCellContent.NUMBER_5 -> MinesweeperSprite.FIVE
            MinesweeperCellContent.NUMBER_6 -> MinesweeperSprite.SIX
            MinesweeperCellContent.NUMBER_7 -> MinesweeperSprite.SEVEN
            MinesweeperCellContent.NUMBER_8 -> MinesweeperSprite.EIGHT
         }
      }
   }
}

val MinesweeperCell.sprite: MinesweeperSprite get() = getSprite()
