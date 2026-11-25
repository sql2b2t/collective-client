package me.collective.client.utils.combat

import me.collective.client.ClientAccess

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth

class ExtraBreakTarget(
   val position: BlockPos
) : ClientAccess {
   private var progress: Float = 0.0F

   val clampedProgress: Float
      get() = Mth.clamp(progress, 0.0F, 1.0F)

   fun addProgress(amount: Float) {
      progress += amount
   }

   fun setProgress(progress: Float) {
      this.progress = progress
   }
}
