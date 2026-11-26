package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.utils.math.MathConstants
import me.collective.client.settings.NamedEntry

import java.util.ArrayList
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

enum class BlockSampleMode(private val displayName: String, vararg initialOffsets: Vec3) : ClientAccess, NamedEntry {
    FAST(
        "Fast",
        Vec3(
            MathConstants.HALF,
            MathConstants.HALF,
            MathConstants.HALF
        )
    ),
    X2(
        "X2",
        Vec3(
            MathConstants.HALF,
            0.05,
            MathConstants.HALF
        ),
        Vec3(
            MathConstants.HALF,
            1.0,
            MathConstants.HALF
        )
    ),
    X4("X4", Vec3(0.05, 0.05, 0.05), Vec3(0.05, 0.05, 0.95), Vec3(0.95, 0.05, 0.05), Vec3(0.95, 0.05, 0.95)),
    X8(
        "X8",
        Vec3(0.05, 0.05, 0.05), Vec3(0.05, 0.05, 0.95),
        Vec3(0.05, 0.95, 0.05), Vec3(0.05, 0.95, 0.95),
        Vec3(0.95, 0.05, 0.05), Vec3(0.95, 0.05, 0.95),
        Vec3(0.95, 0.95, 0.05), Vec3(0.95, 0.95, 0.95)
    );

    val offsets: List<Vec3> = initialOffsets.toList()

    override fun getName(): String = displayName

    fun sampleBlock(position: BlockPos): List<Vec3> {
        return offsets.map { point ->
            val blockOrigin = Vec3.atLowerCornerOf(position)
            var shiftedPoint = point.add(blockOrigin)
            if (this == X4) {
                val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
                val eyeY = player.eyeY
                if (eyeY > position.y + 1) {
                    shiftedPoint = shiftedPoint.add(0.0, 0.9, 0.0)
                }
            }

            shiftedPoint
        }
    }

    private fun isOppositeFaceCoordinate(step: Int, roundedCoordinate: Int): Boolean {
        return step == 1 && roundedCoordinate == 0 || step == -1 && roundedCoordinate == 1
    }

    fun sampleFace(position: BlockPos, face: Direction): List<Vec3> {
        val samples = ArrayList<Vec3>()
        for (offset in offsets) {
            var axisStep = face.stepX
            val x = offset.x
            val roundedX = Math.round(x).toInt()
            if (isOppositeFaceCoordinate(axisStep, roundedX)) {
                continue
            }
            axisStep = face.stepY
            val y = offset.y
            val roundedY = Math.round(y).toInt()
            if (isOppositeFaceCoordinate(axisStep, roundedY)) {
                continue
            }
            axisStep = face.stepZ
            val z = offset.z
            val roundedZ = Math.round(z).toInt()
            if (isOppositeFaceCoordinate(axisStep, roundedZ)) {
                continue
            }
            val blockOrigin = Vec3.atLowerCornerOf(position)
            samples.add(offset.add(blockOrigin))
        }

        return samples
    }
}
