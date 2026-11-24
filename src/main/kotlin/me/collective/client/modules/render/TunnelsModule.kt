package me.collective.client.modules.render

import me.collective.client.ClientAccess
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.managers.RotationManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.ChunkScanner
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting

import java.awt.Color
import java.util.ArrayList
import java.util.Collections
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.util.thread.BlockableEventLoop
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import me.collective.client.utils.world.WorldUtils

class TunnelsModule : Module {
   private val verticalsSetting: Setting<Boolean>
   private val lineWidthSetting: Setting<Float>
   private val heightSetting: Setting<Float>
   private val minLengthSetting: Setting<Int>
   private val fillSetting: Setting<Color>
   private val outlineSetting: Setting<Color>
   private val mergeTimer: ElapsedTimer
   private val pendingTunnels: Queue<TunnelsModule.Tunnel>
   @Volatile
   private var tunnels: MutableList<TunnelsModule.Tunnel>
   private val mergeInProgress: AtomicBoolean
   private val chunkScanner: ChunkScanner

   init {
      this.verticalsSetting = this.add(BooleanSetting("Verticals", true))
      this.lineWidthSetting = this.add(NumberSetting<Float>("LineWidth", 1.0F, 0.1F, 3.0F))
      this.heightSetting = this.add(NumberSetting<Float>("Height", 0.1F, -1.0F, 2.0F).withUnit("m"))
      this.minLengthSetting = this.add(NumberSetting<Int>("MinLength", 4, 1, 16)
         .withUnit("m"))
      this.fillSetting = this.add(ColorSetting("Fill", Color(1358888960, true)))
      this.outlineSetting = this.add(ColorSetting("Outline", Color(-65536, true)))
      this.mergeTimer = ElapsedTimer()
      this.pendingTunnels = ConcurrentLinkedQueue()
      this.tunnels = Collections.emptyList()
      this.mergeInProgress = AtomicBoolean(false)
      this.chunkScanner = ChunkScanner(
         1,
         BiConsumer { chunkPosition: ChunkPos, chunk: LevelChunk ->
            val client: BlockableEventLoop<*> = ClientAccess.collectiveMinecraft
            client.executeIfPossible {
               this.scanChunk(chunkPosition, chunk)
            }
         },
         BiConsumer { unloadedChunkPosition: ChunkPos, unusedChunk: LevelChunk ->
            val client: BlockableEventLoop<*> = ClientAccess.collectiveMinecraft
            client.executeIfPossible {
               this.tunnels.removeIf { tunnel ->
                  this.isTunnelCenterInChunk(tunnel, unloadedChunkPosition)
               }
            }
         },
         null
      )
   }

   private fun isNonSolid(position: BlockPos): Boolean {
      val level = ClientAccess.collectiveMinecraft.level ?: return false
      val state = level.getBlockState(position)
      return !state.isSolid()
   }

   private fun isHorizontalTunnelCell(position: BlockPos): Boolean {
      if (!this.isNonSolid(position.above()) ||
         this.isNonSolid(position.below()) ||
         this.isNonSolid(position.above().above())) {
         return false
      }

      if (this.isNonSolid(position.north()) &&
         this.isNonSolid(position.south()) &&
         this.isNonSolid(position.above().north()) &&
         this.isNonSolid(position.above().south())) {
         return !this.isNonSolid(position.east()) &&
         !this.isNonSolid(position.west()) &&
         !this.isNonSolid(position.above().east()) &&
         !this.isNonSolid(position.above().west())
      }

      return this.isNonSolid(position.east()) &&
         this.isNonSolid(position.west()) &&
         this.isNonSolid(position.above().east()) &&
         this.isNonSolid(position.above().west()) &&
         !this.isNonSolid(position.north()) &&
         !this.isNonSolid(position.south()) &&
         !this.isNonSolid(position.above().north()) &&
         !this.isNonSolid(position.above().south())
   }

   override fun onEnable() {
      val capturedScanner = this.chunkScanner

      capturedScanner.start()
      if (!this.isWorldUnavailable()) {
         val capturedScannerForScan = this.chunkScanner

         capturedScannerForScan.scanLoadedChunks()
      }
   }

   override fun onDisable() {
      this.tunnels.clear()
      this.pendingTunnels.clear()
      val scanner = this.chunkScanner

      scanner.stop()
   }

