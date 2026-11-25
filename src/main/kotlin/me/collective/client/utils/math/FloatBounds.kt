package me.collective.client.utils.math

data class FloatBounds(
   val minX: Float,
   val minY: Float,
   val maxX: Float,
   val maxY: Float
) {
   fun minX(): Float = this.minX

   fun minY(): Float = this.minY

   fun maxX(): Float = this.maxX

   fun maxY(): Float = this.maxY

   fun getWidth(): Float {
      return this.maxX - this.minX
   }

   fun expand(horizontalAmount: Float, verticalAmount: Float): FloatBounds {
      return FloatBounds(
         this.minX - horizontalAmount,
         this.minY - verticalAmount,
         this.maxX + horizontalAmount,
         this.maxY + verticalAmount
      )
   }

   fun getHeight(): Float {
      return this.maxY - this.minY
   }

   fun union(vararg others: FloatBounds): FloatBounds {
      var unionMinX = this.minX
      var unionMaxX = this.maxX
      var unionMinY = this.minY
      var unionMaxY = this.maxY

      for (other in others) {
         val otherMinX = other.minX()
         unionMinX = Math.min(unionMinX, otherMinX)

         val otherMinY = other.minY()
         unionMinY = Math.min(unionMinY, otherMinY)

         val otherMaxX = other.maxX()
         unionMaxX = Math.max(unionMaxX, otherMaxX)

         val otherMaxY = other.maxY()
         unionMaxY = Math.max(unionMaxY, otherMaxY)
      }

      return FloatBounds(unionMinX, unionMinY, unionMaxX, unionMaxY)
   }

   fun intersects(other: FloatBounds): Boolean {
      return (this.maxX < this.minX || this.maxX > other.minX()) &&
         (this.maxY < this.minY || this.maxY > other.minY()) &&
         (other.maxX() < other.minX() || other.maxX() > this.minX) &&
         (other.maxY() < other.minY() || other.maxY() > this.minY)
   }

   fun contains(x: Double, y: Double): Boolean {
      return x >= this.minX &&
         x <= this.maxX &&
         y >= this.minY &&
         y <= this.maxY
   }

   fun expand(amount: Float): FloatBounds {
      return this.expand(amount, amount)
   }

   companion object {
      @JvmStatic
      fun fromPositionAndSize(x: Float, y: Float, width: Float, height: Float): FloatBounds {
         return FloatBounds(x, y, x + width, y + height)
      }
   }
}
