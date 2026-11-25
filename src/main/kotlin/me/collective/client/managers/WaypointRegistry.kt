package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.settings.JsonConfigurable
import me.collective.client.utils.world.Waypoint

class WaypointRegistry : AbstractRegistry<Waypoint>(ArrayList()), ClientAccess, JsonConfigurable {
   init {
      ClientAccess.eventBus.subscribe(this)
   }

   override fun register(entry: Any): Boolean {
      return this.registerWaypoint(entry as Waypoint)
   }

   override fun unregister(entry: Any): Boolean {
      return this.unregisterWaypoint(entry as? Waypoint)
   }

   val visible: List<Waypoint>
      @JvmName("getVisibleProp")
      get() = this.getVisible()

   fun getVisible(): List<Waypoint> {
      if (this.isWorldUnavailable()) {
         return emptyList()
      }
      val player = ClientAccess.collectiveMinecraft.player ?: return emptyList()
      val serverData = player.connection.serverData
      val currentServer = serverData?.ip ?: "singleplayer"

      return this.entries
         .filter { candidate ->
            currentServer.equals(candidate.server, ignoreCase = true)
         }
         .sortedBy { waypoint ->
            player.distanceToSqr(waypoint.x, waypoint.y, waypoint.z)
         }
   }

   override fun fromJson(json: JsonElement) {
      if (!json.isJsonObject) {
         return
      }
      val configObject = json.asJsonObject
      if (!configObject.has("waypoints")) {
         return
      }
      val waypointArray = configObject.getAsJsonArray("waypoints")
      for (element in waypointArray) {
         try {
            this.entries.add(ClientAccess.gson.fromJson(element, Waypoint::class.java))
         } catch (loadFailure: Exception) {
            loadFailure.printStackTrace()
         }
      }
   }

   override fun toJson(): JsonElement {
      val waypointArray = JsonArray()
      val configObject = JsonObject()
      for (waypoint in this.entries) {
         waypointArray.add(ClientAccess.gson.toJsonTree(waypoint))
      }
      configObject.add("waypoints", waypointArray)
      return configObject
   }

   override fun getConfigName(): String = "waypoints.json"

   fun registerWaypoint(waypoint: Waypoint): Boolean {
      for (existingWaypoint in this.getRegistry()) {
         val existingName = existingWaypoint.getName()
         val requestedName = waypoint.getName()
         if (existingName.equals(requestedName, ignoreCase = true)) {
            val existingServer = existingWaypoint.server
            val requestedServer = waypoint.server
            if (existingServer.equals(requestedServer, ignoreCase = true)) {
               return false
            }
         }
      }
      return this.getRegistry().add(waypoint)
   }

   fun unregisterWaypoint(waypoint: Waypoint?): Boolean {
      return this.getRegistry().removeIf { candidate ->
         if (candidate != null && waypoint != null) {
            val candidateName = candidate.getName()
            val requestedName = waypoint.getName()
            if (candidateName.equals(requestedName, ignoreCase = true)) {
               val candidateServer = candidate.server
               val requestedServer = waypoint.server
               if (candidateServer.equals(requestedServer, ignoreCase = true)) {
                  return@removeIf true
               }
            }
         }
         false
      }
   }
}
