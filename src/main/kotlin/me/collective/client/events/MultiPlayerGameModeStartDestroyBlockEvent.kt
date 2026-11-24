package me.collective.client.events

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

class MultiPlayerGameModeStartDestroyBlockEvent(val pos: BlockPos, val direction: Direction) : CancellableEvent()
