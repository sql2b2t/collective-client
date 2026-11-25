package me.collective.client.modules.combat

import me.collective.client.utils.inventory.AutoMineProviderBase
import me.collective.client.utils.inventory.AutoMineCandidates
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos

class AutoMineFishProvider(module: AutoMineModule) : AutoMineProviderBase(module) {

    override fun collectCandidates(candidates: AutoMineCandidates) {
        val targetSelector = autoMine.targetSelector
        val target = targetSelector.selectTarget() ?: return

        val occupiedCount = BlockPos.betweenClosedStream(targetSelector.getTargetFootprintBounds(target)).count()
        if (occupiedCount == 1L) {
            val targetPosition = PlayerStateUtils.getRoundedBlockPosition(target)

            if (!WorldUtils.isAir(targetPosition) && !WorldUtils.canMineBlock(targetPosition)) {
                val supportPosition = targetPosition.below()

                if (WorldUtils.canMineBlock(supportPosition)) {
                    candidates.offer(400) { output ->
                        if (autoMine.isExtraBreakEnabled()) {
                            output.secondaryPosition = targetPosition.below()
                        } else {
                            output.primaryPosition = targetPosition.below()
                        }
                    }
                }
            }
        }
    }

    override fun isEnabled(): Boolean {
        return autoMine.fishSetting.value
    }
}
