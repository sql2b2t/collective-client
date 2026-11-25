package me.collective.client.gui.components

private const val U_STEP = 0.125f
private const val V_STEP = 0.5f

enum class MinesweeperSprite {
   ONE,
   TWO,
   THREE,
   FOUR,
   UNCOVERED,
   EMPTY,
   BOMB,
   BOMB_HIDDEN,
   FIVE,
   SIX,
   SEVEN,
   EIGHT,
   FLAG,
   HOVERED,
   BOMB_BLEW,
   FLAG_NO_BG;

   val minU: Float = (this.ordinal % 8) * U_STEP
   val maxU: Float = this.minU + U_STEP
   val minV: Float = (this.ordinal / 8) * V_STEP
   val maxV: Float = this.minV + V_STEP
}
