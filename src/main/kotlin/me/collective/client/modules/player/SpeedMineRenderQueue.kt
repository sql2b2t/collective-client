package me.collective.client.modules.player

import me.collective.client.events.WorldRenderEvent

import net.minecraft.core.BlockPos

class SpeedMineRenderQueue {
   private val blockRenderStates: MutableMap<BlockPos, SpeedMineBlockRenderState> = HashMap()

   fun remove(blockPos: BlockPos) {
      this.blockRenderStates.remove(blockPos)
   }

   fun setProgress(blockPos: BlockPos, progress: Float) {
      val renderState: SpeedMineBlockRenderState = this.blockRenderStates.computeIfAbsent(
         blockPos) { pos: BlockPos -> SpeedMineBlockRenderState(pos, progress)
      }

      renderState.setProgress(progress)
   }

   fun setProgressTwice(blockPos: BlockPos, progress: Float) {
      val renderState: SpeedMineBlockRenderState = this.blockRenderStates.computeIfAbsent(
         blockPos) { pos: BlockPos -> SpeedMineBlockRenderState(pos, progress)
      }

      renderState.setProgress(progress)

      renderState.setProgress(progress)
   }

   fun clear() {
      this.blockRenderStates.clear()
   }

   fun render(event: WorldRenderEvent) {
      var fadedBlockPos: BlockPos? = null
      val states = this.blockRenderStates.values.iterator()

      while (states.hasNext()) {
         val renderState: SpeedMineBlockRenderState = states.next()

         renderState.render(event)

         if (renderState.isFadedOut()) {

            fadedBlockPos = renderState.blockPos
         }
      }

      if (fadedBlockPos != null) {
         this.blockRenderStates.remove(fadedBlockPos)
      }
   }

   fun snapshotProgress() {
      this.blockRenderStates.values.forEach(SpeedMineBlockRenderState::snapshotProgress)
   }
}
