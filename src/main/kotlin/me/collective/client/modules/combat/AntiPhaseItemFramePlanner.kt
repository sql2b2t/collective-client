package me.collective.client.modules.combat

import me.collective.client.ClientContext
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape

class AntiPhaseItemFramePlanner : AntiPhaseFullBlockPlanner() {

    override fun findPlacement(target: Player): AntiPhasePlacement? {
        val abovePlacement = findAbovePlacement(target)
        return abovePlacement ?: super.findPlacement(target)
    }

    override fun getPlacementBounds(position: BlockPos, supportDirection: Direction): AABB {
        for (shape in itemFrameShapes) {
            val shapeDirection = shape.direction
            if (shapeDirection == supportDirection.opposite) {
                val shapeBounds = shape.bounds
                return shapeBounds.move(position)
            }
        }
        return AABB(position)
    }

    private fun findAbovePlacement(target: Player): AntiPhasePlacement? {
        val targetPosition = PlayerStateUtils.getRoundedBlockPosition(target)
        val abovePosition = targetPosition.above()
        if (WorldUtils.isAir(abovePosition)) {
            if (ClientContext.worldStateManager.isPlacementOccupied(targetPosition)) {
                val validDirections = BlockInteractionUtils.getStrictDirections(targetPosition)
                if (BlockInteractionUtils.isStrictDirectionEnabled() && !validDirections.contains(Direction.UP)) {
                    return null
                }
                return AntiPhasePlacement(targetPosition, Direction.UP, getPlacementBounds(targetPosition.above(), Direction.DOWN))
            }
        }
        return null
    }

    companion object {
        private val itemFrameShapes: List<AntiPhasePlacementShape>

        init {
            val east = Direction.EAST
            val eastBounds: VoxelShape = Block.box(0.0, 2.0, 2.0, 1.0, 14.0, 14.0)
            val eastShape = AntiPhasePlacementShape.fromShape(east, eastBounds)

            val west = Direction.WEST
            val westBounds: VoxelShape = Block.box(15.0, 2.0, 2.0, 16.0, 14.0, 14.0)
            val westShape = AntiPhasePlacementShape.fromShape(west, westBounds)

            val north = Direction.NORTH
            val northBounds: VoxelShape = Block.box(2.0, 2.0, 0.0, 14.0, 14.0, 1.0)
            val northShape = AntiPhasePlacementShape.fromShape(north, northBounds)

            val south = Direction.SOUTH
            val southBounds: VoxelShape = Block.box(2.0, 2.0, 15.0, 14.0, 14.0, 16.0)
            val southShape = AntiPhasePlacementShape.fromShape(south, southBounds)

            val down = Direction.DOWN
            val downBounds: VoxelShape = Block.box(2.0, 0.0, 2.0, 14.0, 1.0, 14.0)
            val downShape = AntiPhasePlacementShape.fromShape(down, downBounds)

            val up = Direction.UP
            val upBounds: VoxelShape = Block.box(2.0, 15.0, 2.0, 14.0, 16.0, 14.0)
            val upShape = AntiPhasePlacementShape.fromShape(up, upBounds)

            itemFrameShapes = listOf(eastShape, westShape, northShape, southShape, downShape, upShape)
        }
    }
}
