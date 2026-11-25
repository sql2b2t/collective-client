package me.collective.client.events

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

class CameraSetupEvent(
    val entity: Entity,
    val partialTick: Float,
    x: Double,
    y: Double,
    z: Double
) : CancellableEvent() {
    private var x: Double = x
    private var y: Double = y
    private var z: Double = z

    fun getPosition(): Vec3 = Vec3(x, y, z)

    fun setPosition(position: Vec3) {
        x = position.x
        y = position.y
        z = position.z
    }
}
