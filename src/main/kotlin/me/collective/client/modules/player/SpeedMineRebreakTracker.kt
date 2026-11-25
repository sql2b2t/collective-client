package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ServerStateManager
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.WorldUtils

import java.util.function.BiConsumer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class SpeedMineRebreakTracker(private val speedMineModule: SpeedMineModule) : ClientAccess {
   private val rebreakTimer: ElapsedTimer = ElapsedTimer()
   private var tracking: Boolean = false
   private var pos: BlockPos? = null

   fun validateTarget() {
      if (this.tracking && this.pos != null) {
         val trackedPos = this.pos
         var validTarget: Boolean = false
         if (WorldUtils.canMineBlock(trackedPos)) {
            val currentPos = this.pos
            validTarget = !WorldUtils.isAir(currentPos!!)
         }
         if (!validTarget) {
            this.reset()
         }

         if (this.pos != null) {
            val positionToCompare = this.pos
            val module: SpeedMineModule = this.speedMineModule

            if (!positionToCompare!!.equals(module.miningPos)) {
               this.reset()
            }
         }
      }
   }

   fun hasRebreakDelayElapsed(): Boolean {
      if (this.tracking && this.pos != null) {
         // Миллисекунды → тики (1 тик = 50 мс). Множитель 15 превращал
         // задержку в 15×RTT и убивал смысл мгновенного rebreak.
         val latencyTicks: Double = ((ClientContext.serverStateManager.getLatencyMillis()) / 5e+01F).toDouble()

         var delayMillisScratch: Long = (Math.ceil(latencyTicks) * 50.0).toLong()

         val minimumDelayMillis: Long = MINIMUM_REBREAK_DELAY_MILLIS
         val latencyDelayMillis: Long = delayMillisScratch
         delayMillisScratch = Math.max(latencyDelayMillis, minimumDelayMillis)
         val clampedDelayMillis: Float = delayMillisScratch.toFloat()
         val tickRateTracker: ServerStateManager = ClientContext.serverStateManager

         delayMillisScratch = (clampedDelayMillis / tickRateTracker.getTickRateScale()).toLong()
         val scaledDelayMillis: Float = delayMillisScratch.toFloat()
         val instantDelay: Float? = this.speedMineModule.instantDelaySetting.value
         delayMillisScratch = (scaledDelayMillis + instantDelay!!).toLong()
         return this.rebreakTimer.hasElapsed(delayMillisScratch)
      } else {
         return false
      }
   }

   fun reset() {
      this.tracking = false
      this.pos = null
   }

   fun handleBlockUpdates(
      event: PacketEvent.Receive
   ) {
      if (event.packet is ClientboundBlockUpdatePacket) {
         val blockUpdate = event.packet as ClientboundBlockUpdatePacket
         if (blockUpdate.blockState.isAir) {
            if (blockUpdate.pos.equals(this.pos)) {
               val minecraft: Minecraft = ClientAccess.collectiveMinecraft
               val resetTask = Runnable { this.reset() }
               minecraft.executeIfPossible(resetTask)
            }
         }
      }

      if (event.packet is ClientboundSectionBlocksUpdatePacket) {
         val sectionUpdate = event.packet as ClientboundSectionBlocksUpdatePacket
         val resetOnAirUpdate = BiConsumer<BlockPos, BlockState> { updatedPos: BlockPos, updatedState: BlockState ->
            if (updatedPos.equals(this.pos)) {
               if (updatedState.isAir) {
                  val minecraft: Minecraft = ClientAccess.collectiveMinecraft
                  val resetTask = Runnable { this.reset() }
                  minecraft.executeIfPossible(resetTask)
               }
            }
         }
         sectionUpdate.runUpdates(resetOnAirUpdate)
      }

      if (event.packet is ClientboundAddEntityPacket) {
         val entitySpawn = event.packet as ClientboundAddEntityPacket
         val spawnCheckPosition = this.pos
         if (spawnCheckPosition == null) {
            return
         }

         val trackedBounds = AABB(spawnCheckPosition)
         val spawnPosition = Vec3(entitySpawn.getX(), entitySpawn.getY(), entitySpawn.getZ())
         if (!trackedBounds.contains(spawnPosition)) {
            return
         }

         val minecraft: Minecraft = ClientAccess.collectiveMinecraft
         val resetTask = Runnable { this.reset() }
         minecraft.execute(resetTask)
      }
   }

   fun startTracking(blockPos: BlockPos) {
      if (!this.tracking) {
         this.tracking = true
         this.pos = blockPos

         this.rebreakTimer.reset()
      }
   }

   companion object {
      private const val MINIMUM_REBREAK_DELAY_MILLIS = 750L
   }

}
