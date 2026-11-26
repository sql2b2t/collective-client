package me.collective.client.events

import me.collective.client.utils.InputUtils
import net.minecraft.client.player.ClientInput
import net.minecraft.world.phys.Vec2

open class KeyboardInputTickEvent(val input: ClientInput) : CancellableEvent() {
    fun reset() {
        InputUtils.clearKeyPresses(input)
        input.moveVector = Vec2.ZERO
    }

    class Pre(input: ClientInput) : KeyboardInputTickEvent(input)
}
