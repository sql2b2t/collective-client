package me.collective.client.modules.player

import me.collective.client.ClientAccess
import java.util.ArrayList
import java.util.Comparator
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import net.minecraft.core.Direction.Axis
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

data class ScaffoldPlacementTarget(
   val position: BlockPos,
   val side: Direction?,
   val airPlace: Boolean
) {
   companion object {
      @JvmStatic
      internal fun findOrigin(module: ScaffoldModule): Vec3 {
         val playerBounds: AABB = ClientAccess.collectiveMinecraft.player!!.getBoundingBox()
         var origin: Vec3 = BoundingBoxUtils.getBottomCenter(playerBounds)
         if (module.tellySetting.getValue()!! || module.andromedaSetting.getValue()!!) {
            origin = origin.with(Axis.Y, module.tellyBaseY)
         }
         if (ClientAccess.collectiveMinecraft.player!!.isPassenger()) {
            val vehicleBounds: AABB = ClientAccess.collectiveMinecraft.player!!.getVehicle()!!.getBoundingBox()
            origin = BoundingBoxUtils.getBottomCenter(vehicleBounds)
         }
         return origin
      }

      @JvmStatic
      internal fun findMultiFor(module: ScaffoldModule, size: Int, frequency: Int): List<ScaffoldPlacementTarget> {
         val origin: Vec3 = findOrigin(module)
         val supportY: Double = module.findSupportHeight(origin.y.toFloat())
         val radius: Int = size - 1
         val targets: MutableList<ScaffoldPlacementTarget> = ArrayList()
         for (x in -radius..radius) {
            for (z in -radius..radius) {
               var position: BlockPos = BlockPos.containing(origin.add(x.toDouble(), -origin.y + supportY, z.toDouble()))
               if (module.isSelectedBlockNonCollidable() &&
         !ClientAccess.collectiveMinecraft.level!!.getBlockState(position).canBeReplaced()
               ) {
                  position = position.above()
               }
               if (!module.canPlaceAt(position, module.blockSlot)) {
                  continue
               }
               var side: Direction? = BlockInteractionUtils.findSupportDirection(position, BlockInteractionUtils.isStrictDirectionEnabled())
               if (module.airPlaceSetting.getValue()!!) {
                  side = null
               }
               if (side != null || module.airPlaceSetting.getValue()!!) {
                  targets.add(ScaffoldPlacementTarget(position, side, side == null))
               }
            }
         }
         targets.sortWith(Comparator.comparing { target: ScaffoldPlacementTarget ->
            val center: Vec3 = Vec3.atCenterOf(target.position)
            val deltaX: Double = origin.x - center.x
            val deltaZ: Double = origin.z - center.z
            (deltaX * deltaX + deltaZ * deltaZ).toFloat()
         })
         return targets.stream().limit(frequency.toLong()).toList()
      }

      @JvmStatic
      internal fun findFor(module: ScaffoldModule): ScaffoldPlacementTarget? {
         var position: BlockPos? = null
         val origin: Vec3 = findOrigin(module)

         val collidingBelow: Boolean
         if (ClientAccess.collectiveMinecraft.player!!.isPassenger()) {
            collidingBelow = ClientAccess.collectiveMinecraft.player!!.getVehicle()!!.verticalCollisionBelow
         } else {
            collidingBelow = ClientAccess.collectiveMinecraft.player!!.verticalCollisionBelow
         }

         val sampleY: Float = origin.y.toFloat()

         val supportY: Double = module.findSupportHeight(sampleY)
         var verticalOffset = 0

         while (true) {
            val maximumOffset: Float = if (module.andromedaSetting.getValue()!!) {
               Math.max(module.offsetSetting.getValue()!!, 2.0f)
            } else {
               module.offsetSetting.getValue()!!
            }
            if (verticalOffset >= maximumOffset + 1) {
               break
            }

            var canSearchForward = false
            if (MovementPhysics.isLocalPlayerMoving()) {
               canSearchForward = ClientAccess.collectiveMinecraft.player!!.onGround() || module.andromedaSetting.getValue()!!
            }
            if (!canSearchForward && verticalOffset > 0) {
               break
            }

            val sampleAxis: Direction.Axis = Axis.Y
            val verticalSample: Vec3 = origin.with(sampleAxis, supportY)
            val movementOffset: Vec3 = PlayerStateUtils.getMovementVector().scale(verticalOffset.toDouble())
            position = BlockPos.containing(verticalSample.add(movementOffset))
            var checkBlockedPosition: Boolean = module.isSelectedBlockNonCollidable()
            if (!checkBlockedPosition && !collidingBelow) {
               val tellyForwardEnabled: Boolean = module.tellySetting.getValue()!! || module.andromedaSetting.getValue()!!
               checkBlockedPosition = !tellyForwardEnabled
            }
            if (checkBlockedPosition) {
               val currentBlockState: BlockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(position)
               if (!currentBlockState.canBeReplaced()) {
                  position = position.above()
               }
            }

            val placementSlot: Int = module.blockSlot
            if (module.canPlaceAt(position, placementSlot)) {
               break
            }

            position = null
            verticalOffset++
         }

         if (position == null) {
            return null
         }

         val supportFlags: Boolean = BlockInteractionUtils.isStrictDirectionEnabled()
         val supportSide: Direction? = BlockInteractionUtils.findSupportDirection(position, supportFlags)
         if (supportSide != null) {
            return ScaffoldPlacementTarget(position, supportSide, false)
         }
         if (module.airPlaceSetting.getValue()!!) {
            return ScaffoldPlacementTarget(BlockPos.containing(origin).below(), null, true)
         }

         var searchDistance = 1

         while (true) {
            if (searchDistance >= 3) {
               return null
            }

            for (searchDirection in Direction.values()) {
               if (searchDirection != Direction.UP) {
                  val neighborPosition: BlockPos = position.relative(searchDirection, searchDistance)

                  val neighborSupportFlags: Boolean = BlockInteractionUtils.isStrictDirectionEnabled()
                  val neighborSupportSide: Direction? = BlockInteractionUtils.findSupportDirection(
                     neighborPosition, neighborSupportFlags
                  )
                  if (neighborSupportSide != null) {
                     // Опора найдена вокруг neighborPosition (position + searchDistance·dir),
                     // значит и цель должна быть neighborPosition с этой стороной:
                     // раньше возвращался position + 1·dir с «далёкой» стороной.
                     return ScaffoldPlacementTarget(neighborPosition, neighborSupportSide, false)
                  }
               }
            }

            searchDistance++
         }
      }

   }
}
