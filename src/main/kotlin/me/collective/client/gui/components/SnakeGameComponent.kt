package me.collective.client.gui.components

import com.mojang.blaze3d.vertex.PoseStack
import me.collective.client.ClientAccess
import me.collective.client.gui.panels.Panel
import me.collective.client.render.FontRenderer
import me.collective.client.utils.collections.IntPair
import me.collective.client.utils.math.MathConstants
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.ArrayList

class SnakeGameComponent(panel: Panel, yOffset: Int) : GridGameComponent(panel, yOffset) {
   private var direction: SnakeDirection = SnakeDirection.UP
   private var pendingDirection: SnakeDirection? = null
   private val segments: ArrayList<IntPair> = ArrayList()
   private var food: IntPair? = null
   private var score: Int = 0
   private var gameOver: Boolean = false

   init {
      this.resetGame()
   }

   override fun tickGame() {
      if (this.gameOver) {
         return
      }
      if (this.pendingDirection != null) {
         this.direction = this.pendingDirection!!
         this.pendingDirection = null
      }

      if (this.segments.isEmpty()) {
         return
      }
      val head = this.segments.first()
      var nextHead = head.add(this.direction.offset)
      if (nextHead.first > this.gridColumns - 1) {
         nextHead = IntPair(0, nextHead.second)
      } else if (nextHead.first < 0) {
         nextHead = IntPair(this.gridColumns - 1, nextHead.second)
      } else if (nextHead.second > this.gridRows - 1) {
         nextHead = IntPair(nextHead.first, 0)
      } else if (nextHead.second < 0) {
         nextHead = IntPair(nextHead.first, this.gridRows - 1)
      }

      if (this.segments.contains(nextHead)) {
         this.gameOver = true
         return
      }

      this.segments.add(0, nextHead)
      if (this.segments.first() == this.food) {
         this.food = this.findFoodPosition()
         this.score++
      } else {
         this.segments.removeAt(this.segments.size - 1)
      }
   }

   override fun render(poseStack: PoseStack, mouseX: Double, mouseY: Double) {
      super.render(poseStack, mouseX, mouseY)
      val currentFood = this.food
      if (currentFood != null) {
         this.drawCell(
            poseStack,
            currentFood.first,
            currentFood.second,
            Color.red
         )
      }
      var headRendered = false

      for (segment in this.segments) {
         if (!headRendered) {
            this.drawCell(
               poseStack,
               segment.first,
               segment.second,
               if (this.gameOver) Color.GRAY.brighter() else Color.GREEN
            )
            headRendered = true
         } else {
            this.drawCell(
               poseStack,
               segment.first,
               segment.second,
               if (this.gameOver) Color.GRAY else Color(0, 150, 0)
            )
         }
      }

      if (this.gameOver) {
         val displayedScore = this.score
         val gameOverText = "Score $displayedScore. Press R."
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
            this.score.toString(),
            this.panel.x + 1.5f,
            (this.panel.y + this.yOffset + 1).toFloat(),
            0.75f,
            Color.white
         )
      }
   }

   override fun keyPressed(keyCode: Int) {
      var requestedDirection: SnakeDirection? = null
      when (keyCode) {
         82 -> this.resetGame()
         262 -> requestedDirection = SnakeDirection.RIGHT
         263 -> requestedDirection = SnakeDirection.LEFT
         264 -> requestedDirection = SnakeDirection.DOWN
         265 -> requestedDirection = SnakeDirection.UP
      }

      if (requestedDirection != null && requestedDirection != this.direction.opposite()) {
         this.pendingDirection = requestedDirection
      }
   }

   override fun getTickDelayMillis(): Int {
      return if (GLFW.glfwGetKey(ClientAccess.collectiveMinecraft.window.window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
         200
      } else {
         500
      }
   }

   override val height: Int
      get() = this.panel.width

   private fun resetGame() {
      this.segments.clear()
      this.segments.add(IntPair(11, 11))
      this.food = this.findFoodPosition()
      this.score = 0
      this.gameOver = false
   }

   private fun findFoodPosition(): IntPair {
      var candidate = this.segments.first()
      while (this.segments.contains(candidate)) {
         candidate = IntPair(
            (Math.random() * this.gridColumns).toInt(),
            (Math.random() * this.gridRows).toInt()
         )
      }
      return candidate
   }
}
