package me.collective.client.bots.control

import me.collective.client.bots.BotClient
import net.minecraft.world.entity.player.Input

class BotInputController(val bot: BotClient) {
    @Volatile var forward: Boolean = false
    @Volatile var backward: Boolean = false
    @Volatile var left: Boolean = false
    @Volatile var right: Boolean = false
    @Volatile var jump: Boolean = false
    @Volatile var sneak: Boolean = false
    @Volatile var sprint: Boolean = false
    @Volatile var movementOverride: Int = 0

    fun tick() {
        bot.worldState.botLocalPlayer?.setPlayerInput(buildInput())
    }

    fun reset() {
        setKeys(forward = false, backward = false, left = false, right = false, jump = false, sneak = false, sprint = false)
        movementOverride = 0
    }

    fun setKeys(forward: Boolean, backward: Boolean, left: Boolean, right: Boolean, jump: Boolean, sneak: Boolean, sprint: Boolean) {
        this.forward = forward
        this.backward = backward
        this.left = left
        this.right = right
        this.jump = jump
        this.sneak = sneak
        this.sprint = sprint
    }

    fun buildInput(): Input {
        val override = movementOverride
        return Input(
            forward || override > 0,
            backward || override < 0,
            left || override > 0,
            right || override < 0,
            jump,
            sneak,
            sprint
        )
    }
}
