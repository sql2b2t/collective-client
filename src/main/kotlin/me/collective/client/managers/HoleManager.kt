package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockStateUtils
import me.collective.client.utils.world.HoleInfo
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.Plane
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.Portal
import net.minecraft.world.phys.AABB
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HoleManager : ClientAccess {
    @Volatile
    private var holesState: List<HoleInfo> = ArrayList()
    private var inHoleState: Boolean = false

    init {
        ClientAccess.eventBus.subscribe(this)
        scanExecutor.scheduleAtFixedRate(HoleScanTask(this), 0L, 50L, TimeUnit.MILLISECONDS)
    }

    val holes: List<HoleInfo>
        @JvmName("getHolesProp")
        get() = holesState

    fun getHoles(): List<HoleInfo> {
        return holesState
    }

    fun setHoles(updatedHoles: List<HoleInfo>) {
        holesState = updatedHoles
    }

    val isInHole: Boolean
        @JvmName("isInHoleProp")
        get() = inHoleState

    fun isInHole(): Boolean {
        return inHoleState
    }

    fun intersectsHole(position: BlockPos): Boolean {
        return intersectsHole(AABB(position))
    }

    fun intersectsHole(bounds: AABB): Boolean {
        return holesState.any { hole -> hole.bounds.intersects(bounds) }
    }

    fun findIntersectingHole(position: BlockPos): HoleInfo? {
        val targetBounds = AABB(position)
        return holesState.firstOrNull { hole -> hole.bounds.intersects(targetBounds) }
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        val roundedPosition = PlayerStateUtils.getLocalRoundedBlockPosition()
        inHoleState = intersectsHole(roundedPosition)
    }

    companion object {
        private val horizontalDirections: Array<Direction> = Plane.HORIZONTAL.toList().toTypedArray()
        private val scanExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        private val classificationCursor = MutableBlockPos()

        @JvmStatic
        internal fun classifyHole(position: BlockPos, fromDirection: Direction?): HoleInfo? {
            val level = ClientAccess.collectiveMinecraft.level ?: return null
            classificationCursor.set(position)
            if (BlockStateUtils.isBlockingExceptCarpet(classificationCursor)) {
                return null
            }
            classificationCursor.move(Direction.DOWN)
            val floorState = level.getBlockState(classificationCursor)
            if (!BlockStateUtils.isBlocking(floorState)) {
                return null
            }
            if (floorState.block is Portal || floorState.block is LiquidBlock) {
                return null
            }
            classificationCursor.move(Direction.UP, 2)
            if (BlockStateUtils.isBlocking(classificationCursor)) {
                return null
            }
            classificationCursor.move(Direction.UP)
            var trapped = BlockStateUtils.isBlocking(classificationCursor)
            var safety = if (BlockStateUtils.isBlastResistantUnbreakable(floorState)) {
                HoleInfo.Safety.SAFE
            } else {
                HoleInfo.Safety.UNSAFE
            }
            var doubleDirection: Direction? = null
            for (direction in horizontalDirections) {
                if (fromDirection == direction.opposite) {
                    continue
                }
                classificationCursor.set(position)
                classificationCursor.move(direction)
                val sideState = level.getBlockState(classificationCursor)
                if (!BlockStateUtils.isBlocking(classificationCursor)) {
                    if (doubleDirection != null || fromDirection != null) {
                        return null
                    }
                    val adjacentBlock = position.relative(direction)
                    val adjacentHole = classifyHole(adjacentBlock, direction) ?: return null
                    if (!adjacentHole.trapped) {
                        trapped = false
                    }
                    if (safety == HoleInfo.Safety.SAFE) {
                        safety = adjacentHole.safety
                    }
                    doubleDirection = direction
                    continue
                }
                if (sideState.`is`(Blocks.RESPAWN_ANCHOR)) {
                    val anchorPolicy = ClientContext.worldStateManager.getDimension()
                    if (!anchorPolicy.isNether) {
                        return null
                    }
                }
                if (BlockStateUtils.isWeakOrInstantlyBreakable(sideState)) {
                    return null
                }
                if (BlockStateUtils.isBlastResistantBreakable(sideState)) {
                    safety = HoleInfo.Safety.UNSAFE
                }
            }
            return HoleInfo(safety, position.immutable(), createHoleBounds(position, doubleDirection), trapped)
        }

        private fun createHoleBounds(position: BlockPos, face: Direction?): AABB {
            if (face == null) return AABB(position)
            val box = AABB(position)
            val unitVec = face.unitVec3i
            return box.expandTowards(unitVec.x.toDouble(), 0.0, unitVec.z.toDouble())
        }
    }
}
