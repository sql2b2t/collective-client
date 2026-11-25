package me.collective.client.modules.combat

import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

open class AntiPhaseFullBlockPlanner : AntiPhasePlacementPlanner {
    override fun findPlacement(target: Player): AntiPhasePlacement? {
        val targetPosition = PlayerStateUtils.getRoundedBlockPosition(target as LivingEntity)
        val supportDirection = BlockInteractionUtils.findSupportDirection(targetPosition)
        if (supportDirection != null && WorldUtils.isAir(targetPosition)) {
            return AntiPhasePlacement(
                targetPosition.relative(supportDirection),
                supportDirection.opposite,
                getPlacementBounds(targetPosition, supportDirection)
            )
        }
        return null
    }

    protected open fun getPlacementBounds(position: BlockPos, supportDirection: Direction): AABB {
        return AABB(position)
    }
}
