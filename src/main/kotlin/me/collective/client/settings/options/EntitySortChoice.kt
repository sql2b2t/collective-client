package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.managers.RotationManager
import me.collective.client.settings.NamedEntry
import java.util.Comparator
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity

enum class EntitySortChoice(
    private val displayName: String,
    val comparator: Comparator<Entity>
) : NamedEntry {
    DISTANCE(
        "Distance",
        Comparator.comparingDouble<Entity> { entity ->
            val player = ClientAccess.collectiveMinecraft.player!!
            entity.distanceTo(player).toDouble()
        }
    ),
    CROSSHAIR(
        "Crosshair",
        Comparator.comparingDouble<Entity> { entity ->
            val renderer = ClientAccess.collectiveMinecraft.gameRenderer
            val camera = renderer.mainCamera
            val cameraYaw = camera.yaw()
            val targetYaw = RotationManager.getRotationsToEntity(entity)[0]
            Mth.degreesDifferenceAbs(cameraYaw, targetYaw).toDouble()
        }
    ),
    HEALTH(
        "Health",
        Comparator.comparingDouble<Entity> { entity -> EntityUtils.getHealthWithAbsorption(entity).toDouble() }
    );

    override fun getName(): String = displayName
}
