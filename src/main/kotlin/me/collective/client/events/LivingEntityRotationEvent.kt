package me.collective.client.events

class LivingEntityRotationEvent(
    var yaw: Float,
    var pitch: Float,
    jump: Boolean,
) : CancellableEvent() {
    // Java boolean getter isJump() -> property named isJump keeps that accessor name.
    val isJump: Boolean = jump

    class LivingEntityAiStepEvent(
        val yaw: Float,
        val pitch: Float,
    ) : CancellableEvent()
}
