package me.collective.client.modules.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult

@JvmRecord
internal data class PistonCrystalPlan(
    val crystalPosition: BlockPos,
    val crystalHit: BlockHitResult,
    val pistonPosition: BlockPos,
    val pistonFacing: Direction,
    val pistonHit: BlockHitResult,
    val powerPosition: BlockPos,
    val powerHit: BlockHitResult
)
