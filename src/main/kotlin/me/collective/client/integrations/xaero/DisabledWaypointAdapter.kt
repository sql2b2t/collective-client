package me.collective.client.integrations.xaero

import net.minecraft.core.BlockPos

class DisabledWaypointAdapter : WaypointAdapter {
   override fun removeWaypoints(name: String) {
   }

   override fun isAvailable(): Boolean {
      return false
   }

   override fun addWaypoint(name: String, position: BlockPos) {
   }
}
