package me.collective.client.utils.inventory

import me.collective.client.ClientAccess

import java.util.ArrayList
import java.util.HashMap
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

class ExtraBreakTracker : ClientAccess {
   private val expiredPositions: MutableList<BlockPos> = ArrayList()
   private val trackedBreaks: MutableMap<BlockPos, Long> = HashMap()
   private var lastBlock: Block? = null

   @Synchronized
   fun pruneExpired() {
      this.trackedBreaks.forEach { position, trackedAt -> this.collectExpired(position, trackedAt) }
      val expiredSnapshot: List<BlockPos> = this.expiredPositions
      val removalTarget: MutableMap<BlockPos, Long> = this.trackedBreaks
      expiredSnapshot.forEach { position -> removalTarget.remove(position) }
      this.expiredPositions.clear()
   }

   fun getLastBlock(): Block? {
      return this.lastBlock
   }

   @Synchronized
   fun isTracked(position: BlockPos): Boolean {
      return this.trackedBreaks.containsKey(position)
   }

   private fun collectExpired(position: BlockPos, trackedAt: Long) {
      val elapsedMillis = System.currentTimeMillis() - trackedAt

      if (elapsedMillis >= 200L) {
         this.expiredPositions.add(position)
      }
   }

   @Synchronized
   fun trackBreak(position: BlockPos) {
      val breaksSnapshot: MutableMap<BlockPos, Long> = this.trackedBreaks
      val trackedAt = System.currentTimeMillis()
      breaksSnapshot[position] = trackedAt
      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val blockState: BlockState = level.getBlockState(position)
      if (!blockState.isAir) {
         this.lastBlock = blockState.block
      }
   }
}
