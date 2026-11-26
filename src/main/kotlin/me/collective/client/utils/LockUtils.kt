package me.collective.client.utils

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class LockUtils private constructor() {
   companion object {
      private val SHARED_LOCK: Lock = ReentrantLock()

      @JvmStatic
      fun runWithSharedLock(action: Runnable) {
         runWithLock(SHARED_LOCK, action)
      }

      @JvmStatic
      fun wrapWithSharedLock(action: Runnable): Runnable {
         return Runnable { runWithSharedLock(action) }
      }

      @JvmStatic
      fun runWithLock(suppliedLock: Lock, action: Runnable) {
         try {
            suppliedLock.lock()
            action.run()
         } finally {
            suppliedLock.unlock()
         }
      }

      @JvmStatic
      fun wrapWithLock(suppliedLock: Lock, action: Runnable): Runnable {
         return Runnable { runWithLock(suppliedLock, action) }
      }
   }
}
