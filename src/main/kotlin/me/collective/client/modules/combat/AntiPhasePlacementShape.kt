package me.collective.client.modules.combat

import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape

@JvmRecord
data class AntiPhasePlacementShape(
    val direction: Direction,
    val bounds: AABB
) {
    companion object {
        @JvmStatic
        fun fromShape(facing: Direction, shape: VoxelShape): AntiPhasePlacementShape {
            return AntiPhasePlacementShape(facing.opposite, shape.bounds())
        }
    }
}
