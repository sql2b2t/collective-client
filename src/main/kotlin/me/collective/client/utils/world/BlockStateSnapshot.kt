package me.collective.client.utils.world

import me.collective.client.ClientAccess

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

data class BlockStateSnapshot(
   @get:JvmName("state") val state: BlockState,
   @get:JvmName("position") val position: BlockPos,
   @get:JvmName("capturedAtMillis") val capturedAtMillis: Long
) : ClientAccess {

   fun hasExpired(): Boolean {
      return System.currentTimeMillis() - capturedAtMillis >= 500L
   }

   companion object {
      @JvmStatic
      fun captureState(position: BlockPos): BlockState {
         val level = ClientAccess.collectiveMinecraft.level!!
         return level.getBlockState(position)
      }
   }
}
