package me.collective.client.modules.movement

import java.util.ArrayList
import java.util.Collections

class ObstacleCorrectionWindow {
   private val corrections: MutableList<Long> = Collections.synchronizedList(ArrayList())
   private val windowMillis: Int
   private val maximumCorrections: Int

   constructor(maximumCorrections: Int) : this(1000, maximumCorrections)

   constructor(windowMillis: Int, maximumCorrections: Int) {
      this.windowMillis = windowMillis
      this.maximumCorrections = maximumCorrections
   }

   fun clear() {
      corrections.clear()
   }

   fun pruneExpired() {
      corrections.removeIf { timestamp -> System.currentTimeMillis() > timestamp + windowMillis }
   }

   fun recordCorrection() {
      corrections.add(System.currentTimeMillis())
   }

   fun exceedsLimit(): Boolean {
      return corrections.size > maximumCorrections
   }
}
