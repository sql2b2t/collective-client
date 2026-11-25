package me.collective.client.modules.combat

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.movement.FireworksModule
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Pose
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import java.util.ArrayList

internal class AutoMaceStrikePlanner {
    private val collisionCache = Long2ObjectOpenHashMap<List<AABB>>()
    private val path: MutableList<Vec3> = ArrayList()
    private var collisionContext: CollisionContext? = null
    private var playerBody: AABB = AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    private var targetBox: AABB = AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    private var playerPosition: Vec3 = Vec3.ZERO
    private var initialVelocity: Vec3 = Vec3.ZERO
    private var position: Vec3 = Vec3.ZERO
    private var velocity: Vec3 = Vec3.ZERO
    private var look: Vec3 = Vec3.ZERO
    private var controlVelocity: Vec3 = Vec3.ZERO

    private var playerX = 0.0
    private var playerZ = 0.0
    private var targetX = 0.0
    private var targetY = 0.0
    private var targetZ = 0.0
    private var targetMotionX = 0.0
    private var targetMotionZ = 0.0

    private var attackRange = 0.0
    private var eyeHeight = 0.0
    private var gravity = 0.0
    private var controlSpeed = 0.0
    private var verticalSpeed = 0.0
    private var glide = 0.0
    private var boostBudget = 0.0

    private var initialFallDistance = 0.0
    private var initialYaw = 0f
    private var initialPitch = 0f
    private var yaw = 0f
    private var pitch = 0f
    private var simulationYaw = 0f
    private var simulationPitch = 0f

    private var active = false
    private var control = false
    private var usingRocket = false
    private var rocketActive = false
    private var descend = false
    private var simulationDown = false
    private var selectedDown = false

    private var simulationStage = 0
    private var selectedHitTicks = Int.MAX_VALUE
    private var selectedRotation: FloatArray? = null

    private var selectedOffset: Vec3? = null
    private var selectedMode = 0
    private var refreshTicks = 0
    private var searchCursor = 0
    private var solution = false
    private var simulationCount = 0
    private var score = 0.0
    private var limitReached = false
    private var pathBlocked = false
    private var simulationHitTicks = 0

    fun reset() {
        active = false
        solution = false
        selectedOffset = null
        selectedMode = 0
        selectedRotation = null
        selectedHitTicks = Int.MAX_VALUE
        selectedDown = false
        refreshTicks = 0
        searchCursor = 0
        path.clear()
        collisionCache.clear()
    }

    val isActive: Boolean @JvmName("isActiveProp") get() = isActive()
    fun isActive(): Boolean = active && solution

    fun rotation(): FloatArray? = if (isActive()) selectedRotation else null

    fun hitTicks(): Int = if (isActive()) selectedHitTicks else Int.MAX_VALUE

    fun shouldDescend(): Boolean = isActive() && selectedDown

    fun updateNormal(target: Entity, range: Double, rockets: Boolean, budget: Double) {
        control = false
        usingRocket = rockets
        boostBudget = budget
        update(target, range)
    }

    fun updateControl(target: Entity, range: Double, speed: Double, vSpeed: Double, glideSpeed: Double) {
        control = true
        usingRocket = false
        boostBudget = Double.POSITIVE_INFINITY
        controlSpeed = speed
        verticalSpeed = vSpeed
        glide = glideSpeed
        update(target, range)
    }

    private fun update(target: Entity, range: Double) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        active = true
        collisionCache.clear()
        val context = CollisionContext.of(player)
        collisionContext = context
        val dimensions = player.getDimensions(Pose.FALL_FLYING)
        playerBody = dimensions.makeBoundingBox(0.0, 0.0, 0.0).inflate(0.1)
        eyeHeight = dimensions.eyeHeight.toDouble()
        gravity = player.gravity
        targetBox = target.boundingBox
        targetX = target.x
        targetY = target.y
        targetZ = target.z
        targetMotionX = targetX - target.xo
        targetMotionZ = targetZ - target.zo
        val targetSpeed = Math.sqrt(targetMotionX * targetMotionX + targetMotionZ * targetMotionZ)
        if (targetSpeed > 1.0) {
            targetMotionX /= targetSpeed
            targetMotionZ /= targetSpeed
        }
        playerPosition = player.position()
        playerX = playerPosition.x
        playerZ = playerPosition.z
        initialVelocity = ClientContext.playerStateTracker.tickDisplacement ?: Vec3.ZERO
        initialFallDistance = PlayerStateUtils.getTrackedFallDistance().toDouble()
        val rotations = ClientContext.playerStateTracker.packetRotations
        initialYaw = rotations[0]
        initialPitch = rotations[1]
        attackRange = range
        simulationCount = 0
        path.clear()