   private fun isVerticalTunnelCell(position: BlockPos): Boolean {
      val scanVerticals = this.verticalsSetting.value
      if (!scanVerticals) {
         return false
      }

      for (horizontalDirection in Direction.Plane.HORIZONTAL) {
         val sidePosition = position.relative(horizontalDirection)
         val level = ClientAccess.collectiveMinecraft.level ?: return false
         val sideState = level.getBlockState(sidePosition)
         if (!sideState.isSolid()) {
            return false
         }

         val bambooBlock = Blocks.BAMBOO_BLOCK
         if (sideState.`is`(bambooBlock)) {
            return false
         }

         val basaltBlock = Blocks.BASALT
         if (sideState.`is`(basaltBlock)) {
            return false
         }
      }

      for (verticalDirection in Direction.Plane.VERTICAL) {
         val firstVerticalPosition = position.relative(verticalDirection)
         if (!this.isNonSolid(firstVerticalPosition)) {
            return false
         }
         val secondVerticalPosition = firstVerticalPosition.relative(verticalDirection)
         if (!this.isNonSolid(secondVerticalPosition)) {
            return false
         }
      }
      return true
   }

   private fun isTunnelCenterInChunk(
      tunnel: TunnelsModule.Tunnel, chunkPosition: ChunkPos
   ): Boolean {

      val maxX = tunnel.maxX
      val minX = tunnel.minX
      val centerX = Mth.lerpInt(0.5F, minX, maxX).toFloat()

      val maxZ = tunnel.maxZ
      val minZ = tunnel.minZ
      val centerZ = Mth.lerpInt(0.5F, minZ, maxZ).toFloat()
      return centerX >= chunkPosition.minBlockX &&
         centerX <= chunkPosition.maxBlockX &&
         centerZ >= chunkPosition.minBlockZ &&
         centerZ <= chunkPosition.maxBlockZ
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (!this.isWorldUnavailable()) {
         val mergeFlag = this.mergeInProgress
         if (!mergeFlag.get()) {
            val capturedTimer = this.mergeTimer

            if (capturedTimer.hasElapsed(75L)) {
               capturedTimer.reset()
               val busy = true
               val busyFlag = this.mergeInProgress
               busyFlag.set(busy)
               val snapshot = ArrayList(this.tunnels)
               val eyePosition = ClientAccess.collectiveMinecraft.player?.eyePosition
               ClientAccess.executorService.submit(
                  Runnable { this.mergePendingTunnels(snapshot, eyePosition) }
               )
            }
         }
      }
   }

   @EventHandler
   private fun onRenderWorld(event: WorldRenderEvent.Main) {
      if (!this.isWorldUnavailable()) {
         val visibleTunnels = this.tunnels
         for (tunnel in visibleTunnels) {
            val bounds = tunnel.getBounds()
            val horizontalLength = Math.max(bounds.xsize, bounds.zsize)
            val length = Math.max(horizontalLength, bounds.ysize)
            val minimumLength = this.minLengthSetting.value
            if (!(length < minimumLength)) {
               var renderHeight = this.heightSetting.value
               if (tunnel.vertical) {
                  renderHeight = bounds.ysize.toFloat()
               }

               val renderTop = bounds.minY + renderHeight.toDouble()
               val renderBounds = bounds.setMaxY(renderTop)

               if (RotationManager.isVisible(renderBounds)) {
                  val fillPoses = event.poseStack!!
                  WorldBoxRenderer.fillBox(
                     fillPoses, renderBounds, this.fillSetting.value
                  )
                  val outlinePoses = event.poseStack!!
                  val outlineColor = this.outlineSetting.value
                  val lineWidth = this.lineWidthSetting.value
                  WorldBoxRenderer.outlineBox(outlinePoses, renderBounds, outlineColor, lineWidth)
               }
            }
         }
      }
   }

   private fun scanChunk(chunkPosition: ChunkPos, unusedChunk: LevelChunk) {
      val level = ClientAccess.collectiveMinecraft.level ?: return
      val firstY = level.minY + 1

      val topY = WorldUtils.getTopY().toInt()
      val endY = topY - 2
      var localX = 0

      while (true) {

         if (localX >= 16) {
            return
         }

         var localZ = 0

         while (true) {

            if (localZ >= 16) {
               localX++
               break
            }

            val x = chunkPosition.minBlockX + localX
            val z = chunkPosition.minBlockZ + localZ

            var y = firstY
            while (y < endY) {
               val blockY = y
               scanPosition.set(x, blockY, z)

               if (this.isNonSolid(scanPosition)) {

                  if (!this.isVerticalTunnelCell(scanPosition)) {

                     if (this.isHorizontalTunnelCell(scanPosition)) {
                        this.pendingTunnels.add(
                           TunnelsModule.Tunnel(x, y, z, x + 1, y + 1, z + 1, false)
                        )
                     }
                  } else {
                     val verticalStartY = y

                     while (y < endY) {
                        val nextY = y + 1
                        scanPosition.set(x, nextY, z)

                        if (!this.isVerticalTunnelCell(scanPosition)) {
                           break
                        }

                        y++
                     }

                     this.pendingTunnels.add(
                        TunnelsModule.Tunnel(x, verticalStartY, z, x + 1, y + 1, z + 1, true)
                     )
                  }
               }

               y++
            }

            localZ++
         }
      }
   }

