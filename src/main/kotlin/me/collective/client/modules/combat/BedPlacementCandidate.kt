package me.collective.client.modules.combat

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.BlockHitResult

@JvmRecord
data class BedPlacementCandidate(
    val foot: BlockPos,
    val head: BlockPos,
    val targetDamage: Float,
    val selfDamage: Float,
    val hitResult: BlockHitResult
)
