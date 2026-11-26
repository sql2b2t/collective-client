package me.collective.client.utils

import java.util.concurrent.TimeUnit

class ElapsedTimer {
   var lastResetMillis: Long = -1L

   fun elapsedMillis(): Long {
      return System.currentTimeMillis() - lastResetMillis
   }

   fun reset() {
      lastResetMillis = System.currentTimeMillis()
   }

   fun setTime(timestampMillis: Long) {
      lastResetMillis = timestampMillis
   }

   fun hasElapsedAndReset(amount: Long, unit: TimeUnit): Boolean {
      val millis = toMillis(amount.toDouble(), unit)
      return hasElapsedAndReset(millis)
   }

   fun hasElapsed(amount: Double, unit: TimeUnit): Boolean {
      val millis = toMillis(amount, unit)
      return hasElapsed(millis)
   }

   fun hasElapsed(millis: Long): Boolean {
      return System.currentTimeMillis() - lastResetMillis >= millis
   }

   fun clear() {
      lastResetMillis = -1L
   }

   fun hasElapsedAndReset(millis: Long): Boolean {
      val elapsed = hasElapsed(millis)
      if (elapsed) {
         reset()
      }
      return elapsed
   }

   companion object {
      private fun toMillis(value: Double, unit: TimeUnit): Long = when (unit) {
         TimeUnit.NANOSECONDS -> {
            val scaled = value * 0.001
            (scaled * 0.001).toLong()
         }
         TimeUnit.MICROSECONDS -> (value * 0.001).toLong()
         TimeUnit.MILLISECONDS -> value.toLong()
         TimeUnit.SECONDS -> (value * 1000.0).toLong()
         TimeUnit.MINUTES -> {
            val scaled = value * 1000.0
            (scaled * 60.0).toLong()
         }
         TimeUnit.HOURS -> {
            var scaled = value * 1000.0
            scaled *= 60.0
            (scaled * 60.0).toLong()
         }
         TimeUnit.DAYS -> {
            var scaled = value * 1000.0
            scaled *= 60.0
            scaled *= 60.0
            (scaled * 24.0).toLong()
         }
      }
   }
}
