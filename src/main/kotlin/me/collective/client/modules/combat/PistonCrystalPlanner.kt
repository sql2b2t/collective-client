package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.AirBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FireBlock
import net.minecraft.world.level.block.RedstoneTorchBlock
import net.minecraft.world.level.block.TorchBlock
import net.minecraft.world.level.block.piston.MovingPistonBlock
import net.minecraft.world.level.block.piston.PistonBaseBlock
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.HashSet
import java.util.function.BiPredicate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class PistonCrystalPlanner(private val module: PistonCrystalModule) {

    fun findPlan(target: Player): PistonCrystalPlan? {
        // Кандидаты не зависят от высоты размещения — считаются один раз на цикл,
        // а не заново для каждого из трёх уровней.
        val targetCandidates = candidates(target)
        for (height in 0 until 3) {
            val plan = findPlan(target, height, targetCandidates)
            if (plan != null) {
                return plan
            }
        }
        return null
    }

    private fun findPlan(target: Player, height: Int, targetCandidates: Set<Candidate>): PistonCrystalPlan? {
        var selected: PistonCrystalPlan? = null
        var score = Double.MAX_VALUE
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val eyePosition = player.eyePosition

        for (candidate in targetCandidates) {
            val position = candidate.position.offset(0, height, 0)
            val outward = candidate.outward
            // Положительная дистанция + сравнение «<» → выбирается ближайший кандидат.
            // Раньше было «-distance», и отбор шёл в сторону самого дальнего.
            val candidateScore = eyePosition.distanceTo(Vec3.atCenterOf(position))
            if (candidateScore < score) {
                if (!hasVerticalClearance(position, outward.opposite, height)) {
                    continue
                }
                val plan = buildPlan(position, outward)
                if (plan != null) {
                    selected = plan
                    score = candidateScore
                }
            }
        }
        return selected
    }

    private fun buildPlan(crystalPosition: BlockPos, outward: Direction): PistonCrystalPlan? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null

        if (!WorldUtils.isBedrockOrObsidian(crystalPosition.below()) ||
            !WorldUtils.isReplaceable(crystalPosition) ||
            module.hasBlockingEntities(module.crystalBounds(crystalPosition)) { blocksCrystal(it) }
        ) {
            return null
        }
        val base = crystalPosition.below()
        val directions = BlockInteractionUtils.getStrictDirections(base)
        val face = if (directions.isEmpty()) Direction.UP else directions.first()
        val crystalHit = Vec3.atCenterOf(base).relative(face, 0.5)
        if (player.eyePosition.distanceTo(crystalHit) > module.placeRangeSetting.value) {
            return null
        }
        val attackPosition = Vec3.atCenterOf(crystalPosition).relative(outward.opposite, 0.02)
        if (attackPosition.distanceTo(player.eyePosition) > module.attackRangeSetting.value) {
            return null
        }
        val placement = findPiston(crystalPosition, outward) ?: return null
        return PistonCrystalPlan(
            crystalPosition,
            BlockHitResult(crystalHit, face, base, false),
            placement.position,
            outward.opposite,
            placement.hit,
            placement.powerPosition,
            placement.powerHit
        )
    }

    private fun hasVerticalClearance(position: BlockPos, inward: Direction, height: Int): Boolean {
        for (y in 0 until height) {
            if (!WorldUtils.isReplaceable(position.relative(inward).above(y + 1))) {
                return false
            }
        }
        return true
    }

    private fun findPiston(crystalPosition: BlockPos, outward: Direction): PistonPlacement? {
        var selected: PistonPlacement? = null
        var distance = Double.MAX_VALUE
        val player = ClientAccess.collectiveMinecraft.player ?: return null

        for (position in pistonPositions(crystalPosition, outward)) {
            val candidateDistance = player.eyePosition.distanceTo(Vec3.atCenterOf(position))
            if (candidateDistance >= distance) {
                continue
            }
            val inward = outward.opposite
            if (position.relative(inward) == crystalPosition && WorldUtils.isPlacementOccupied(position.relative(inward, 2))) {
                continue
            }
            val hit = findHit(position, if (module.rotateSetting.value) outward else null, true) { support, _ ->
                val level = ClientAccess.collectiveMinecraft.level ?: return@findHit false
                val state = level.getBlockState(support)
                val block = state.block
                !state.isAir && block !is PistonBaseBlock && block !is PistonHeadBlock &&
                    block !is MovingPistonBlock && block !is FireBlock
            } ?: continue

            val power = if (module.powerItem == Items.REDSTONE_TORCH) {
                findTorch(position, inward, crystalPosition)
            } else {
                findRedstone(position, inward, crystalPosition)
            }
            if (power != null) {
                selected = PistonPlacement(position, hit, power.position, power.hit)
                distance = candidateDistance
            }
        }
        return selected
    }

    private fun pistonPositions(crystalPosition: BlockPos, outward: Direction): List<BlockPos> {
        val positions = ArrayList<BlockPos>()
        val level = ClientAccess.collectiveMinecraft.level ?: return positions

        val dx = outward.stepX
        val dz = outward.stepZ
        val minX = if (dx == 0) -1 else min(0, dx)
        val maxX = if (dx == 0) 1 else max(0, dx)
        val minZ = if (dz == 0) -1 else min(0, dz)
        val maxZ = if (dz == 0) 1 else max(0, dz)

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in 0..1) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue
                    }
                    val position = crystalPosition.offset(x, y, z)
                    if (!isPistonAngleValid(position) || !allowsPistonHead(position.relative(outward.opposite)) ||
                        module.hasBlockingEntities(AABB(position)) { isPlayer(it) }
                    ) {
                        continue
                    }
                    val state = level.getBlockState(position)
                    val block = state.block
                    if (block is PistonBaseBlock || block is MovingPistonBlock || block is FireBlock || state.canBeReplaced()) {
                        positions.add(position)
                    }
                }
            }
        }
        return positions
    }

    private fun candidates(target: LivingEntity): Set<Candidate> {
        val level = ClientAccess.collectiveMinecraft.level ?: return emptySet()
        val height = target.getEyeHeight(target.pose).toInt()
        val candidates = HashSet<Candidate>()

        for (surround in PlayerStateUtils.getSurroundPositions(target)) {
            if (level.getBlockState(surround).canBeReplaced()) {
                for (outward in Direction.Plane.HORIZONTAL) {
                    val position = surround.relative(outward)
                    if (!BoundingBoxUtils.intersectsBlock(target.boundingBox, position)) {
                        candidates.add(Candidate(position.offset(0, height, 0), outward))
                    }
                }
            }
        }
        return candidates
    }

    private fun findTorch(piston: BlockPos, facing: Direction, crystalPosition: BlockPos): PowerPlacement? {
        var selected: PowerPlacement? = null
        var distance = Double.MAX_VALUE
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        for (direction in Direction.values()) {
            if (direction == facing || direction == Direction.DOWN) {
                continue
            }
            val position = piston.relative(direction)
            if (position == crystalPosition) {
                continue
            }
            val candidateDistance = player.eyePosition.distanceTo(Vec3.atCenterOf(position))
            if (candidateDistance >= distance) {
                continue
            }
            val state = level.getBlockState(position)
            if (!state.canBeReplaced() && state.block !is RedstoneTorchBlock && state.block !is FireBlock) {
                continue
            }
            val hit = findHit(position, null, false) { support, side ->
                if (side == Direction.UP && !WorldUtils.isPlacementOccupied(position.below()) || support == piston) {
                    return@findHit false
                }
                val currentLevel = ClientAccess.collectiveMinecraft.level ?: return@findHit false
                val block = currentLevel.getBlockState(support).block
                block !is TorchBlock && block !is PistonBaseBlock && block !is PistonHeadBlock
            }
            if (hit != null) {
                selected = PowerPlacement(position, hit)
                distance = candidateDistance
            }
        }
        return selected
    }

    private fun findRedstone(piston: BlockPos, facing: Direction, crystalPosition: BlockPos): PowerPlacement? {
        var selected: PowerPlacement? = null
        var distance = Double.MAX_VALUE
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        for (direction in Direction.values()) {
            if (direction == facing) {
                continue
            }
            val position = piston.relative(direction)
            if (position == crystalPosition || position.y < crystalPosition.y) {
                continue
            }
            val candidateDistance = player.eyePosition.distanceTo(Vec3.atCenterOf(position))
            if (candidateDistance >= distance) {
                continue
            }
            val state = level.getBlockState(position)
            if ((!state.canBeReplaced() && !state.`is`(Blocks.REDSTONE_BLOCK)) ||
                module.hasBlockingEntities(AABB(position)) { isPlayer(it) }
            ) {
                continue
            }
            val hit = findHit(position, null, true) { _, _ -> true }
            if (hit != null) {
                selected = PowerPlacement(position, hit)
                distance = candidateDistance
            }
        }
        return selected
    }

    private fun findHit(position: BlockPos, facing: Direction?, allowAir: Boolean, predicate: BiPredicate<BlockPos, Direction>?): BlockHitResult? {
        val strict = BlockInteractionUtils.isStrictDirectionEnabled()
        if (module.airPlaceSetting.value && allowAir) {
            val directions = BlockInteractionUtils.getStrictDirectionsOrFallback(position, Direction.UP)
            return BlockHitResult(Vec3.atCenterOf(position), directions.first(), position, false)
        }
        for (direction in Direction.values()) {
            val support = position.relative(direction)
            if (support.y >= WorldUtils.getTopY() || !WorldUtils.isPlacementOccupied(support) ||
                (predicate != null && !predicate.test(support, direction)) ||
                (strict && !BlockInteractionUtils.getStrictDirections(support).contains(direction.opposite))
            ) {
                continue
            }
            val point = selectHitPoint(support, direction.opposite, facing, module.placeRangeSetting.value)
            if (point != null) {
                return BlockHitResult(point, direction.opposite, support, false)
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun selectHitPoint(position: BlockPos, side: Direction, facing: Direction?, range: Float): Vec3? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val points = BlockSampleMode.X8.sampleFace(position, side) as List<Vec3>
        for (point in points) {
            if (point.distanceTo(player.eyePosition) > range) {
                continue
            }
            if (facing == null) {
                return point
            }
            val rotations = RotationManager.getRotationsToPosition(point)
            if (abs(rotations[1]) <= MathConstants.FORTY_FIVE_DEGREES && Direction.fromYRot(rotations[0].toDouble()) == facing) {
                return point
            }
        }
        return null
    }

    private fun isPistonAngleValid(position: BlockPos): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val eyes = player.eyePosition
        val dx = eyes.x - position.x - 0.5
        val dz = eyes.z - position.z - 0.5
        return sqrt(dx * dx + dz * dz) > abs(eyes.y - position.y - 0.5)
    }

    private fun allowsPistonHead(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val block = level.getBlockState(position).block
        return block is AirBlock || block is FireBlock || block is PistonHeadBlock ||
            block is MovingPistonBlock || block is RedstoneTorchBlock
    }

    private fun isPlayer(entity: Entity): Boolean {
        return !entity.isSpectator && entity is Player
    }

    private fun blocksCrystal(entity: Entity): Boolean {
        return !entity.isSpectator && ((entity is ItemEntity && entity.tickCount > 3) || entity is LivingEntity)
    }

    private data class Candidate(val position: BlockPos, val outward: Direction)
    private data class PowerPlacement(val position: BlockPos, val hit: BlockHitResult)
    private data class PistonPlacement(val position: BlockPos, val hit: BlockHitResult, val powerPosition: BlockPos, val powerHit: BlockHitResult)
}
