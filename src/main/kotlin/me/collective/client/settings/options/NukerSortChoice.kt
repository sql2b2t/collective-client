package me.collective.client.settings.options

import net.minecraft.world.phys.Vec3
import me.collective.client.ClientAccess
import me.collective.client.settings.NamedEntry

import java.util.function.ToDoubleFunction
import net.minecraft.core.BlockPos

enum class NukerSortChoice(
    private val displayName: String,
    private val sortKeyFunction: ToDoubleFunction<BlockPos>
) : NamedEntry {
    CLOSEST(
        "Closest",
        ToDoubleFunction { blockPos: BlockPos ->
            val playerForEye = ClientAccess.collectiveMinecraft.player!!
            val eyeSnapshot = playerForEye.getEyePosition()
            val blockCenter = Vec3.atCenterOf(blockPos)
            eyeSnapshot.distanceToSqr(blockCenter)
        }
    ),
    FURTHEST(
        "Furthest",
        ToDoubleFunction { blockPos: BlockPos ->
            val playerForEye = ClientAccess.collectiveMinecraft.player!!
            val eyeSnapshot = playerForEye.getEyePosition()
            val blockCenter = Vec3.atCenterOf(blockPos)
            -eyeSnapshot.distanceToSqr(blockCenter)
        }
    ),
    TOP("Top", ToDoubleFunction { blockPos: BlockPos -> -blockPos.getY().toDouble() }),
    BOTTOM("Bottom", ToDoubleFunction { blockPos: BlockPos -> blockPos.getY().toDouble() });

    fun getSortKey(blockPos: BlockPos): Double {
        return sortKeyFunction.applyAsDouble(blockPos)
    }

    override fun getName(): String = displayName
}
