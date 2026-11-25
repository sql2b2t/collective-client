package me.collective.client.utils.inventory

import java.util.function.Consumer
import net.minecraft.core.BlockPos

class AutoMineCandidates {
   var primaryPosition: BlockPos? = null
      @JvmName("getPrimaryPositionProp") get
      @JvmName("setPrimaryPositionProp") set

   fun setPrimaryPosition(pos: BlockPos?) {
      this.primaryPosition = pos
   }

   fun getPrimaryPosition(): BlockPos? = this.primaryPosition
   var secondaryPosition: BlockPos? = null
   var isMiningCancellationRequested: Boolean = false
   var priority: Int = 0

   fun reset() {
      this.primaryPosition = null
      this.isMiningCancellationRequested = false
   }

   fun requestMiningCancellation() {
      this.isMiningCancellationRequested = true
   }

   fun swapPositions() {
      val previousPrimary = this.primaryPosition
      this.primaryPosition = this.secondaryPosition
      this.secondaryPosition = previousPrimary
   }

   fun offer(incomingPriority: Int, update: Consumer<AutoMineCandidates>) {
      if (this.priority > incomingPriority) {
         if (this.primaryPosition != null && !this.isMiningCancellationRequested) {
            return
         }
      }

      update.accept(this)
      this.priority = incomingPriority
   }
}
