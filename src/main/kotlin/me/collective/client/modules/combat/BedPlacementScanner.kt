package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class BedPlacementScanner(private val module: BedAuraModule) : ClientAccess {
    private var bedDirection: Direction? = null
    private var hitPoint: Vec3? = null
    private var range: Float = 0f

    fun scanPlacement(target: LivingEntity): BedPlacementCandidate? {
        this.range = module.placeRangeSetting.value
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val eyePosition = player.eyePosition
        // Ранний отсев по дистанции: центр головы дальше range + 1.7 (максимальный
        // сдвиг ноги — соседний блок) гарантированно отсекается проверкой footBounds
        // внутри evaluatePlacement, поэтому поиск опор и расчёт урона не нужны.
        val rangeLimitSqr = ((this.range + 1.7f) * (this.range + 1.7f)).toDouble()
        val targetPosition = target.position()
        // bounds цели не меняются в течение скана — считаются один раз,
        // а не на каждую позицию (раньше getDamageBounds вызывался 80 раз/тик).
        val damageBounds = module.getDamageBounds(target)
        var bestPlacement: BedPlacementCandidate? = null

        var offsetX = -2.0f
        while (offsetX < 2.0f) {
            var offsetY = -2.0f
            while (offsetY < 3.0f) {
                var offsetZ = -2.0f
                while (offsetZ < 2.0f) {
                    hitPoint = null
                    scanPosition.set(
                        targetPosition.x + offsetX,
                        targetPosition.y + offsetY,
                        targetPosition.z + offsetZ
                    )

                    val deltaX = eyePosition.x - (scanPosition.x + 0.5)
                    val deltaY = eyePosition.y - (scanPosition.y + 0.5)
                    val deltaZ = eyePosition.z - (scanPosition.z + 0.5)
                    if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > rangeLimitSqr) {
                        offsetZ += 1.0f
                        continue
                    }

                    val candidate = evaluatePlacement(target, scanPosition, damageBounds)
                    if (candidate != null && (bestPlacement == null || candidate.targetDamage >= bestPlacement.targetDamage)) {
                        bestPlacement = candidate
                    }
                    offsetZ += 1.0f
                }
                offsetY += 1.0f
            }
            offsetX += 1.0f
        }

        return bestPlacement
    }

    private fun selectSupportDirection(facing: Direction, position: BlockPos, requireSupport: Boolean): Direction? {
        val airPlace = module.airPlaceSetting.value
        for (direction in Direction.values()) {
            val adjacentPosition = position.relative(direction)
            if (adjacentPosition.y >= WorldUtils.getTopY()) {
                continue
            }

            if (WorldUtils.isPlacementOccupied(adjacentPosition) || airPlace) {
                if (requireSupport) {
                    val supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition)
                    if (!supportDirections.contains(direction.opposite)) {
                        continue
                    }
                }

                if (!module.rotateSetting.value) {
                    return direction
                }

                hitPoint = selectHitPoint(direction, facing, position)
                if (hitPoint != null) {
                    return direction
                }
            }
        }
        return null
    }

    private fun selectHitPoint(supportDirection: Direction?, facing: Direction, position: BlockPos): Vec3? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val eyePos = player.eyePosition

        for (sample in sampleHitPoints(position, supportDirection)) {
            if (sample.distanceTo(eyePos) <= range) {
                val yaw = RotationManager.getRotationsToPosition(sample)[0]
                val sampleFacing = Direction.fromYRot(yaw.toDouble())
                if (facing == sampleFacing) {
                    return sample
                }
            }
        }

        return null
    }

    private fun createHitResult(direction: Direction, position: BlockPos): BlockHitResult {
        var needsPointFacing = false
        if (module.airPlaceSetting.value) {
            val adjacentPosition = position.relative(direction)
            if (!WorldUtils.isPlacementOccupied(adjacentPosition)) {
                needsPointFacing = true
            }
        }

        if (module.liquidPlaceSetting.value) {
            if (WorldUtils.hasFluid(position)) {
                needsPointFacing = true
            }
        }

        if (!needsPointFacing) {
            val point = hitPoint ?: Vec3.atCenterOf(position.relative(direction))
            return BlockHitResult(point, direction.opposite, position.relative(direction), false)
        }

        val pointFacing = bedDirection ?: Direction.NORTH
        val point = selectHitPoint(null, pointFacing, position) ?: Vec3.atCenterOf(position)
        return BlockHitResult(point, direction, position, false)
    }

    private fun evaluatePlacement(target: LivingEntity, headPosition: BlockPos, damageBounds: AABB): BedPlacementCandidate? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val state = level.getBlockState(headPosition)
        if (!state.canBeReplaced()) {
            if (!module.isBedPart(headPosition, BedPart.HEAD)) {
                return null
            }
        }

        var footPosition: BlockPos? = null
        var supportDirection: Direction? = null

        for (direction in Direction.Plane.HORIZONTAL) {
            val candidateFoot = headPosition.relative(direction)

            if (!BlockInteractionUtils.canPlaceAt(candidateFoot, true, false)) {
                if (!module.isBedPart(candidateFoot, BedPart.FOOT)) {
                    continue
                }
            }

            val supportFacing = direction.opposite
            val requireSupport = BlockInteractionUtils.isStrictDirectionEnabled()

            supportDirection = selectSupportDirection(supportFacing, candidateFoot, requireSupport)
            if (supportDirection != null) {
                bedDirection = direction.opposite
                footPosition = candidateFoot
                break
            }
        }

        val foot = footPosition ?: return null
        val direction = supportDirection ?: return null

        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val eyeSnapshot = player.eyePosition
        val footBounds = AABB(foot)

        if (eyeSnapshot.distanceTo(BoundingBoxUtils.clampToBounds(eyeSnapshot, footBounds)) > range) {
            return null
        }

        val explosionCenter = Vec3.atCenterOf(headPosition)
        val targetDamage = DamageUtils.getBedDamage(explosionCenter, target, damageBounds)

        if (targetDamage < module.minDamageSetting.value) {
            return null
        }

        val selfDamage = DamageUtils.getBedDamage(explosionCenter, player)
        if (selfDamage > module.maxSelfDamageSetting.value) {
            return null
        }

        if (hitPoint == null && !module.rotateSetting.value) {
            hitPoint = Vec3.atCenterOf(foot).relative(direction, 0.5)
        }

        return BedPlacementCandidate(
            foot,
            headPosition.immutable(),
            targetDamage,
            selfDamage,
            createHitResult(direction, foot)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun sampleHitPoints(position: BlockPos, direction: Direction?): List<Vec3> {
        val samplingMode = BlockSampleMode.X8
        return if (direction == null) {
            samplingMode.sampleBlock(position) as List<Vec3>
        } else {
            val facePositionSnapshot = position.relative(direction)
            val faceDirection = direction.opposite
            samplingMode.sampleFace(facePositionSnapshot, faceDirection) as List<Vec3>
        }
    }

    companion object {
        private val scanPosition = MutableBlockPos()
    }
}
