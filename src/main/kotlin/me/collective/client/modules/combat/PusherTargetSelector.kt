package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.RotationManager
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.util.Comparator

class PusherTargetSelector(private val module: PusherModule) : ClientAccess {

    private fun isValidTarget(target: Player): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val position = target.blockPosition()
        val headPosition = position.above()
        val headState = level.getBlockState(headPosition)

        if (!excludedHeadBlocks.contains(headState.block)) {
            val headResistance = headState.getDestroySpeed(level, headPosition)

            if (headResistance != -1.0f) {
                val blockTracker = ClientContext.worldStateManager
                val clearance = position.above(2)

                if (!blockTracker.isPlacementOccupied(clearance)) {
                    if (module.onlyHoleSetting.value) {
                        if (!ClientContext.holeManager.intersectsHole(position)) {
                            return false
                        }
                    }

                    if (module.onlySafeSetting.value) {
                        if (!ClientContext.holeManager.intersectsHole(position)) {
                            val safeState = level.getBlockState(target.blockPosition())
                            val safeBlock = safeState.block
                            val safeResistance = safeBlock.explosionResistance

                            if (safeResistance < 600.0f) {
                                return false
                            }
                        }
                    }

                    val occupiedCount = PlayerStateUtils.getSurroundPositions(target).size
                    return occupiedCount <= 6
                }
            }
        }

        return false
    }

    fun selectTarget(): Player? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val yawSnapshot = player.yRot

        return level.players().stream()
            .filter { candidate ->
                if (candidate.isAlive && candidate !== player) {
                    val bounds = candidate.boundingBox
                    val height = bounds.ysize

                    if (height > 1.0) {
                        if (EntityUtils.hasCollisionBelow(candidate)) {
                            val distance = player.distanceTo(candidate)
                            if (distance <= module.getRange()) {
                                if (!ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                                    return@filter true
                                }
                            }
                        }
                    }
                }
                false
            }
            .filter { candidate ->
                if (module.ignoreNakedSetting.value) {
                    if (!PlayerStateUtils.hasArmor(candidate)) {
                        return@filter false
                    }
                }
                true
            }
            .filter { isValidTarget(it) }
            .min(Comparator.comparingDouble { candidate ->
                val candidateYaw = RotationManager.getRotationsToEntity(candidate)[0]
                Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw).toDouble()
            })
            .orElse(null)
    }

    companion object {
        private val excludedHeadBlocks: List<Block> = listOf(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.REINFORCED_DEEPSLATE,
            Blocks.RESPAWN_ANCHOR
        )
    }
}
