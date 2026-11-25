package me.collective.client.utils.collections

data class IntPair(
   val first: Int,
   val second: Int
) {
   fun first(): Int = this.first

   fun second(): Int = this.second

   fun squaredDistanceTo(other: IntPair): Double {
      val firstDifference = (this.first - other.first).toDouble()
      val firstSquared = Math.pow(firstDifference, 2.0)
      val secondDifference = (this.second - other.second).toDouble()
      return firstSquared + Math.pow(secondDifference, 2.0)
   }

   fun distanceTo(other: IntPair): Double {
      return Math.sqrt(this.squaredDistanceTo(other))
   }

   fun add(other: IntPair): IntPair {
      return IntPair(this.first + other.first, this.second + other.second)
   }
}
