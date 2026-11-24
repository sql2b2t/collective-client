package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.inventory.AutoMineCandidates
import me.collective.client.utils.inventory.AutoMineProviderBase
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.EnderChestBlockEntity

class AutoMineEnderChestProvider(module: AutoMineModule) : AutoMineProviderBase(module) {

    override fun collectCandidates(candidates: AutoMineCandidates) {
        var selectedPosition: BlockPos? = null
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val eyeSnapshot = player.eyePosition
        val range: Float = autoMine.rangeSetting.value

        for (blockEntity in ClientContext.worldStateManager.blockEntities) {
            val blockPosition = blockEntity.blockPos
            val blockCenter = blockPosition.getCenter()
            val distance = eyeSnapshot.distanceTo(blockCenter)

            if (distance <= range.toDouble() && blockEntity is EnderChestBlockEntity) {
                val validityPosition = blockEntity.blockPos

                if (canMinePosition(validityPosition)) {
                    if (selectedPosition != null) {
                        val miningPositionSnapshot = blockEntity.blockPos
                        if (!isAlreadyMining(miningPositionSnapshot)) {
                            continue
                        }
                    }

                    selectedPosition = blockEntity.blockPos
                }
            }
        }

        val selectedSnapshot = selectedPosition
        if (selectedSnapshot != null) {
            candidates.offer(5) { output -> output.primaryPosition = selectedSnapshot }
        }
    }

    override fun isEnabled(): Boolean {
        return autoMine.enderChestsSetting.value
    }
}
