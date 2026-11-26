package me.collective.client.events

interface Cancellable {
   val isCancelled: Boolean

   fun setCancelled(cancelled: Boolean)

   fun cancel() {
      this.setCancelled(true)
   }
}
