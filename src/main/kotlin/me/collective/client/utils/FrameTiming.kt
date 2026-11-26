package me.collective.client.utils

import me.collective.client.ClientAccess
import java.util.ArrayDeque
import java.util.Deque
import net.minecraft.util.Mth

class FrameTiming : ClientAccess {
   private val frameExpirationsNanos: Deque<Long> = ArrayDeque()
   private var lastFrameTimeNanos: Long = System.nanoTime()
   private var lastFrameRate: Long = 0L
   private var framesPerSecond: Int = 0

   fun getFramesPerSecond(): Int {
      return framesPerSecond
   }

   fun getFrameFactor(rateScale: Float): Float {
      return Mth.clamp(1.0f / (lastFrameRate.toFloat() * rateScale), 0.0f, 1.0f)
   }

   fun recordFrame() {
      val sampledTimeNanos = System.nanoTime()
      val expirationQueue = frameExpirationsNanos

      val expirationNanos = sampledTimeNanos + NANOS_PER_SECOND
      expirationQueue.add(expirationNanos)

      while (!frameExpirationsNanos.isEmpty()) {
         val headExpirationNanos = frameExpirationsNanos.peekFirst()!!
         if (sampledTimeNanos <= headExpirationNanos) {
            break
         }

         frameExpirationsNanos.remove()
      }

      framesPerSecond = frameExpirationsNanos.size

      lastFrameRate = NANOS_PER_SECOND / (sampledTimeNanos - lastFrameTimeNanos)
      lastFrameTimeNanos = sampledTimeNanos
   }

   companion object {
      private const val NANOS_PER_SECOND = 1000000000L
   }
}
