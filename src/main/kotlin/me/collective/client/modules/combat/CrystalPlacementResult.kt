package me.collective.client.modules.combat

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity

@JvmRecord
data class CrystalPlacementResult(
    val base: BlockPos,
    val target: LivingEntity,
    val damage: Double,
    val kind: CrystalPlacementKind
) {
    fun selectBetterPlacement(other: CrystalPlacementResult?): CrystalPlacementResult {
        if (other == null) {
            return this
        }
        var priority = this.kind.ordinal
        if (priority < other.kind.ordinal) {
            return this
        }
        priority = this.kind.ordinal
        if (priority > other.kind.ordinal) {
            return other
        }
        return if (other.damage > this.damage) other else this
    }
}
