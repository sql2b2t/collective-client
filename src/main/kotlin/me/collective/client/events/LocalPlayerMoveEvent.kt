package me.collective.client.events

import me.collective.client.utils.math.Vec3Utils

import net.minecraft.world.entity.MoverType
import net.minecraft.world.phys.Vec3

// Kept open: Java VehicleMoveEvent extends this class.
open class LocalPlayerMoveEvent(velocity: Vec3, moverType: MoverType?) : CancellableEvent() {
    val originalVelocity: Vec3 = Vec3(velocity.x, velocity.y, velocity.z)

    val velocity: Vec3 = velocity

    var moverType: MoverType? = moverType

    val x: Double
        get() = velocity.x

    val y: Double
        get() = velocity.y

    val z: Double
        get() = velocity.z

    fun setVelocity(velocity: Vec3) {
        Vec3Utils.setComponents(
            this.velocity,
            velocity.x,
            velocity.y,
            velocity.z
        )
    }

    fun setY(y: Double) {
        this.setVelocity(
            Vec3(
                this.x,
                y,
                this.z
            )
        )
    }

    fun setHorizontalVelocity(x: Double, z: Double) {
        this.setVelocity(Vec3(x, this.y, z))
    }
}
