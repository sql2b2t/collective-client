package me.collective.client.utils.world

import me.collective.client.ClientAccess
import me.collective.client.events.ClientChunkCacheReplaceWithPacketDataEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.PacketEvent

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.BiConsumer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

class ChunkScanner(
   private val workerCount: Int,
   private val chunkLoadCallback: BiConsumer<ChunkPos, LevelChunk>?,
   private val chunkUnloadCallback: BiConsumer<ChunkPos, LevelChunk>?,
   private val blockUpdateCallback: BiConsumer<BlockPos, BlockState>?
) : ClientAccess {
   @Volatile
   private var executor: ExecutorService? = null
   private var started = false

   constructor(workerCount: Int, listener: Listener) : this(
      workerCount,
      BiConsumer { chunkPosition: ChunkPos, chunk: LevelChunk ->
         listener.onChunkLoaded(chunkPosition, chunk)
      },
      BiConsumer { chunkPosition: ChunkPos, chunk: LevelChunk ->
         listener.onChunkUnloaded(chunkPosition, chunk)
      },
      BiConsumer { position: BlockPos, state: BlockState ->
         listener.onBlockUpdated(position, state)
      }
   )

   fun start() {
      if (this.started) {
         return
      }
      this.started = true
      executor = createExecutor(workerCount)
      ClientAccess.eventBus.subscribe(this)
   }

   fun scanLoadedChunks() {
      val loadCallback = chunkLoadCallback ?: return

      for (chunk in WorldUtils.getLoadedChunks()) {
         dispatch { loadCallback.accept(chunk.pos, chunk) }
      }
   }

   fun restartExecutor() {
      this.executor?.shutdownNow()
      executor = createExecutor(workerCount)
   }

   fun getExecutor(): ExecutorService? {
      return executor
   }

   @EventHandler
   private fun onClientChunkCacheReplaceWithPacketDataEvent(
      event: ClientChunkCacheReplaceWithPacketDataEvent
   ) {
      val loadCallback = chunkLoadCallback ?: return
      dispatch {
         val chunkForPosition = event.chunk
         val chunkPosition = chunkForPosition.pos
         loadCallback.accept(chunkPosition, event.chunk)
      }
   }

   @EventHandler
   fun onPacketReceive(event: PacketEvent.Receive) {
      if (ClientAccess.collectiveMinecraft.level != null) {
         val updateCallback = blockUpdateCallback
         if (updateCallback != null) {
            if (event.packet is ClientboundBlockUpdatePacket) {
               val blockPacket = event.packet as ClientboundBlockUpdatePacket
               dispatch {
                  updateCallback.accept(blockPacket.pos, blockPacket.blockState)
               }
               return
            }
         }

         if (updateCallback != null) {
            if (event.packet is ClientboundSectionBlocksUpdatePacket) {
               val sectionPacket = event.packet as ClientboundSectionBlocksUpdatePacket
               dispatch {
                  sectionPacket.runUpdates { position, state ->
                     updateCallback.accept(position.immutable(), state)
                  }
               }
               return
            }
         }

         val unloadCallback = chunkUnloadCallback
         if (unloadCallback != null) {
            if (event.packet is ClientboundForgetLevelChunkPacket) {
               val forgetPacket = event.packet as ClientboundForgetLevelChunkPacket
               val extractedChunkPosition = forgetPacket.pos

               val copiedChunkPosition = ChunkPos(extractedChunkPosition.x, extractedChunkPosition.z)
               val chunkX = copiedChunkPosition.x
               val chunkZ = copiedChunkPosition.z
               val level = ClientAccess.collectiveMinecraft.level ?: return
               val chunk = level.getChunk(chunkX, chunkZ)
               dispatch { unloadCallback.accept(copiedChunkPosition, chunk) }
            }
         }
      }
   }

   private fun dispatch(action: Runnable) {
      val currentExecutor = this.executor
      if (workerCount == 0 || currentExecutor == null) {
         action.run()
         return
      }
      try {
         currentExecutor.execute(action)
      } catch (rejected: java.util.concurrent.RejectedExecutionException) {
         // executor уже остановлен (stop/restart) — событие пропускается, а не роняет обработчик
      }
   }

   fun stop() {
      this.started = false
      ClientAccess.eventBus.unsubscribe(this)
      this.executor?.shutdownNow()
      executor = null
   }

   companion object {
      private fun createExecutor(requestedWorkers: Int): ExecutorService {
         val workers = maxOf(requestedWorkers, 1)
         return Executors.newFixedThreadPool(workers)
      }
   }

   interface Listener {
      fun onChunkLoaded(chunkPosition: ChunkPos, chunk: LevelChunk)

      fun onChunkUnloaded(chunkPosition: ChunkPos, chunk: LevelChunk)

      fun onBlockUpdated(position: BlockPos, state: BlockState)
   }
}
