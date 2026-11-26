package me.collective.client.modules.player

import me.collective.client.ClientAccess

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

data class ScaffoldPlacementGeometry(
   val supportDirection: Direction?,
   val aimPosition: Vec3,
   val supportShape: VoxelShape
) : ClientAccess {
   companion object {
      @JvmStatic
      fun fromTarget(
         target: ScaffoldPlacementTarget, closestToPlayer: Boolean
      ): ScaffoldPlacementGeometry {
         if (target.airPlace) {
            val airTargetBounds: AABB = AABB(target.position)
            val airCenter: Vec3 = Vec3.atCenterOf(target.position)
            return ScaffoldPlacementGeometry(null, airCenter, Shapes.create(airTargetBounds))
         }

         val supportDirection: Direction = target.side!!
         val aimPositionResult: Vec3 = if (closestToPlayer) {
            val supportPosition: BlockPos = target.position.relative(supportDirection)
            closestPointAtBlockBase(supportPosition)
         } else {
            Vec3.atCenterOf(target.position.relative(supportDirection))
         }

         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val supportBlockPosition: BlockPos = target.position.relative(supportDirection)
         val supportState = level.getBlockState(supportBlockPosition)
         val shapeLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val shapePosition: BlockPos = BlockPos.ZERO
         val supportShape: VoxelShape = supportState.getCollisionShape(shapeLevel, shapePosition)
         return ScaffoldPlacementGeometry(supportDirection, aimPositionResult, supportShape)
      }

      private fun closestPointAtBlockBase(position: BlockPos): Vec3 {
         return Vec3(closestX(position), position.getY().toDouble(), closestZ(position))
      }

      private fun closestX(position: BlockPos): Double {
         val playerX: Double = ClientAccess.collectiveMinecraft.player!!.getX()
         val minimumX: Double = position.getX().toDouble()
         val maximumX: Double = (position.getX() + 1).toDouble()
         return Mth.clamp(playerX, minimumX, maximumX)
      }

      private fun closestZ(position: BlockPos): Double {
         val playerZ: Double = ClientAccess.collectiveMinecraft.player!!.getZ()
         val minimumZ: Double = position.getZ().toDouble()
         val maximumZ: Double = (position.getZ() + 1).toDouble()
         return Mth.clamp(playerZ, minimumZ, maximumZ)
      }

   }
}
