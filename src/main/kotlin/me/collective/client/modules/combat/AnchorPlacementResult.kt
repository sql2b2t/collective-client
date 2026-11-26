package me.collective.client.modules.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction

@JvmRecord
data class AnchorPlacementResult(
    val base: BlockPos,
    val direction: Direction,
    val targetDamage: AnchorTargetDamage
) {
    fun selectHigherDamage(other: AnchorPlacementResult?): AnchorPlacementResult {
        if (other == null) {
            return this
        }
        val otherDamage: Double = other.targetDamage.damage
        val currentDamage: Double = targetDamage.damage
        return if (otherDamage > currentDamage) other else this
    }
}
