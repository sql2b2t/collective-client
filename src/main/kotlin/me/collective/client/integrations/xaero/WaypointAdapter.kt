package me.collective.client.integrations.xaero

import net.minecraft.core.BlockPos

interface WaypointAdapter {
   fun removeWaypoints(name: String)

   fun isAvailable(): Boolean

   fun addWaypoint(name: String, position: BlockPos)
}
