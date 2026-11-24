package me.collective.client.events

import net.minecraft.network.protocol.game.ClientboundExplodePacket

class ClientPacketListenerHandleExplosionEvent private constructor(
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val radius: Float,
    var velocityX: Float,
    var velocityY: Float,
    var velocityZ: Float,
    // Trailing marker keeps the primary's JVM signature distinct from the public
    // (Double, Double, Double, Float, Float, Float, Float) secondary constructor.
    @Suppress("UNUSED_PARAMETER") marker: Boolean
) : CancellableEvent() {
    constructor(packet: ClientboundExplodePacket) : this(
        packet.center().x,
        packet.center().y,
        packet.center().z,
        6.0f,
        packet.playerKnockback().map { it.x.toFloat() }.orElse(0f),
        packet.playerKnockback().map { it.y.toFloat() }.orElse(0f),
        packet.playerKnockback().map { it.z.toFloat() }.orElse(0f),
        false
    )

    constructor(
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        radius: Float,
        velocityX: Float,
        velocityY: Float,
        velocityZ: Float
    ) : this(centerX, centerY, centerZ, radius, velocityX, velocityY, velocityZ, false)
}
