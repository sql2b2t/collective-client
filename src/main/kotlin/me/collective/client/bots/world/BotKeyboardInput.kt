package me.collective.client.bots.world

import net.minecraft.client.player.ClientInput
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

class BotKeyboardInput : ClientInput() {
    @Volatile
    private var currentInput: Input = Input.EMPTY

    override fun tick() {
        val input = currentInput
        this.keyPresses = input
        val x = (if (input.left()) 1.0f else 0.0f) - (if (input.right()) 1.0f else 0.0f)
        val y = (if (input.forward()) 1.0f else 0.0f) - (if (input.backward()) 1.0f else 0.0f)
        val vec = Vec2(x, y).normalized()
        this.leftImpulse = vec.x
        this.forwardImpulse = vec.y
    }

    fun apply(input: Input) {
        this.currentInput = input
    }
}
