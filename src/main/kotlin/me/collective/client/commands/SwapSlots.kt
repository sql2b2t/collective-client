package me.collective.client.commands

@JvmRecord
data class SwapSlots(
   val sourceSlot: Int,
   val targetSlot: Int
) {
   fun isInvalid(): Boolean {
      return this.sourceSlot == -1 || this.targetSlot == -1
   }

   companion object {
      @JvmField
      val INVALID: SwapSlots = SwapSlots(-1, -1)
   }
}
