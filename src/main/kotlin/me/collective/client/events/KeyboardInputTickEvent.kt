package me.collective.client.events

import me.collective.client.utils.InputUtils
import net.minecraft.client.player.ClientInput

open class KeyboardInputTickEvent(val input: ClientInput) : CancellableEvent() {
    fun reset() {
        InputUtils.clearKeyPresses(input)
        input.leftImpulse = 0f
        input.forwardImpulse = 0f
    }

    class Pre(input: ClientInput) : KeyboardInputTickEvent(input)
}
