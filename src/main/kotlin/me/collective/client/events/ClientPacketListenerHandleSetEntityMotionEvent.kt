package me.collective.client.events

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.world.phys.Vec3

class ClientPacketListenerHandleSetEntityMotionEvent private constructor(
    val entityId: Int,
    velX: Float,
    velY: Float,
    velZ: Float,
    // Trailing marker keeps the primary's JVM signature distinct from the public
    // (Int, Float, Float, Float) secondary constructor.
    @Suppress("UNUSED_PARAMETER") marker: Boolean
) : CancellableEvent() {
    private var velX: Float = velX
    private var velY: Float = velY
    private var velZ: Float = velZ

    var velocityModified: Boolean = false
        private set

    fun isVelocityModified(): Boolean = this.velocityModified

    var velocityX: Float
        get() = velX
        set(value) {
            velX = value
            velocityModified = true
        }

    var velocityY: Float
        get() = velY
        set(value) {
            velY = value
            velocityModified = true
        }

    var velocityZ: Float
        get() = velZ
        set(value) {
            velZ = value
            velocityModified = true
        }

    constructor(entityId: Int, velocityX: Float, velocityY: Float, velocityZ: Float) : this(
        entityId,
        velocityX,
        velocityY,
        velocityZ,
        false
    )

    constructor(packet: ClientboundSetEntityMotionPacket) : this(
        packet.id,
        packet.xa.toFloat(),
        packet.ya.toFloat(),
        packet.za.toFloat(),
        false
    )

    fun getVelocity(): Vec3 = Vec3(velX.toDouble(), velY.toDouble(), velZ.toDouble())

    @get:JvmName("velocity")
    val velocity: Vec3
        get() = getVelocity()

    fun setVelocity(velocity: Vec3) {
        setVelocity(velocity.x().toFloat(), velocity.y().toFloat(), velocity.z().toFloat())
    }

    fun setVelocity(velocityX: Float, velocityY: Float, velocityZ: Float) {
        this.velocityX = velocityX
        this.velocityY = velocityY
        this.velocityZ = velocityZ
    }
}
