package me.collective.client.settings.options

import me.collective.client.ClientAccess
import me.collective.client.managers.RotationManager
import me.collective.client.settings.NamedEntry

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

enum class ChorusControlYawChoice(private val displayName: String) : ClientAccess, NamedEntry {
    KEEP("Keep"),
    INVERT("Invert"),
    POS("Pos");

    override fun getName(): String = displayName

    fun resolveYaw(destination: Vec3, eyePosition: Vec3): Float {
        if (this == KEEP) {
            val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            return player.yRot
        }

        var yaw = RotationManager.calculateRotations(destination, eyePosition)[0]
        if (this == INVERT) {
            yaw += 180.0f
        }

        return yaw
    }
}
