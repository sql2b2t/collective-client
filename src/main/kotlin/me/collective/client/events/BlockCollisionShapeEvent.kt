package me.collective.client.events

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape

class BlockCollisionShapeEvent private constructor() : CancellableEvent() {
    var shape: VoxelShape? = null
    var pos: BlockPos? = null
        private set
    var state: BlockState? = null
        private set

    companion object {
        @JvmField
        val INSTANCE = BlockCollisionShapeEvent()

        @JvmStatic
        fun prepare(shape: VoxelShape, pos: BlockPos, state: BlockState): BlockCollisionShapeEvent {
            INSTANCE.resetCancellation()
            INSTANCE.shape = shape
            INSTANCE.pos = pos
            INSTANCE.state = state
            return INSTANCE
        }
    }
}
