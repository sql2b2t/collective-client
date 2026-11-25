package me.collective.client.utils.sort

import baritone.api.BaritoneAPI
import baritone.api.IBaritone
import baritone.api.Settings
import baritone.api.pathing.goals.Goal
import baritone.api.pathing.goals.GoalBlock
import baritone.api.pathing.goals.GoalComposite
import baritone.api.pathing.goals.GoalNear
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.HashSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Доводит игрока до сундука так, чтобы к нему было видно откуда-то рядом.
 *
 * <p>Главная сложность не в пути, а в ракурсе: Baritone доводит до блока,
 * но может встать так, что сундук не виден и не открывается. Поэтому цель
 * формулируется не «к блоку», а «в любую из точек, откуда сундук видно».
 */
class ChestNavigator {
    private var target: BlockPos? = null
    private var issued: Goal? = null
    private var issuedPrecise = false
    private var sinceIssue = 0
    private var attempts = 0
    private var bestDist = Double.MAX_VALUE
    private var stallTicks = 0
    private var failure = ""
    private var savedAllowBreak: Boolean? = null
    private var savedAllowPlace: Boolean? = null

    /** Человекочитаемая причина, почему до сундука не дойти. */
    fun failure(): String {
        return failure
    }

    fun travelTo(canonical: BlockPos?): Status {
        val mc = Minecraft.getInstance()
        // Без Baritone путь построить нечем: честно завершаемся с причиной.
        if (!isBaritoneAvailable()) {
            failure = "Baritone не установлен"
            return Status.UNREACHABLE
        }
        val player = mc.player!!
        val level = mc.level!!
        if (player == null || level == null || canonical == null) {
            failure = "не в мире"
            return Status.UNREACHABLE
        }

        if (canonical != target) {
            stop()
            target = canonical
            attempts = 0
            stallTicks = 0
            bestDist = Double.MAX_VALUE
            failure = ""
        }

        // Уже стоим так, что сундук кликается — идти больше некуда
        if (openHit(canonical) != null) {
            stop()
            return Status.ARRIVED
        }

        val b = baritone()
        if (b == null) {
            failure = "Baritone недоступен"
            return Status.UNREACHABLE
        }

        sinceIssue++
        val precise = regionLoaded(canonical)
        val phaseChanged = issued != null && precise != issuedPrecise
        val idle = issued != null &&
         !phaseChanged &&
         sinceIssue > 20 &&
         !b.pathingBehavior.isPathing &&
         !b.customGoalProcess.isActive

        if (issued == null || phaseChanged || idle) {
            if (phaseChanged) attempts = 0

            if (idle) {
                val feet = player.blockPosition()
                // Baritone считает цель достигнутой, но кликнуть нечем —
                // это не «не дошли», это «встали не туда»
                if (issuedPrecise && issued!!.isInGoal(feet.x, feet.y, feet.z)) {
                    failure = "дошли, но ни одна точка рядом не видит сундук"
                    return Status.UNREACHABLE
                }
                if (++attempts > MAX_ATTEMPTS) {
                    failure = "Baritone не нашёл путь"
                    return Status.UNREACHABLE
                }
            }

            // Чанк не загружен — точек стояния не знаем, идём «примерно»
            val goal: Goal? = if (precise) preciseGoal(canonical) else GoalNear(canonical, SPOT_RADIUS - 1)
            if (goal == null) {
                failure = "нет места, откуда сундук достать"
                return Status.UNREACHABLE
            }

            issued = goal
            issuedPrecise = precise
            sinceIssue = 0
            // setGoal, а не setGoalAndPath: расчёт пути идёт в пуле Baritone,
            // render-тред не мёрзнет. Простой/провал ловят idle/attempts/таймауты.
            b.customGoalProcess.setGoal(goal)
        }

        val dist = player.position().distanceTo(Vec3.atCenterOf(canonical))
        if (dist < bestDist - 0.5) {
            bestDist = dist
            stallTicks = 0
        } else if (++stallTicks > STALL_TICKS) {
            failure = "перестал приближаться"
            return Status.UNREACHABLE
        }

        return Status.TRAVELLING
    }

    fun stop() {
        target = null
        issued = null
        sinceIssue = 0
        attempts = 0
        stallTicks = 0
        bestDist = Double.MAX_VALUE

        try {
            val b = baritone()
            if (b != null && (b.pathingBehavior.isPathing || b.customGoalProcess.isActive)) {
                b.pathingBehavior.cancelEverything()
            }
        } catch (ignored: Throwable) {
        }
    }

