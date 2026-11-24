package me.collective.client.utils

import me.collective.client.ClientContext
import net.minecraft.util.Mth
import java.util.function.Supplier

class ClampedInterpolatedFloat : InterpolatedFloat {

   constructor(speed: Float, easingEnabled: Boolean) : super(speed, easingEnabled)

   constructor(speedSupplier: Supplier<Float>, easingEnabled: Boolean) : super(speedSupplier, easingEnabled)

   constructor(speed: Float) : super(speed)

   var floatValue: Float
      get() = getValue()
      set(v) = setValue(v)

   var booleanValue: Boolean
      get() = getValue() >= 0.5f
      set(v) = setValue(v)

   var booleanTarget: Boolean
      get() = targetValue >= 0.5f
      set(v) = setTarget(v)

   override fun getValue(): Float {
      val rawValue = currentValue
      val upperBound = 1.0f
      return Mth.clamp(rawValue, 0.0f, upperBound)
   }

   fun setValue(value: Boolean) {
      this.setValue(if (value) 1.0f else 0.0f)
   }

   fun setTarget(value: Boolean) {
      this.setTarget(if (value) 1.0f else 0.0f)
   }

   fun reset() {
      this.setValue(0.0f)
   }

   override fun updateAndGet(): Float {
      val frameTiming = ClientContext.frameTiming
      val interpolationInput = 0.5f

      val timingFactor = frameTiming.getFrameFactor(interpolationInput)
      val suppliedSpeed = this.speedSupplier!!.get()
      var interpolationFactor = timingFactor * suppliedSpeed

      if (easingEnabled) {
         val shiftedFactor = interpolationFactor - 1.0f
         interpolationFactor = (1.0 - shiftedFactor.toDouble() * Math.pow(shiftedFactor.toDouble(), 3.0)).toFloat()
      }

      previousValue = currentValue
      currentValue = previousValue + (targetValue - previousValue) * interpolationFactor
      var shouldSnap = currentValue == targetValue
      if (!shouldSnap) {
         val targetDifference = targetValue - currentValue
         val absoluteTargetDifference = Math.abs(targetDifference.toDouble())
         shouldSnap = absoluteTargetDifference < 0.01
      }
      if (shouldSnap) {
         val snapValue = targetValue
         this.setValue(snapValue)
      }

      val timingSample = ClientContext.frameTiming.getFramesPerSecond()

      if (timingSample <= 15) {
         currentValue = targetValue
      }
      return getValue()
   }
}
