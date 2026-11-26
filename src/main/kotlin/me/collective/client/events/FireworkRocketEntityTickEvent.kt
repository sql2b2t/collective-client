package me.collective.client.events

class FireworkRocketEntityTickEvent(yaw: Float, pitch: Float) : CancellableEvent() {
    private var yaw: Float = yaw
    private var pitch: Float = pitch

    fun getYaw(): Float = yaw

    fun getPitch(): Float = pitch

    fun setYaw(yaw: Float) {
        this.yaw = yaw
        cancel()
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch
        cancel()
    }
}