   private fun mergePendingTunnels(snapshot: MutableList<TunnelsModule.Tunnel>, eyePosition: Vec3?) {
      try {
         var changed = false

         while (true) {
            val pending = this.pendingTunnels.poll() ?: break
            var mergedPending = false
            var existingIndex = 0

            while (existingIndex < snapshot.size) {
               val existing = snapshot[existingIndex]
               if (existing.canMergeWith(pending)) {
                  snapshot[existingIndex] = existing.merge(pending)
                  mergedPending = true
                  changed = true
                  break
               }
               existingIndex++
            }

            if (!mergedPending) {
               snapshot.add(pending)
               changed = true
            }
         }

         if (changed) {

            val merged = collectMergedTunnels(snapshot)
            snapshot.removeAll(merged)
         }

         if (eyePosition != null) {
            snapshot.removeIf { tunnel ->
               val distanceSquared = tunnel.distanceToCenterSquared(eyePosition)

               distanceSquared > 65536.0
            }
         }
         this.tunnels = snapshot
      } catch (ignoredFailure: Exception) {
      } finally {
         val idle = false
         val busyFlag = this.mergeInProgress
         busyFlag.set(idle)
      }
   }

   private class Tunnel(
      var minX: Int,
      var minY: Int,
      var minZ: Int,
      var maxX: Int,
      var maxY: Int,
      var maxZ: Int,
      val vertical: Boolean
   ) {
      private var cachedBounds: AABB? = null

      fun getBounds(): AABB {
         val boundsSnapshot = this.cachedBounds
         if (boundsSnapshot != null) {
            return boundsSnapshot
         }
         val createdBounds = AABB(
            this.minX.toDouble(),
            this.minY.toDouble(),
            this.minZ.toDouble(),
            this.maxX.toDouble(),
            this.maxY.toDouble(),
            this.maxZ.toDouble()
         )
         this.cachedBounds = createdBounds
         return createdBounds
      }

      fun distanceToCenterSquared(position: Vec3): Double {
         var coordinateMinimum = this.minX.toDouble()
         var coordinateSpan = (this.maxX
            - this.minX).toDouble()

         val centerX = coordinateMinimum + coordinateSpan * 0.5
         coordinateMinimum = this.minY.toDouble()
         coordinateSpan = (this.maxY
            - this.minY).toDouble()

         val centerY = coordinateMinimum + coordinateSpan * 0.5
         coordinateMinimum = this.minZ.toDouble()
         coordinateSpan = (this.maxZ
            - this.minZ).toDouble()

         val centerZ = coordinateMinimum + coordinateSpan * 0.5
         return position.distanceToSqr(centerX, centerY, centerZ)
      }

      fun canMergeWith(other: TunnelsModule.Tunnel): Boolean {
         if (this.vertical != other.vertical) {
            return false
         }

         if (this.vertical) {
            if (this.minX != other.minX || this.maxX != other.maxX) {
               return false
            }
            return this.minZ == other.minZ &&
         this.maxZ == other.maxZ &&
         (this.minY == other.maxY || this.maxY == other.minY)
         }

         if (this.minY != other.minY || this.maxY != other.maxY) {
            return false
         }
         val touchesAlongX = (this.minX == other.maxX || this.maxX == other.minX) &&
         this.minZ == other.minZ &&
         this.maxZ == other.maxZ
         return touchesAlongX ||
         (this.minZ == other.maxZ || this.maxZ == other.minZ) &&
         this.minX == other.minX &&
         this.maxX == other.maxX
      }

      fun merge(other: TunnelsModule.Tunnel): TunnelsModule.Tunnel {
         return TunnelsModule.Tunnel(
            Math.min(this.minX, other.minX),
            Math.min(this.minY, other.minY),
            Math.min(this.minZ, other.minZ),
            Math.max(this.maxX, other.maxX),
            Math.max(this.maxY, other.maxY),
            Math.max(this.maxZ, other.maxZ),
            this.vertical
         )
      }

   }

   companion object {
      private val scanPosition: BlockPos.MutableBlockPos = BlockPos.MutableBlockPos()

      private fun collectMergedTunnels(tunnels: MutableList<TunnelsModule.Tunnel>): Set<TunnelsModule.Tunnel> {
         val merged = HashSet<TunnelsModule.Tunnel>()

         for (index in tunnels.indices) {
            var tunnel = tunnels[index]
            if (!merged.contains(tunnel)) {
               for (candidateIndex in index + 1 until tunnels.size) {
                  val candidate = tunnels[candidateIndex]
                  if (!merged.contains(candidate)) {
                     if (tunnel.canMergeWith(candidate)) {
                        tunnel = tunnel.merge(candidate)
                        tunnels[index] = tunnel
                        merged.add(candidate)
                     }
                  }
               }
            }
         }

         return merged
      }
   }

   constructor() : super("Tunnels", "Highlights dug-out tunnels.", ModuleCategory.RENDER)
}
