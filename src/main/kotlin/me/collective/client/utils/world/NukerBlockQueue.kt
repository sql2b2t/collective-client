package me.collective.client.utils.world

import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.inventory.ExtraBreakTracker

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.player.NukerModule
import me.collective.client.modules.player.SpeedMineModule

import java.util.ArrayList
import java.util.Collections
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB

class NukerBlockQueue(private val nukerModule: NukerModule) : ClientAccess {
   private val positions: MutableList<BlockPos> = ArrayList()

   fun pruneInvalidPositions() {
      positions.removeIf { blockPos -> shouldRemovePosition(blockPos) }
   }

   fun getPositions(): List<BlockPos> {
      return positions
   }

   private fun shouldRemovePosition(blockPos: BlockPos): Boolean {
      val speedMineForToggle = speedMineModule
      if (speedMineForToggle!!.isToggled()) {
         val pendingTaskSnapshot: ExtraBreakTracker = speedMineForToggle.extraBreakTracker

         if (pendingTaskSnapshot.isTracked(blockPos)) {
            return true
         }
      }

      if (!WorldUtils.canMineBlock(blockPos)) {
         return true
      }

      val player = ClientAccess.collectiveMinecraft.player ?: return true
      val eyePosition = player.eyePosition
      val blockBounds = AABB(blockPos)
      val closestPoint = BoundingBoxUtils.clampToBounds(eyePosition, blockBounds)
      val distance = eyePosition.distanceTo(closestPoint)
      val configuredRange = nukerModule.rangeSetting.value
      return distance > configuredRange.toDouble()
   }

   fun enqueue(blockPos: BlockPos) {
      if (!positions.contains(blockPos)) {
         positions.add(blockPos)
      }
   }

   fun getNextPositions(includeSecond: Boolean): List<BlockPos> {
      if (positions.isEmpty()) {
         return Collections.emptyList()
      }

      if (positions.size == 1) {
         return ArrayList(listOf(positions.first()))
      }

      return ArrayList(
         if (includeSecond)
            listOf(positions.first(), positions[1])
         else
            Collections.singletonList(positions.first())
      )
   }

   companion object {
      private var speedMineModule: SpeedMineModule? = null

      @JvmStatic
      fun bindModules(modules: ModuleRegistry) {
         speedMineModule = modules.getModuleOrNull(SpeedMineModule::class.java)
      }
   }
}
