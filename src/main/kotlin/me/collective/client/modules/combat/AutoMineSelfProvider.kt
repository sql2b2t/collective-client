package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.AutoMineCandidates
import me.collective.client.utils.inventory.AutoMineProviderBase
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.phys.AABB
import kotlin.math.roundToLong

class AutoMineSelfProvider(module: AutoMineModule) : AutoMineProviderBase(module) {
    private val recentTimer = ElapsedTimer()
    private var recentPosition: BlockPos? = null
    private var recentPriority = 0

    private fun isClosedUpperTrapdoor(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(position)

        if (state.block !is TrapDoorBlock) {
            return false
        }

        val open = state.getValue(TrapDoorBlock.OPEN)
        if (open) {
            return false
        }

        return state.getValue(TrapDoorBlock.HALF) != Half.BOTTOM
    }

    private fun selectFaceCandidate(): BlockPos? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        if (!autoMine.faceSetting.value) {
            return null
        }

        if (PlayerStateUtils.isLocalLowProfile()) {
            return null
        }

        val eyePosition = BlockPos.containing(player.eyePosition)
        val eyeState = level.getBlockState(eyePosition)

        if (!eyeState.isAir) {
            val eyeBlock = eyeState.block
            val resistance = eyeBlock.explosionResistance

            if (resistance >= 600.0f) {
                if (canMinePosition(eyePosition)) {
                    val feetPosition = player.blockPosition()
                    val feetState = level.getBlockState(feetPosition)
                    if (!feetState.isAir) {
                        if (!canMinePosition(feetPosition)) {
                            return null
                        }
                    }

                    return eyePosition
                }
            }
        }

        return null
    }

    override fun collectCandidates(candidates: AutoMineCandidates) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val facePosition = selectFaceCandidate()
        val targetSelector = autoMine.targetSelector

        if (targetSelector.selectTarget() != null || facePosition != null) {
            if (!player.isFallFlying) {
                offerRecentCandidate(candidates)

                val playerBounds = player.boundingBox
                if (autoMine.onlyCrawlSetting.value) {
                    if (!PlayerStateUtils.isLocalLowProfile() && facePosition == null) {
                        return
                    }
                }

                val candidatePosition = selectSelfCandidate(playerBounds)
                var priority = if (!PlayerStateUtils.isLocalLowProfile() && facePosition == null) 100 else 1500

                if (autoMine.tryCrawlSetting.value) {
                    if (!PlayerStateUtils.isLocalLowProfile() && candidatePosition != null) {
                        val candidateY = candidatePosition.y.toLong()
                        val playerY = player.y.roundToLong()
                        if (candidateY == playerY) {
                            priority = 1000
                        }
                    }
                }

                if (candidatePosition == null) {
                    return
                }

                candidates.offer(priority) { output ->
                    output.setPrimaryPosition(candidatePosition)
                }
                recentPosition = candidatePosition
                recentPriority = priority
                recentTimer.reset()
            }
        }
    }

    private fun hasGroundedResistantSupport(supportPosition: BlockPos): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(supportPosition)

        val block = state.block
        val resistance = block.explosionResistance

        if (resistance < 600.0f) {
            return false
        }

        return player.onGround()
    }

    private fun offerRecentCandidate(candidates: AutoMineCandidates) {
        if (autoMine.instantSetting.value) {
            val currentRecentPosition = recentPosition
            if (!recentTimer.hasElapsed(150L) && currentRecentPosition != null) {
                val retainedPriority = recentPriority
                candidates.offer(retainedPriority) { output ->
                    output.setPrimaryPosition(currentRecentPosition)
                }
            }
        }
    }

    override fun isEnabled(): Boolean {
        return autoMine.selfSetting.value
    }

    private fun selectSelfCandidate(playerBounds: AABB): BlockPos? {
        val basePosition = PlayerStateUtils.getLocalRoundedBlockPosition()
        val belowPosition = basePosition.below()
        var overheadPosition = getOverheadPosition(playerBounds)
        val facePosition = selectFaceCandidate()

        if (autoMine.tryCrawlSetting.value) {
            if (canMinePosition(basePosition)) {
                if (!PlayerStateUtils.isLocalLowProfile()) {
                    if (isAlreadyMining(basePosition)) {
                        return basePosition
                    }

                    val supportPosition = overheadPosition.below()
                    if (hasGroundedResistantSupport(supportPosition)) {
                        return basePosition
                    }
                }
            }
        }

        if (canMinePosition(belowPosition)) {
            val allowBelow = isAlreadyMining(belowPosition) || PlayerStateUtils.isLocalLowProfile()
            if (allowBelow && shouldMineDown()) {
                return belowPosition
            }
        }

        val belowOverhead = overheadPosition.below()
        if (isClosedUpperTrapdoor(belowOverhead)) {
            if (PlayerStateUtils.isLocalLowProfile()) {
                overheadPosition = overheadPosition.below()
            }
        }

        if (autoMine.tryCrawlSetting.value) {
            if (PlayerStateUtils.isLocalLowProfile()) {
                return null
            }
        }

        if (facePosition != null) {
            overheadPosition = facePosition
        }

        return if (canMinePosition(overheadPosition)) overheadPosition else null
    }

    private fun getOverheadPosition(playerBounds: AABB): BlockPos {
        val player = ClientAccess.collectiveMinecraft.player
        val positionSnapshot = player?.position() ?: net.minecraft.world.phys.Vec3.ZERO
        val heightOffset = playerBounds.ysize - BoundingBoxUtils.blockCornerInset
        val overheadSnapshot = positionSnapshot.add(0.0, heightOffset, 0.0)
        val headPosition = BlockPos.containing(overheadSnapshot)
        return headPosition.above()
    }

    private fun shouldMineDown(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false

        if (!autoMine.downSetting.value) {
            return false
        }

        if (autoMine.downPrioritySetting.value) {
            return true
        }

        val basePosition = PlayerStateUtils.getLocalRoundedBlockPosition()
        val probePosition = basePosition.below(2)

        if (level.isEmptyBlock(probePosition)) {
            return false
        }

        for (occupiedPosition in PlayerStateUtils.getSurroundPositions(player)) {
            val belowPosition = occupiedPosition.below()
            val state = level.getBlockState(belowPosition)

            if (state.isAir) {
                return false
            }

            val block = state.block
            val resistance = block.explosionResistance
            if (resistance < 600.0f) {
                return false
            }
        }

        return true
    }
}
