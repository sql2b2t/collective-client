package me.collective.client.events

import net.minecraft.core.BlockPos

class CrystalPlacementEvent(val pos: BlockPos) : CancellableEvent()
