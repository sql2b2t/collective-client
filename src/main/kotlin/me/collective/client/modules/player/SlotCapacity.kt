package me.collective.client.modules.player

class SlotCapacity(
   val slotIndex: Int,
   val capacity: Int,
   count: Int
) {
   private var storedCount: Int = count

   val count: Int
      get() = this.storedCount

   fun setCount(count: Int) {
      val capacity: Int = this.capacity
      this.storedCount = Math.min(count, capacity)
   }

}
