package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import net.minecraft.core.BlockPos

enum class ObstacleAlignmentDirection : ClientAccess {
   XP,
   XN,
   ZP,
   ZN,
   XP_ZP,
   XN_ZP,
   XP_ZN,
   XN_ZN;

   fun positionAtOffset(offset: Int): BlockPos {
      return when (this) {
         XP, XN -> {
            val axisPlayer = ClientAccess.collectiveMinecraft.player!!
            val x = axisPlayer.x
            // Z-координата берётся у игрока: раньше обнулялась и поиск шёл у колонки z = 0.
            BlockPos.containing(if (this == XP) x + offset else x - offset, 0.0, axisPlayer.z)
         }
         ZP, ZN -> {
            val axisPlayer = ClientAccess.collectiveMinecraft.player!!
            val z = axisPlayer.z
            // Аналогично: X-координата раньше обнулялась.
            BlockPos.containing(axisPlayer.x, 0.0, if (this == ZP) z + offset else z - offset)
         }
         XP_ZP -> BlockPos.containing(projectedX() + offset, 0.0, projectedZ() + offset)
         XN_ZP -> BlockPos.containing(projectedX() - offset, 0.0, projectedZ() + offset)
         XP_ZN -> BlockPos.containing(projectedX() + offset, 0.0, projectedZ() - offset)
         XN_ZN -> BlockPos.containing(projectedX() - offset, 0.0, projectedZ() - offset)
      }
   }

   companion object {
      private fun projectedX(): Double {
         val coordinateMagnitude = maximumHorizontalCoordinate().toDouble()
         val playerX = ClientAccess.collectiveMinecraft.player!!.x
         return coordinateMagnitude * Math.signum(playerX)
      }

      private fun projectedZ(): Double {
         val coordinateMagnitude = maximumHorizontalCoordinate().toDouble()
         val playerZ = ClientAccess.collectiveMinecraft.player!!.z
         return coordinateMagnitude * Math.signum(playerZ)
      }

      private fun maximumHorizontalCoordinate(): Int {
         return Math.max(Math.abs(ClientAccess.collectiveMinecraft.player!!.x), Math.abs(ClientAccess.collectiveMinecraft.player!!.z)).toInt()
      }

      @JvmStatic
      fun fromYaw(yaw: Float): ObstacleAlignmentDirection? {
         return when (RotationManager.getYawSector(yaw)) {
            0 -> ZP
            1 -> XN_ZP
            2 -> XN
            3 -> XN_ZN
            4 -> ZN
            5 -> XP_ZN
            6 -> XP
            7 -> XP_ZP
            else -> null
         }
      }
   }
}
