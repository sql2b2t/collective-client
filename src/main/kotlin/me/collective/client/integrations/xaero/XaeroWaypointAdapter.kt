package me.collective.client.integrations.xaero

import java.util.ArrayList
import net.minecraft.core.BlockPos
import xaero.common.minimap.waypoints.Waypoint
import xaero.hud.minimap.BuiltInHudModules
import xaero.hud.minimap.module.MinimapSession
import xaero.hud.minimap.waypoint.WaypointColor
import xaero.hud.minimap.waypoint.set.WaypointSet
import xaero.hud.minimap.world.MinimapWorld
import xaero.hud.minimap.world.MinimapWorldManager
import xaero.hud.module.HudModule

class XaeroWaypointAdapter : WaypointAdapter {
   override fun addWaypoint(name: String, position: BlockPos) {

      val waypointSet = getCurrentWaypointSet()
      if (waypointSet != null) {
         val waypoint = Waypoint(
            waypointX(position), position.y, position.z, name, getWaypointSymbol(name), WaypointColor.getRandom()
         )
         waypointSet.add(waypoint)
      }
   }

   override fun removeWaypoints(name: String) {

      val waypointSet = getCurrentWaypointSet()
      if (waypointSet != null) {
         val matchingWaypoints = ArrayList<Waypoint>()
         val iterator = waypointSet.waypoints.iterator()

         while (iterator.hasNext()) {
            val waypoint = iterator.next() as Waypoint
            if (waypoint.name == name) {
               matchingWaypoints.add(waypoint)
            }
         }

         waypointSet.removeAll(matchingWaypoints)
      }
   }

   override fun isAvailable(): Boolean {
      return true
   }

   private fun waypointX(position: BlockPos): Int {
      return position.x
   }

   private fun getWaypointSymbol(name: String): String {
      val nameLength = name.length
      val maximumLength = 1
      val symbolLength = minOf(maximumLength, nameLength)
      val startIndex = 0
      return name.substring(startIndex, symbolLength)
   }

   private fun getCurrentWaypointSet(): WaypointSet? {

      val world = getCurrentWorld()
      if (world == null) {
         return null
      }

      val setName = WAYPOINT_SET_NAME
      return getOrCreateWaypointSet(world, setName)
   }

   private fun getCurrentWorld(): MinimapWorld? {
      val minimapModule: HudModule<MinimapSession> = BuiltInHudModules.MINIMAP
      val session = minimapModule.currentSession as MinimapSession?
      if (session == null) {
         return null
      }

      val worldManager: MinimapWorldManager = session.worldManager
      return worldManager.currentWorld
   }

   private fun getOrCreateWaypointSet(
      world: MinimapWorld, name: String
   ): WaypointSet? {
      var waypointSet = world.getWaypointSet(name)
      if (waypointSet == null) {
         world.addWaypointSet(name)
         waypointSet = world.getWaypointSet(name)
      }

      return waypointSet
   }

   private companion object {
      const val WAYPOINT_SET_NAME: String = "Collective Waypoints"
   }
}
