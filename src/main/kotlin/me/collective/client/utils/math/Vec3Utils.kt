package me.collective.client.utils.math

import me.collective.mixin.Vec3Accessor
import net.minecraft.world.phys.Vec3

class Vec3Utils private constructor() {
   companion object {
      @JvmStatic
      fun setComponents(vector: Vec3, x: Double, y: Double, z: Double) {
         val mutableVector = vector as Vec3Accessor
         mutableVector.setX(x)
         mutableVector.setY(y)
         mutableVector.setZ(z)
      }

      @JvmStatic
      fun horizontalDistance(start: Vec3, end: Vec3): Float {
         val startX = start.x
         val startZ = start.z
         val endX = end.x
         val endZ = end.z
         val deltaX = startX - endX
         val deltaZ = startZ - endZ
         return Math.hypot(deltaX, deltaZ).toFloat()
      }

      @JvmStatic
      fun lerp(fraction: Float, start: Vec3, end: Vec3): Vec3 {
         val difference = end.subtract(start)
         val scaledDifference = difference.scale(fraction.toDouble())
         return start.add(scaledDifference)
      }
   }
}
