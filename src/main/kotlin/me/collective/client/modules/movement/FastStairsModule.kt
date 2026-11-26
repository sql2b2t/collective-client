package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.world.WorldUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.phys.AABB

class FastStairsModule : Module("FastStairs", "Makes you move faster on stair blocks.", ModuleCategory.MOVEMENT) {
   companion object {
      private val scanPosition: MutableBlockPos = MutableBlockPos()
   }

   private val jumpTimer = ElapsedTimer()
   private var roseSincePreviousTick: Boolean = false
   private var capturedSpeed: Float = 0.0f

   private fun hasNearbyStairs(center: BlockPos): Boolean {
      for (x in -2..2) {
         for (z in -2 until 2) {
            scanPosition.set(center.x + x, center.y, center.z + z)
            if (WorldUtils.getBlock(scanPosition) is StairBlock) {
               return true
            }
         }
      }
      return false
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      this.capturedSpeed = ClientContext.movementSpeedTracker.currentSpeed.toFloat()
      val previousY = ClientAccess.collectiveMinecraft.player!!.yo
      this.roseSincePreviousTick = previousY < ClientAccess.collectiveMinecraft.player!!.y
   }

   @EventHandler
   private fun onLocalPlayerPreTickEvent(event: LocalPlayerPreTickEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val feetBounds: AABB = player.boundingBox.move(0.0, -0.1, 0.0)
      if (BlockPos.betweenClosedStream(feetBounds).noneMatch { pos -> WorldUtils.getBlock(pos) is StairBlock }) {
         return
      }
      val playerPosition = BlockPos.containing(player.x, player.y, player.z)
      if (!player.onGround() || !this.roseSincePreviousTick || player.isCreative || player.isSpectator) {
         return
      }
      if (!this.hasNearbyStairs(playerPosition.above()) || this.capturedSpeed < 20.0f) {
         return
      }
      if (!this.jumpTimer.hasElapsedAndReset(150L)) {
         // Прыжок не чаще раза в 150 мс (раньше условие было инвертировано и спамило прыжками).
         return
      }
      ClientContext.inputOverrides.requestJump(true)
   }
}
