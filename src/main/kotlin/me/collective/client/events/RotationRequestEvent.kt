package me.collective.client.events

import me.collective.client.ClientAccess

class RotationRequestEvent : CancellableEvent(), ClientAccess {
    val playerPitch: Float
        get() = ClientAccess.collectiveMinecraft.player?.xRot ?: 0f

    val playerYaw: Float
        get() = ClientAccess.collectiveMinecraft.player?.yRot ?: 0f
}
