package me.collective.client.events

import me.collective.client.ClientAccess

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

class VehicleMoveEvent(velocity: Vec3) : LocalPlayerMoveEvent(velocity, null) {
    companion object {
        @JvmStatic
        fun isLocalPlayerVehicle(candidate: Any?): Boolean {
            val player = ClientAccess.collectiveMinecraft.player ?: return false
            return candidate is Entity && player.vehicle === candidate
        }
    }
}
