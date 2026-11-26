package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Range calculation for the normal and movement-synchronized mining modes. */
internal object SpeedMineRange {
   fun isInRange(blockPos: BlockPos, range: Float): Boolean {
      if (!RotationManager.isMovementSyncEnabled()) {
         val eyePosition: Vec3 = ClientAccess.collectiveMinecraft.player!!.getEyePosition()
         val blockCenter: Vec3 = Vec3.atCenterOf(blockPos)
         return eyePosition.distanceTo(blockCenter) <= range
      }

      val scalePlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val scaleAttribute = Attributes.SCALE
      val playerScale: Float = scalePlayer.getAttributeValue(scaleAttribute).toFloat()
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!

      val feetPosition: Vec3 = BoundingBoxUtils.getInterpolationBounds(player).bottomCenter
      val blockBounds = AABB(blockPos)

      var nearestDistance: Double = Double.MAX_VALUE

      for (sampleHeight in PlayerStateUtils.getBodySampleHeights()) {
         val sampleY: Double = (sampleHeight * playerScale).toDouble()
         val samplePoint: Vec3 = feetPosition.add(0.0, sampleY, 0.0)
         val sampleDistance: Double = WorldUtils.distanceToBounds(samplePoint, blockBounds)
         if (sampleDistance < nearestDistance) {
            nearestDistance = sampleDistance
         }
      }

      return nearestDistance <= range
   }
}
