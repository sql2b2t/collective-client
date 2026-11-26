package me.collective.client.utils

import net.minecraft.util.Mth
import java.util.function.Function

class FloatAnimation {
   private var startValue: Float = 0.0f
   private var targetValue: Float = 0.0f
   private var endTimeMillis: Long = 0L
   private var durationMillis: Long = 0L

   var value: Float
      @JvmName("getValueProp") get() = getValue()
      @JvmName("setValueProp") set(v) = setValue(v)

   fun getValue(): Float {
      if (System.currentTimeMillis() > endTimeMillis) {
         return targetValue
      }
      val remainingMillis = (endTimeMillis - System.currentTimeMillis()).toFloat()
      val remainingFraction = remainingMillis / Math.max(durationMillis, 1L).toFloat()
      val progress = 1.0f - Mth.clamp(remainingFraction, 0.0f, 1.0f)
      return progress * (targetValue - startValue) + startValue
   }

   fun setValue(value: Float) {
      this.setTarget(value, 0L)
   }

   fun setTarget(targetState: Boolean, newDurationMillis: Long) {
      val decodedTarget = if (targetState) 1.0f else 0.0f
      this.setTarget(decodedTarget, newDurationMillis)
   }

   fun getValue(easing: Function<Float, Float>): Float {
      if (System.currentTimeMillis() > endTimeMillis) {
         return targetValue
      }

      val interpolationScale = 1.0f
      val remainingMillis = (endTimeMillis - System.currentTimeMillis()).toFloat()
      val remainingFraction = remainingMillis / Math.max(durationMillis, 1L).toFloat()
      val clampUpperBound = 1.0f
      val easingInput = interpolationScale - Mth.clamp(remainingFraction, 0.0f, clampUpperBound)
      val easingResult = easing.apply(easingInput)
      return easingResult * (targetValue - startValue) + startValue
   }

   fun setTarget(newTargetValue: Float, newDurationMillis: Long) {
      if (targetValue != newTargetValue) {
         startValue = this.getValue()
         targetValue = newTargetValue
         endTimeMillis = System.currentTimeMillis() + newDurationMillis
         durationMillis = newDurationMillis
      }
   }
}
