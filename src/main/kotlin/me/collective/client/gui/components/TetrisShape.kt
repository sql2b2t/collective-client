package me.collective.client.gui.components

import java.awt.Color

enum class TetrisShape(val color: Color) {
   NoShape(Color(0, 0, 0)),
   ZShape(Color(204, 102, 102)),
   SShape(Color(102, 204, 102)),
   LineShape(Color(102, 102, 204)),
   TShape(Color(204, 204, 102)),
   SquareShape(Color(204, 102, 204)),
   LShape(Color(102, 204, 204)),
   MirroredLShape(Color(218, 170, 0))
}