        val previousWorks = solution && simulate(selectedOffset, selectedMode, path)
        var good = previousWorks && acceptable()
        var finalWorks = previousWorks
        if (!good && (selectedOffset != null || !solution)) {
            finalWorks = false
            if (simulate(null, 1, null) && acceptable()) {
                selectedOffset = null
                selectedMode = 1
                good = true
                finalWorks = true
                solution = true
                searchCursor = 0
                refreshTicks = 0
            }
        }
        var search = false
        if (previousWorks) {
            if (!good && ++refreshTicks >= 10) search = true
        } else if (!solution && searchCursor == 0) {
            if (++refreshTicks >= 5) search = true
        } else {
            search = true
        }
        if (search) {
            refreshSearch(previousWorks)
            finalWorks = false
        }
        if (!finalWorks) {
            path.clear()
            finalWorks = solution && simulate(selectedOffset, selectedMode, path)
        }
        if (finalWorks) {
            selectedMode = simulationStage
            selectedHitTicks = simulationHitTicks
            selectedRotation = floatArrayOf(simulationYaw, simulationPitch)
            selectedDown = simulationDown
        } else {
            solution = false
            selectedRotation = null
            selectedHitTicks = Int.MAX_VALUE
            path.clear()
        }
    }

    private fun candidateOffsets(): List<Vec3> {
        val candidates = ArrayList<Vec3>(194)
        for (height in CENTER_HEIGHTS) {
            candidates.add(Vec3(0.0, height, 0.0))
        }
        val bearing = Mth.atan2(playerZ - targetZ, playerX - targetX)
        for (index in 0 until 16) {
            val offset = (index + 1) / 2 * (if (index % 2 == 1) 1 else -1)
            val angle = bearing + offset * Math.PI * 2.0 / 16.0
            for (radius in RADII) {
                for (height in HEIGHTS) {
                    candidates.add(Vec3(Math.cos(angle) * radius, height, Math.sin(angle) * radius))
                }
            }
        }
        return candidates
    }

    private fun collisionBoxes(x: Int, y: Int, z: Int): List<AABB> {
        val key = BlockPos.asLong(x, y, z)
        val cached = collisionCache.get(key)
        if (cached != null) return cached
        val level = ClientAccess.collectiveMinecraft.level ?: return emptyList()
        val context = collisionContext ?: CollisionContext.empty()
        val position = BlockPos(x, y, z)
        val shape = level.getBlockState(position).getCollisionShape(level, position, context)
        val boxes = ArrayList<AABB>()
        for (box in shape.toAabbs()) {
            boxes.add(box.move(x.toDouble(), y.toDouble(), z.toDouble()))
        }
        collisionCache.put(key, boxes)
        return boxes
    }

    private fun candidateClear(offset: Vec3): Boolean {
        val bounds = playerBody.move(targetX + offset.x, targetY + offset.y, targetZ + offset.z)
        for (x in Mth.floor(bounds.minX) until Mth.ceil(bounds.maxX)) {
            for (z in Mth.floor(bounds.minZ) until Mth.ceil(bounds.maxZ)) {
                for (y in Mth.floor(bounds.minY) - 1 until Mth.ceil(bounds.maxY)) {
                    for (box in collisionBoxes(x, y, z)) {
                        if (box.intersects(bounds)) return false
                    }
                }
            }
        }
        return true
    }

    private fun pointClear(x: Double, y: Double, z: Double): Boolean {
        val bx = Mth.floor(x)
        val by = Mth.floor(y)
        val bz = Mth.floor(z)
        for (yy in by - 1..by) {
            for (box in collisionBoxes(bx, yy, bz)) {
                if (box.intersects(x, y, z, x, y, z)) return false
            }
        }
        return true
    }

    private fun lineClear(from: Vec3, to: Vec3): Boolean {
        val delta = to.subtract(from)
        val steps = Math.max(1, Mth.ceil(delta.length() / 0.25))
        for (i in 0 until steps) {
            val point = from.add(delta.scale(i.toDouble() / steps))
            if (!pointClear(point.x, point.y, point.z)) return false
        }
        return true
    }

    private fun bodyClear(pos: Vec3): Boolean {
        val bounds = playerBody.move(pos.x, pos.y, pos.z)
        for (x in Mth.floor(bounds.minX) until Mth.ceil(bounds.maxX)) {
            for (z in Mth.floor(bounds.minZ) until Mth.ceil(bounds.maxZ)) {
                for (y in Mth.floor(bounds.minY) - 1 until Mth.ceil(bounds.maxY)) {
                    for (box in collisionBoxes(x, y, z)) {
                        if (box.intersects(bounds)) return false
                    }
                }
            }
        }
        return true
    }

    private fun movementClear(pos: Vec3, vel: Vec3): Boolean {
        val steps = Math.max(1, Mth.ceil(vel.length() / 0.5))
        for (i in 1..steps) {
            if (!bodyClear(pos.add(vel.scale(i.toDouble() / steps)))) return false
        }
        return true
    }

    private fun inAttackRange(pos: Vec3, tick: Int): Boolean {
        val eye = pos.add(0.0, eyeHeight, 0.0)
        val movingTarget = targetBox.move(targetMotionX * tick, 0.0, targetMotionZ * tick)
        val nearest = BoundingBoxUtils.clampToBounds(eye, movingTarget)
        return eye.distanceToSqr(nearest) <= attackRange * attackRange && lineClear(eye, nearest)
    }

    private fun followOnHit(startPos: Vec3, startVel: Vec3, tick: Int): Boolean {
        var p = startPos
        var v = startVel
        for (i in 0 until 6) {
            if (inAttackRange(p, tick + i)) return true
            if (!movementClear(p, v)) return false
            p = p.add(v)
            v = Vec3(
                v.x * 0.9100000262260437,
                (v.y - gravity) * 0.9800000190734863,
                v.z * 0.9100000262260437
            )
        }
        return false
    }

    private fun setAim(direction: Vec3, clampPitch: Boolean) {
        yaw = Mth.wrapDegrees((Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI) - 90.0).toFloat())
        pitch = (Mth.atan2(-direction.y, Math.sqrt(direction.x * direction.x + direction.z * direction.z)) * (180.0 / Math.PI)).toFloat()
        if (clampPitch) pitch = Math.max(0f, pitch)
        val pitchRad = pitch.toDouble() * (Math.PI / 180.0)
        val yawRad = -yaw.toDouble() * (Math.PI / 180.0)
        val cosPitch = Math.cos(pitchRad)
        look = Vec3(
            Math.sin(yawRad) * cosPitch,
            -Math.sin(pitchRad),
            Math.cos(yawRad) * cosPitch
        )
    }

    private fun elytraVelocity(current: Vec3): Vec3 {
        val p = pitch * (Math.PI.toFloat() / 180.0f)
        val cos = Math.cos(p.toDouble())
        val cosSquared = cos * cos
        val lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z)
        val horizontal = Math.sqrt(current.x * current.x + current.z * current.z)
        var x = current.x
        var y = current.y + gravity * (-1.0 + cosSquared * 0.75)
        var z = current.z

        if (lookHorizontal > 0.0) {
            if (y < 0.0) {
                val lift = y * -0.1 * cosSquared
                x += look.x * lift / lookHorizontal
                y += lift
                z += look.z * lift / lookHorizontal
            }
            if (p < 0.0f) {
                val lift = horizontal * -Mth.sin(p) * 0.04
                x -= look.x * lift / lookHorizontal
                y += lift * 3.2
                z -= look.z * lift / lookHorizontal
            }
            x += (look.x / lookHorizontal * horizontal - x) * 0.1
            z += (look.z / lookHorizontal * horizontal - z) * 0.1
        }
        return Vec3(
            x * 0.9900000095367432,
            y * 0.9800000190734863,
            z * 0.9900000095367432
        )
    }

    private fun aimAt(target: Vec3, tick: Int) {
        val delta = target.subtract(position)
        if (control) {
            val horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z)
            setAim(delta, true)
            val dx = targetX + targetMotionX * tick - position.x
            val dz = targetZ + targetMotionZ * tick - position.z
            val targetDistance = Math.sqrt(dx * dx + dz * dz)
            val speed = Math.min(Math.min(controlSpeed, targetDistance), 1.0)
            descend = delta.y < 0.0 && verticalSpeed > 0.0 &&
                (descend || horizontal <= -delta.y / verticalSpeed * speed)
            controlVelocity = Vec3(
                if (horizontal < 0.0001) 0.0 else delta.x / horizontal * speed,
                if (descend) -verticalSpeed else -glide,
                if (horizontal < 0.0001) 0.0 else delta.z / horizontal * speed
            )
            return
        }
        val length = delta.length()
        val desired = if (length < 0.0001) Vec3(0.0, -1.0, 0.0) else delta.scale(1.0 / length)
        var adjusted = desired
        if (usingRocket) {
            for (i in 0 until 2) {
                setAim(adjusted, false)
                val projectedVelocity = elytraVelocity(velocity).scale(if (rocketActive) 1.0 else 0.5)
                val perpendicular = projectedVelocity.subtract(desired.scale(projectedVelocity.dot(desired)))
                val amount = perpendicular.length()
                val gain = if (rocketActive) 0.8 else 0.85
                adjusted = if (amount >= gain) {
                    perpendicular.scale(-1.0)
                } else {
                    desired.scale(Math.sqrt(1.0 - amount * amount / (gain * gain)))
                        .subtract(perpendicular.scale(1.0 / gain))
                }
            }
        }
        setAim(adjusted, true)
    }

    private fun simulate(offset: Vec3?, requestedStage: Int, outPath: MutableList<Vec3>?): Boolean {
        position = playerPosition
        velocity = initialVelocity
        var fallDistance = initialFallDistance
        limitReached = false
        pathBlocked = false
        rocketActive = false
        var previousYaw = initialYaw
        var previousPitch = initialPitch
        descend = control && velocity.y < -glide - 0.01
        var stage = if (offset == null) 1 else requestedStage
        val waypoint = if (offset == null) {
            Vec3(targetX, targetY, targetZ)
        } else {
            Vec3(targetX + offset.x, targetY + offset.y, targetZ + offset.z)
        }
        if (inAttackRange(position, 0)) {
            aimAt(Vec3(targetX, targetY + 0.25, targetZ), 0)
            simulationStage = 1
            simulationYaw = yaw
            simulationPitch = pitch
            simulationDown = descend
            simulationHitTicks = 0
            score = fallDistance
            return true
        }
        for (tick in 0 until 80) {
            simulationCount++
            val mx = targetMotionX * tick
            val mz = targetMotionZ * tick
            val movedTarget = Vec3(targetX + mx, targetY, targetZ + mz)
            rocketActive = usingRocket && position.distanceToSqr(movedTarget) > boostBudget * boostBudget
            val movedWaypoint = waypoint.add(mx, 0.0, mz)
            if (stage == 0 && (position.y < waypoint.y || position.distanceToSqr(movedWaypoint) <= 9.0)) {
                stage = 1
            }
            aimAt(if (stage == 0) movedWaypoint else movedTarget.add(0.0, 0.25, 0.0), tick)
            if (tick == 0) {
                simulationStage = stage
                simulationYaw = yaw
                simulationPitch = pitch
                simulationDown = descend
            }
            if (velocity.y > -0.5 && fallDistance > 1.0) {
                fallDistance = 1.0
                limitReached = true
            }
            val previousPosition = position
            val previousVelocity = velocity
            velocity = if (control) controlVelocity else elytraVelocity(velocity)
            if (rocketActive) {
                velocity = velocity.add(FireworksModule.getGrimBoost(yaw, pitch, previousYaw, previousPitch, velocity))
            }
            previousYaw = yaw
            previousPitch = pitch
            if (!movementClear(position, velocity)) return false
            position = position.add(velocity)
            if (velocity.y < 0.0) fallDistance -= velocity.y
            if (usingRocket && !rocketActive) {
                velocity = velocity.add(look.scale(0.1)).add(look.scale(1.5).subtract(velocity).scale(0.5))
            }
            outPath?.add(position)
            if (inAttackRange(position, tick + 1)) {
                simulationHitTicks = tick + 1
                score = fallDistance - 0.15 * simulationHitTicks
                if (!followOnHit(previousPosition, previousVelocity, tick)) {
                    score -= 10.0
                    pathBlocked = true
                }
                return true
            }
        }
        simulationHitTicks = Int.MAX_VALUE
        score = fallDistance - 100.0
        return true
    }

    private fun acceptable(): Boolean {
        return !limitReached && !pathBlocked && simulationHitTicks != Int.MAX_VALUE
    }

    private fun refreshSearch(previousStillWorks: Boolean) {
        refreshTicks = 0
        val candidates = candidateOffsets()
        var found = false
        var bestOffset: Vec3? = null
        var bestMode = 0
        var bestScore = 0.0
        if (previousStillWorks && simulate(selectedOffset, selectedMode, null)) {
            found = true
            bestOffset = selectedOffset
            bestMode = selectedMode
            bestScore = score + 1.0
        }
        if (simulate(null, 1, null) && (!found || score > bestScore)) {
            found = true
            bestOffset = null
            bestMode = 1
            bestScore = score
        }
        val start = searchCursor
        searchCursor = 0
        for (i in candidates.indices) {
            if (simulationCount >= 3000) {
                searchCursor = (start + i) % candidates.size
                break
            }
            val offset = candidates[(start + i) % candidates.size]
            if (candidateClear(offset) &&
                lineClear(Vec3(targetX + offset.x, targetY + offset.y + eyeHeight, targetZ + offset.z), targetBox.center) &&
                simulate(offset, 0, null) &&
                (!found || score > bestScore)
            ) {
                found = true
                bestOffset = offset
                bestMode = 0
                bestScore = score
                if (acceptable()) break
            }
        }
        solution = found
        if (found && (bestOffset != selectedOffset || bestMode != selectedMode)) {
            selectedOffset = bestOffset
            selectedMode = bestMode
        }
    }

    companion object {
        private val RADII = doubleArrayOf(2.5, 4.5, 7.0, 10.0)
        private val HEIGHTS = doubleArrayOf(0.5, 2.0, 4.0)
        private val CENTER_HEIGHTS = doubleArrayOf(5.0, 9.0)
    }
}
