package me.collective.client.events

open class CancellableEvent {
   private var cancelled: Boolean = false

   val isCancelled: Boolean
      @JvmName("isCancelledProperty") get() = this.cancelled

   open fun isCancelled(): Boolean = this.cancelled

   open fun resetCancellation() {
      this.cancelled = false
   }

   open fun cancel() {
      this.cancelled = true
   }
}