    /** Лучший видимый сундук из текущей точки, либо null. */
    fun openHit(canonical: BlockPos?): BlockHitResult? {
        val mc = Minecraft.getInstance()
        val player = mc.player!!
        val level = mc.level!!
        if (player == null || level == null || canonical == null) return null

        val eye = player.getEyePosition()
        var best: BlockHitResult? = null
        var bestD = Double.MAX_VALUE

        for (half in halves(canonical)) {
            val d = cubeDistance(eye, half)
            if (d > 4.2 || d >= bestD) continue
            val hit = trace(eye, half)
            if (hit != null) {
                best = hit
                bestD = d
            }
        }

        return best
    }

    /** На сортировке ломать и ставить блоки нельзя — это заметно на античите. */
    fun setSafePathing(on: Boolean) {
        if (!on) {
            restorePathing()
            return
        }

        try {
            val s: Settings = BaritoneAPI.getSettings()
            if (savedAllowBreak == null) {
                savedAllowBreak = s.allowBreak.value
                savedAllowPlace = s.allowPlace.value
            }
            s.allowBreak.value = false
            s.allowPlace.value = false
        } catch (ignored: Throwable) {
        }
    }

    fun restorePathing() {
        val savedBreak = savedAllowBreak ?: return
        val savedPlace = savedAllowPlace!!

        try {
            val s: Settings = BaritoneAPI.getSettings()
            s.allowBreak.value = savedBreak
            s.allowPlace.value = savedPlace
        } catch (ignored: Throwable) {
        }

        savedAllowBreak = null
        savedAllowPlace = null
    }

    private fun preciseGoal(canonical: BlockPos): Goal? {
        val spots = standSpots(canonical)
        if (spots.isEmpty()) return null
        if (spots.size == 1) return GoalBlock(spots[0])

        val goals = spots.map { GoalBlock(it) }.toTypedArray()
        return GoalComposite(*goals)
    }

