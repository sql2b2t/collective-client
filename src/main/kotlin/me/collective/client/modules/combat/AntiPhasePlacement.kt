package me.collective.client.modules.combat

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

@JvmRecord
data class AntiPhasePlacement(
    val supportPosition: BlockPos,
    val direction: Direction,
    val bounds: AABB
) {
    fun getHitPosition(): Vec3 {
        val supportCenter = Vec3.atCenterOf(supportPosition)
        return supportCenter.relative(direction, 0.5)
    }

    fun hitPosition(): Vec3 = getHitPosition()
}

val AntiPhasePlacement.hitPosition: Vec3 get() = getHitPosition()
