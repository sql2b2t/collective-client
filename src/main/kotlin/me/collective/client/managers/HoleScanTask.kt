package me.collective.client.managers

import me.collective.client.utils.world.HoleInfo
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.world.phys.AABB

class HoleScanTask(
    private val holeManager: HoleManager,
) : Runnable {

    private fun scanNearbyHoles() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        if (minecraft.level == null) {
            return
        }

        val holes = ArrayList<HoleInfo>()
        val coveredPositions = HashSet<Long>()
        val origin = player.blockPosition()
        for (xOffset in -16 until 16) {
            for (yOffset in -16 until 16) {
                for (zOffset in -16 until 16) {
                    val x = origin.x + xOffset
                    val y = origin.y + yOffset
                    val z = origin.z + zOffset
                    scanCursor.set(x, y, z)
                    val positionKey = scanCursor.asLong()
                    if (!coveredPositions.contains(positionKey)) {
                        val hole = HoleManager.classifyHole(scanCursor, null)
                        if (hole != null) {
                            holes.add(hole)
                            addCoveredPositions(coveredPositions, hole.bounds)
                        }
                    }
                }
            }
        }

        holeManager.setHoles(holes)
    }

    override fun run() {
        try {
            scanNearbyHoles()
        } catch (_: Throwable) {
        }
    }

    private fun addCoveredPositions(positions: MutableSet<Long>, bounds: AABB) {
        for (x in bounds.minX.toInt() until bounds.maxX.toInt()) {
            for (z in bounds.minZ.toInt() until bounds.maxZ.toInt()) {
                val y = bounds.minY.toInt()
                positions.add(BlockPos.asLong(x, y, z))
                positions.add(BlockPos.asLong(x, y + 1, z))
                positions.add(BlockPos.asLong(x, y + 2, z))
            }
        }
    }

    companion object {
        private val scanCursor = MutableBlockPos()
    }
}
