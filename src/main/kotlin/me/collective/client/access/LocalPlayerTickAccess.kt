package me.collective.client.access

interface LocalPlayerTickAccess {
    fun sendMovementPacketsWrapper()

    fun superTick()

    fun resetEvent()

    fun `collective$updateServer`(yaw: Float, pitch: Float)

    fun resetRotations()
}
