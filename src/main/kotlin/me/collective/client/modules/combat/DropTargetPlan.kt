package me.collective.client.modules.combat

import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player

@JvmRecord
data class DropTargetPlan(
    val player: Player,
    val mode: DropPlacementMode,
    val cageDirection: Direction?
)
