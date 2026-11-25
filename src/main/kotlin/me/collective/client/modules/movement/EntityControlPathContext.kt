package me.collective.client.modules.movement

import baritone.api.event.events.BlockChangeEvent
import baritone.api.utils.BetterBlockPos
import dev.babbaj.pathfinder.NetherPathfinder
import dev.babbaj.pathfinder.Octree
import dev.babbaj.pathfinder.PathSegment
import java.lang.ref.SoftReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class EntityControlPathContext(val level: ClientLevel, val height: Int) {
   private val minY: Int = level.minY
   private var lastCullMillis: Long = System.currentTimeMillis()
   private val executor: ExecutorService = Executors.newSingleThreadExecutor { task -> Thread(task, "Collective entity pathfinder") }
   private val pointer: Long

   init {
      this.pointer = try {
         NetherPathfinder.newContext(0L)
      } catch (failure: Throwable) {
         0L
      }
   }

   companion object {
      // shortcut: the bundled Baritone uses the Nether-only native API, adapt this boundary when its distribution changes.
      @JvmStatic
      fun isSupported(): Boolean {
         try {
            return NetherPathfinder.isThisSystemSupported()
         } catch (failure: Throwable) {
            return false
         }
      }

      @JvmStatic
      fun isHazard(state: BlockState): Boolean {
         return !state.fluidState.isEmpty() || state.`is`(BlockTags.FIRE) || state.`is`(BlockTags.CAMPFIRES) ||
         state.`is`(Blocks.MAGMA_BLOCK) || state.`is`(Blocks.COBWEB) || state.`is`(Blocks.POWDER_SNOW) || state.`is`(Blocks.SWEET_BERRY_BUSH)
      }
   }

   fun isValid(): Boolean {
      return this.pointer != 0L
   }

   fun loadChunks(center: ChunkPos) {
      for (x in (center.x - 40)..(center.x + 40)) {
         for (z in (center.z - 40)..(center.z + 40)) {
            this.loadChunk(x, z)
         }
      }
   }

   fun loadChunk(x: Int, z: Int) {
      val chunk: LevelChunk? = this.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false)
      if (chunk != null) {
         val reference: SoftReference<LevelChunk> = SoftReference(chunk)
         this.executor.execute {
            val loaded: LevelChunk? = reference.get()
            if (loaded != null) {
               this.packChunk(loaded)
            }
         }
      }
   }

   fun onBlockChange(event: BlockChangeEvent) {
      val changes = event.blocks
      this.executor.execute {
         for (change in changes) {
            val position: BlockPos = change.first()
            val state: BlockState = change.second()
            this.setBlock(position, !state.isAir)
            if (isHazard(state)) {
               for (y in 1..2) {
                  this.setBlock(position.above(y), true)
               }
            }
         }
      }
   }

   fun markBlocked(center: BlockPos, radius: Int) {
      this.executor.execute {
         for (position in BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            this.setBlock(position, true)
         }
      }
   }

   fun cullChunks(center: ChunkPos) {
      if (System.currentTimeMillis() - this.lastCullMillis >= 180000L) {
         this.lastCullMillis = System.currentTimeMillis()
         this.executor.execute {
            try {
               NetherPathfinder.cullFarChunks(this.pointer, center.x, center.z, 5000)
            } catch (failure: Throwable) {
            }
         }
      }
   }

   @Suppress("UNCHECKED_CAST")
   fun findPath(from: BlockPos, to: BlockPos): CompletableFuture<Route> {
      val fromY: Int = Mth.clamp(from.getY() - this.minY, 0, this.height - 1)
      val toY: Int = Mth.clamp(to.getY() - this.minY, 0, this.height - 1)
      return CompletableFuture.supplyAsync<Route?>({
         var path: PathSegment? = null
         try {
            path = NetherPathfinder.pathFind(this.pointer, from.getX(), fromY, from.getZ(), to.getX(), toY, to.getZ(), true, false, 10000, true)
         } catch (failure: Throwable) {
            return@supplyAsync null
         }
         if (path == null) {
            return@supplyAsync null
         }
         val nodes: ArrayList<Node> = ArrayList(path.packed.size)
         val visited: HashMap<BlockPos, Int> = HashMap()
         for (packed in path.packed) {
            val position: BetterBlockPos = BetterBlockPos.deserializeFromLong(packed)
            val size: Int = Mth.clamp(2 * Integer.lowestOneBit(position.getX() or position.getY() or position.getZ()), 2, 16)
            val node = Node(BlockPos(position.getX(), position.getY() + this.minY, position.getZ()), size)
            val previous: Int? = visited[node.position]
            if (previous != null) {
               while (nodes.size > previous + 1) {
                  visited.remove(nodes.removeLast().position)
               }
            } else {
               visited[node.position] = nodes.size
               nodes.add(node)
            }
         }
         Route(nodes, path.finished)
      }, this.executor).exceptionally { null } as CompletableFuture<Route>
   }

   fun cancel() {
      if (this.isValid()) {
         try {
            NetherPathfinder.cancel(this.pointer)
         } catch (failure: Throwable) {
         }
      }
   }

   fun close() {
      this.cancel()
      this.executor.execute {
         if (this.isValid()) {
            try {
               NetherPathfinder.freeContext(this.pointer)
            } catch (failure: Throwable) {
            }
         }
      }
      this.executor.shutdown()
   }

   private fun setBlock(position: BlockPos, solid: Boolean) {
      val y: Int = position.getY() - this.minY
      if (y >= 0 && y < 128) {
         try {
            val chunk: Long = NetherPathfinder.getChunkPointer(this.pointer, position.getX() shr 4, position.getZ() shr 4)
            if (chunk != 0L) {
               Octree.setBlock(chunk, position.getX() and 15, y, position.getZ() and 15, solid)
            }
         } catch (failure: Throwable) {
         }
      }
   }

   private fun packChunk(chunk: LevelChunk) {
      try {
         val nativeChunk: Long = NetherPathfinder.getOrCreateChunk(this.pointer, chunk.getPos().x, chunk.getPos().z)
         if (nativeChunk == 0L) {
            return
         }
         val sections: Array<LevelChunkSection> = chunk.getSections()
         for (section in 0 until Math.min(sections.size, 128 shr 4)) {
            val blocks: LevelChunkSection? = sections[section]
            if (blocks == null || blocks.hasOnlyAir()) {
               continue
            }
            val sectionY: Int = section shl 4
            for (y in 0 until 16) {
               for (z in 0 until 16) {
                  for (x in 0 until 16) {
                     val state: BlockState = blocks.getBlockState(x, y, z)
                     if (!state.isAir) {
                        Octree.setBlock(nativeChunk, x, sectionY + y, z, true)
                        if (isHazard(state)) {
                           var above: Int = 1
                           while (above <= 2 && sectionY + y + above < 128) {
                              Octree.setBlock(nativeChunk, x, sectionY + y + above, z, true)
                              above++
                           }
                        }
                     }
                  }
               }
            }
         }
      } catch (failure: Throwable) {
      }
   }

   class Node(val position: BlockPos, val size: Int) {
      fun bounds(): AABB {
         return AABB.ofSize(Vec3.atLowerCornerOf(this.position), this.size.toDouble(), this.size.toDouble(), this.size.toDouble())
      }
   }

   class Route(nodes: List<Node>, finished: Boolean) {
      private val routeNodes: List<Node> = nodes
      private val routeFinished: Boolean = finished

      fun nodes(): List<Node> {
         return this.routeNodes
      }

      fun finished(): Boolean {
         return this.routeFinished
      }
   }
}
