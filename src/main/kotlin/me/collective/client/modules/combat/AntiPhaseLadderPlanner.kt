package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.LadderBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape

open class AntiPhaseLadderPlanner : AntiPhasePlacementPlanner {
    override fun findPlacement(target: Player): AntiPhasePlacement? {
        val targetPosition = PlayerStateUtils.getRoundedBlockPosition(target)
        var bestDirection: Direction? = null
        var bestBounds: AABB? = null
        var bestDistanceSquared = 999.0f

        if (!WorldUtils.isAir(targetPosition)) {
            return null
        }

        for (shape in ladderShapes) {
            val supportDirection = shape.direction
            val supportPosition = targetPosition.relative(supportDirection)

            if (!this.shouldRejectPlacement(targetPosition, target, shape)) {
                if (BlockInteractionUtils.isStrictDirectionEnabled()) {
                    if (this.isUnsupportedDirection(supportPosition, supportDirection)) {
                        continue
                    }
                }

                val targetPositionVector = target.position()
                val supportCenter = supportPosition.center
                val distanceSquared = targetPositionVector.distanceToSqr(supportCenter).toFloat()
                if (distanceSquared < bestDistanceSquared) {
                    bestBounds = shape.bounds
                    bestDirection = supportDirection
                    bestDistanceSquared = distanceSquared
                }
            }
        }

        if (bestDirection == null || bestBounds == null) {
            return null
        }

        return AntiPhasePlacement(
            targetPosition.relative(bestDirection),
            bestDirection.opposite,
            bestBounds.move(targetPosition)
        )
    }

    protected open fun shouldRejectPlacement(
        position: BlockPos,
        target: Player,
        shape: AntiPhasePlacementShape
    ): Boolean {
        val supportDirection = shape.direction
        val supportPosition = position.relative(supportDirection)

        val targetBoundsSnapshot = BoundingBoxUtils.getInterpolationBounds(target)
        val shapeBounds = shape.bounds
        val worldBounds = shapeBounds.move(position)
        if (!targetBoundsSnapshot.intersects(worldBounds)) {
            val level = ClientAccess.collectiveMinecraft.level ?: return true
            val supportState = level.getBlockState(supportPosition)
            if (!supportState.canBeReplaced()) {
                return false
            }
        }

        return true
    }

    private fun isUnsupportedDirection(supportPosition: BlockPos, supportDirection: Direction): Boolean {
        val validDirections = BlockInteractionUtils.getStrictDirections(supportPosition)
        return !validDirections.contains(supportDirection.opposite)
    }

    companion object {
        private val ladderShapes: List<AntiPhasePlacementShape>

        init {
            val east = Direction.EAST
            val eastShape = LadderBlock.SHAPES[Direction.EAST] as VoxelShape
            val eastPlacement = AntiPhasePlacementShape.fromShape(east, eastShape)
            val west = Direction.WEST
            val westShape = LadderBlock.SHAPES[Direction.WEST] as VoxelShape
            val westPlacement = AntiPhasePlacementShape.fromShape(west, westShape)
            val north = Direction.NORTH
            val northShape = LadderBlock.SHAPES[Direction.NORTH] as VoxelShape
            val northPlacement = AntiPhasePlacementShape.fromShape(north, northShape)
            val south = Direction.SOUTH
            val southShape = LadderBlock.SHAPES[Direction.SOUTH] as VoxelShape
            val southPlacement = AntiPhasePlacementShape.fromShape(south, southShape)
            ladderShapes = listOf(eastPlacement, westPlacement, northPlacement, southPlacement)
        }
    }
}
