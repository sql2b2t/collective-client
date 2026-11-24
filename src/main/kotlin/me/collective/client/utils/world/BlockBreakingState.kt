package me.collective.client.utils.world

import net.minecraft.core.BlockPos

interface BlockBreakingState {
   fun getCurrentBreakingBlock(): BlockPos?

   fun setCurrentBreakingBlock(position: BlockPos?)

   fun getBreakingProgress(): Float

   fun setBreakingProgress(progress: Float)

   fun isBreakingBlock(): Boolean

   fun setBreakingBlock(breaking: Boolean)
}
