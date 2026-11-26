package me.collective.client.events

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.SignBlockEntity

class ClientPacketListenerHandleOpenSignEditorEvent(
    val position: BlockPos,
    val sign: SignBlockEntity,
    val frontText: Boolean
) : CancellableEvent()
