package me.collective.client.utils

import me.collective.client.ClientContext
import me.collective.client.utils.math.MathConstants
import java.util.function.Supplier

open class InterpolatedFloat {
   @JvmField
   var currentValue: Float = 0.0f

   @JvmField
   var targetValue: Float = 0.0f

   @JvmField
   var previousValue: Float = 0.0f

   @JvmField
   var easingEnabled: Boolean = false

   @JvmField
   var speedSupplier: Supplier<Float>? = null

   constructor(speed: Float, easingEnabled: Boolean) {
      this.speedSupplier = Supplier { speed }
      this.easingEnabled = easingEnabled
   }

   constructor(speedSupplier: Supplier<Float>, easingEnabled: Boolean) {
      this.speedSupplier = speedSupplier
      this.easingEnabled = easingEnabled
   }

   constructor(speed: Float) : this(speed, true)

   var value: Float
      @JvmName("getValueProp") get() = getValue()
      @JvmName("setValueProp") set(v) = setValue(v)

   var target: Float
      @JvmName("getTargetProp") get() = targetValue
      @JvmName("setTargetProp") set(v) = setTarget(v)

   open fun getValue(): Float {
      return currentValue
   }

   fun setTarget(value: Float) {
      if (targetValue != value) {
         previousValue = currentValue
         targetValue = value
      }
   }

   open fun updateAndGet(): Float {
      val frameTiming = ClientContext.frameTiming
      val frameArgument = 0.5f

      val frameFactor = frameTiming.getFrameFactor(frameArgument)
      val speedValue = this.speedSupplier?.get() ?: 0f
      var interpolationFactor = frameFactor * speedValue

      if (easingEnabled) {
         val factorOffset = interpolationFactor - 1.0f
         interpolationFactor = (1.0 - factorOffset.toDouble() * Math.pow(factorOffset.toDouble(), 3.0)).toFloat()
      }

      previousValue = currentValue
      currentValue = previousValue + (targetValue - previousValue) * interpolationFactor
      var shouldSnap = currentValue == targetValue
      if (!shouldSnap) {
         val remainingDifference = targetValue - currentValue
         shouldSnap = Math.abs(remainingDifference) < MathConstants.HALF
      }
      if (shouldSnap) {
         val snapValue = targetValue
         this.setValue(snapValue)
      }

      val frameLimit = ClientContext.frameTiming.getFramesPerSecond()

      if (frameLimit <= 15) {
         currentValue = targetValue
      }
      return currentValue
   }

   fun setValue(value: Float) {
      if (currentValue != value || targetValue != value) {
         previousValue = value
         currentValue = value
         targetValue = value
      }
   }

   fun setEasingEnabled(easingEnabled: Boolean) {
      this.easingEnabled = easingEnabled
   }

   fun setSpeedSupplier(speedSupplier: Supplier<Float>) {
      this.speedSupplier = speedSupplier
   }

   fun getSpeedSupplier(): Supplier<Float>? {
      return this.speedSupplier
   }
}
