package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.utils.world.HoleInfo
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.settings.NamedEntry
import java.util.Comparator
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

enum class HoleSnapPriorityChoice(
    private val displayName: String,
    val comparator: Comparator<HoleInfo>
) : ClientAccess, NamedEntry {
    DISTANCE(
        "Distance",
        Comparator.comparing<HoleInfo, Float> { hole ->
            val block: BlockPos = hole.pos
            val center: Vec3 = block.center
            val capturedPlayer = ClientAccess.collectiveMinecraft.player!!
            val playerPosition = capturedPlayer.position()
            Vec3Utils.horizontalDistance(center, playerPosition)
        }
    ),
    CROSSHAIR(
        "Crosshair",
        Comparator.comparing<HoleInfo, Float> { hole ->
            val gameRenderer = ClientAccess.collectiveMinecraft.gameRenderer
            val camera = gameRenderer.mainCamera
            val cameraYaw = camera.yaw()

            val block: BlockPos = hole.pos
            val center: Vec3 = block.center

            val targetYaw = RotationManager.getRotationsToPosition(center)[0]
            val yawDifference = Mth.degreesDifferenceAbs(cameraYaw, targetYaw)
            yawDifference
        }
    );

    override fun getName(): String = displayName
}
