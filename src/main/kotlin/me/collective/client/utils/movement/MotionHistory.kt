package me.collective.client.utils.movement

import java.util.ArrayDeque
import java.util.Deque
import java.util.function.ToDoubleFunction

class MotionHistory(capacity: Int) {
   private val capacity: Int = capacity
   private val samples: Deque<MotionSample> = ArrayDeque(capacity)

   fun getLatest(): MotionSample? {
      return this.samples.peekLast()
   }

   fun isEmpty(): Boolean {
      return this.samples.isEmpty()
   }

   fun append(sample: MotionSample) {
      if (this.samples.size >= this.capacity) {
         this.samples.pollFirst()
      }

      this.samples.addLast(sample)
   }

   fun weightedAverage(extractor: ToDoubleFunction<in MotionSample>): Double {
      if (this.samples.isEmpty()) {
         return 0.0
      }

      var weightedTotal = 0.0
      var totalWeight = 0.0
      var weight = 1

      for (sample in this.samples) {
         weightedTotal += extractor.applyAsDouble(sample) * weight
         totalWeight += weight++
      }

      return weightedTotal / totalWeight
   }

   fun average(extractor: ToDoubleFunction<in MotionSample>): Double {
      return this.samples.stream().mapToDouble(extractor).average().orElse(0.0)
   }

   fun clear() {
      this.samples.clear()
   }
}
