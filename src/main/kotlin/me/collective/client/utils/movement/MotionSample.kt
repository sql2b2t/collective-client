package me.collective.client.utils.movement

data class MotionSample(
   val motionX: Double,
   val motionY: Double,
   val motionZ: Double,
   val accelerationX: Double,
   val accelerationZ: Double,
   val collisionBelow: Boolean,
   val sneaking: Boolean,
   val fallFlying: Boolean
) {
   fun motionX(): Double {
      return this.motionX
   }

   fun motionY(): Double {
      return this.motionY
   }

   fun motionZ(): Double {
      return this.motionZ
   }

   fun accelerationX(): Double {
      return this.accelerationX
   }

   fun accelerationZ(): Double {
      return this.accelerationZ
   }

   fun collisionBelow(): Boolean {
      return this.collisionBelow
   }

   fun sneaking(): Boolean {
      return this.sneaking
   }

   fun fallFlying(): Boolean {
      return this.fallFlying
   }
}
