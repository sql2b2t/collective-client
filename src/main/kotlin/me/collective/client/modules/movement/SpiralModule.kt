package me.collective.client.modules.movement

import me.collective.client.managers.RotationManager
import me.collective.client.utils.math.Vec3Utils


import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import java.util.ArrayList
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

class SpiralModule : Module("Spiral", ModuleCategory.MOVEMENT) {
   private val sizeSetting: Setting<Int>
   private val waypoints: MutableList<Vec3>
   private var waypointIndex: Int = 0

   init {
      this.sizeSetting = this.add(NumberSetting<Int>("Size", 5, 1, 20))
      this.waypoints = ArrayList<Vec3>(5000)
   }

   override fun onEnable() {
      this.waypointIndex = 0
      this.waypoints.clear()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player ?: return
      var position: Vec3 = player.position()
      var yaw = 0.0f
      var index = 0

      while (true) {
         if (index >= 5000 * 4) {
            return
         }

         this.waypoints.add(position)
         val direction: Vec3 = Vec3.directionFromRotation(0.0f, yaw)
         val setting: Setting<Int> = this.sizeSetting
         val size: Int = setting.value
         val radius: Int = index * size
         val distance: Double = radius * 16.0
         val offset: Vec3 = direction.scale(distance)
         position = position.add(offset)
         val nextYaw = yaw + 90.0f
         yaw = Mth.wrapDegrees(nextYaw)
         index++
      }
   }

   @EventHandler
   private fun onWorldRender(event: WorldRenderEvent.Main) {
      var index = Math.max(this.waypointIndex, 1)
      while (true) {
         val startIndex = Math.max(this.waypointIndex, 1)
         if (index >= startIndex + 2) {
            return
         }

         val previousPoint: Vec3 = this.waypoints[index - 1]
         val point: Vec3 = this.waypoints[index]
         index++
      }
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      val index = this.waypointIndex
      val waypoint: Vec3 = this.waypoints[index]
      val yaw: Float = RotationManager.getRotationsToPosition(waypoint)[0]
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      player.yRot = yaw
      val position: Vec3 = ClientAccess.collectiveMinecraft.player!!.position()
      val distance: Float = Vec3Utils.horizontalDistance(waypoint, position)
      if (distance < 3.0f) {
         this.waypointIndex++
      }
   }
}