    /** Все места, с которых сундук виден, ближайшие к игроку — не дальше MAX_SPOTS. */
    private fun standSpots(canonical: BlockPos): List<BlockPos> {
        val halves = halves(canonical)
        val seen = HashSet<BlockPos>()
        val candidates = ArrayList<BlockPos>()

        for (half in halves) {
            for (dy in -SPOT_DOWN..SPOT_UP) {
                for (dx in -SPOT_RADIUS..SPOT_RADIUS) {
                    for (dz in -SPOT_RADIUS..SPOT_RADIUS) {
                        val p = half.offset(dx, dy, dz)
                        if (seen.add(p) && standable(p) && inReach(eyeAt(p), halves)) {
                            candidates.add(p.immutable())
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val player: LocalPlayer = Minecraft.getInstance().player!!
        val me = player.position()
        candidates.sortBy { px -> me.distanceToSqr(Vec3.atCenterOf(px)) }

        val visible = ArrayList<BlockPos>()
        var traced = 0
        for (p in candidates) {
            // Проверка видимости — это луч: дорого, поэтому ограничиваем
            if (visible.size >= MAX_SPOTS || traced >= MAX_TRACES) break
            traced++

            val eye = eyeAt(p)
            for (half in halves) {
                if (cubeDistance(eye, half) <= SPOT_REACH && trace(eye, half) != null) {
                    visible.add(p)
                    break
                }
            }
        }

        if (visible.isNotEmpty()) return visible
        return if (candidates.size <= MAX_SPOTS) candidates else candidates.subList(0, MAX_SPOTS)
    }

    companion object {
        private const val SPOT_RADIUS = 4
        private const val SPOT_UP = 3
        private const val SPOT_DOWN = 3
        private const val SPOT_REACH = 3.8
        private const val MAX_SPOTS = 16
        private const val MAX_TRACES = 64
        private const val MAX_ATTEMPTS = 3
        private const val STALL_TICKS = 400
        private const val EYE_HEIGHT = 1.62

        private fun halves(canonical: BlockPos): List<BlockPos> {
            val other = ContainerGeometry.otherHalf(Minecraft.getInstance().level, canonical)
            return if (other == null) listOf(canonical) else listOf(canonical, other)
        }

        private fun eyeAt(feet: BlockPos): Vec3 {
            return Vec3(feet.x + 0.5, feet.y + EYE_HEIGHT, feet.z + 0.5)
        }

        private fun inReach(eye: Vec3, halves: List<BlockPos>): Boolean {
            for (half in halves) if (cubeDistance(eye, half) <= SPOT_REACH) return true
            return false
        }

        /** Расстояние от глаза до кубика блока, а не до его центра. */
        private fun cubeDistance(eye: Vec3, pos: BlockPos): Double {
            val dx = max(max(pos.x - eye.x, 0.0), eye.x - (pos.x + 1))
            val dy = max(max(pos.y - eye.y, 0.0), eye.y - (pos.y + 1))
            val dz = max(max(pos.z - eye.z, 0.0), eye.z - (pos.z + 1))
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        /** Виден ли блок из точки глаза: луч в ближайшую точку его контура. */
        private fun trace(eye: Vec3, pos: BlockPos): BlockHitResult? {
            val mc = Minecraft.getInstance()
            val level = mc.level!!
            val shape = level.getBlockState(pos).getShape(level, pos)
            val box: AABB = if (shape.isEmpty) AABB(pos) else shape.bounds().move(pos)
            val aim = Vec3(clamp(eye.x, box.minX, box.maxX), clamp(eye.y, box.minY, box.maxY), clamp(eye.z, box.minZ, box.maxZ))
            val dir = aim.subtract(eye)
            val len = dir.length()
            if (len < 1.0E-4) return null

            val end = aim.add(dir.scale(0.1 / len))
            val hit = level.clip(
                ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player!!)
            )
            return if (hit.type == HitResult.Type.BLOCK && hit.blockPos == pos) hit else null
        }

        /** Можно ли стоять: ноги и голова свободны, под ногами опора, рядом безопасно. */
        private fun standable(pos: BlockPos): Boolean {
            val mc = Minecraft.getInstance()
            val level = mc.level!!
            if (!level.hasChunkAt(pos)) return false

            val above = pos.above()
            val below = pos.below()
            val feet = level.getBlockState(pos)
            val head = level.getBlockState(above)
            val floor = level.getBlockState(below)

            if (!feet.getCollisionShape(level, pos).isEmpty) return false
            if (!head.getCollisionShape(level, above).isEmpty) return false
            if (floor.getCollisionShape(level, below).isEmpty) return false

            return !hazard(feet) && !hazard(head) && !hazard(floor)
        }

        private fun hazard(state: BlockState): Boolean {
            return state.fluidState.`is`(FluidTags.LAVA) ||
         state.`is`(BlockTags.FIRE) ||
         state.`is`(Blocks.MAGMA_BLOCK) ||
         state.`is`(Blocks.CACTUS) ||
         state.`is`(Blocks.POWDER_SNOW) ||
         state.`is`(Blocks.SWEET_BERRY_BUSH) ||
         state.`is`(Blocks.WITHER_ROSE)
        }

        /** Загружен ли весь квадрат, в котором ищем точки стояния. */
        private fun regionLoaded(pos: BlockPos): Boolean {
            val mc = Minecraft.getInstance()
            val level = mc.level!!
            return level.hasChunkAt(pos) &&
         level.hasChunkAt(pos.offset(SPOT_RADIUS, 0, SPOT_RADIUS)) &&
         level.hasChunkAt(pos.offset(SPOT_RADIUS, 0, -SPOT_RADIUS)) &&
         level.hasChunkAt(pos.offset(-SPOT_RADIUS, 0, SPOT_RADIUS)) &&
         level.hasChunkAt(pos.offset(-SPOT_RADIUS, 0, -SPOT_RADIUS))
        }

        private fun clamp(v: Double, min: Double, max: Double): Double {
            return if (v < min) min else min(v, max)
        }

        private fun isBaritoneAvailable(): Boolean {
            return try {
                Class.forName("baritone.api.BaritoneAPI") != null && baritone() != null
            } catch (ignored: Throwable) {
                false
            }
        }

        private fun baritone(): IBaritone? {
            return try {
                BaritoneAPI.getProvider().getPrimaryBaritone()
            } catch (ignored: Throwable) {
                null
            }
        }
    }

    enum class Status {
        TRAVELLING,
        ARRIVED,
        UNREACHABLE
    }
}
