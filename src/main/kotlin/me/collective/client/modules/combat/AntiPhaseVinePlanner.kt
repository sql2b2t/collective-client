package me.collective.client.modules.combat

import me.collective.client.utils.combat.BoundingBoxUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

class AntiPhaseVinePlanner : AntiPhaseLadderPlanner() {
    override fun shouldRejectPlacement(
        position: BlockPos,
        target: Player,
        shape: AntiPhasePlacementShape
    ): Boolean {
        val targetBoundsSnapshot = BoundingBoxUtils.getInterpolationBounds(target)
        val shapeBounds = shape.bounds
        val worldBounds = shapeBounds.move(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        return targetBoundsSnapshot.intersects(worldBounds)
    }
}
