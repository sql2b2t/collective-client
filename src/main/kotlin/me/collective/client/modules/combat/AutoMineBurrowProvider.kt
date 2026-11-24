package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.inventory.AutoMineCandidates
import me.collective.client.utils.inventory.AutoMineProviderBase
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import kotlin.math.roundToInt

class AutoMineBurrowProvider(module: AutoMineModule) : AutoMineProviderBase(module) {

    override fun collectCandidates(candidates: AutoMineCandidates) {
        val extraBreak = autoMine.isExtraBreakEnabled()
        val target = autoMine.targetSelector.selectTarget() ?: return

        val targetY = (target.getY().roundToInt()).toFloat() + 0.5f

        val boundsSnapshot = target.boundingBox
        val inflateX = -BoundingBoxUtils.blockCornerInset
        val inflateZ = -BoundingBoxUtils.blockCornerInset

        val inflatedSnapshot = boundsSnapshot.inflate(inflateX, 0.0, inflateZ)
        val minimumSnapshot = inflatedSnapshot.setMinY(targetY.toDouble())
        val footBounds = minimumSnapshot.setMaxY(targetY.toDouble())

        val level = ClientAccess.collectiveMinecraft.level ?: return

        val burrowCandidates = BlockPos.betweenClosedStream(footBounds)
            .map { it.immutable() }
            .filter { this.canMinePosition(it) }
            .filter { position ->
                val state = level.getBlockState(position)
                val block = state.block
                val resistance = block.explosionResistance
                resistance >= 600.0f && block != Blocks.COBWEB && block != Blocks.ANVIL
            }
            .sorted(Comparator.comparing { position ->
                if (this.isAlreadyMining(position)) {
                    -1.0
                } else {
                    target.distanceToSqr(position.getCenter())
                }
            })
            .toList()

        if (burrowCandidates.isNotEmpty()) {
            val priority = if (extraBreak && burrowCandidates.size == 1) 500 else 700
            candidates.offer(priority) { output ->
                if (!extraBreak) {
                    output.primaryPosition = burrowCandidates.first()
                } else {
                    if (burrowCandidates.size == 1) {
                        output.secondaryPosition = burrowCandidates.first()
                    } else {
                        output.primaryPosition = burrowCandidates.first()
                        output.secondaryPosition = burrowCandidates[1]
                    }
                }
            }
        }
    }

    override fun isEnabled(): Boolean {
        return autoMine.burrowSetting.value
    }
}
