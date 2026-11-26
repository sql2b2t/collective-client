package me.collective.client.events

import net.minecraft.core.BlockPos

class MultiPlayerGameModeDestroyBlockEvent(pos: BlockPos) : CancellableEvent() {
    @JvmField
    var pos: BlockPos = pos

    fun getPos(): BlockPos = pos
}
