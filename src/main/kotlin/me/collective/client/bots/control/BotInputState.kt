package me.collective.client.bots.control

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Input

class BotInputState {
    private var lastInput: Input? = null
    private var jumpingFromGround: Boolean = false

    private fun isAirborne(entity: LivingEntity): Boolean {
        return !entity.onGround() && !entity.isInWater && !entity.isInLava && !entity.onClimbable() && !entity.isPassenger
    }

    private fun hasDirectionalInput(input: Input): Boolean {
        return input.forward() || input.backward() || input.left() || input.right()
    }

    fun filter(entity: LivingEntity, current: Input): Input {
        if (!isAirborne(entity)) {
            val moving = hasDirectionalInput(current)
            lastInput = if (moving) current else null
            jumpingFromGround = moving && entity.onGround() && current.jump()
            return current
        }

        if (!jumpingFromGround && hasDirectionalInput(current)) {
            lastInput = current
        }

        val cached = lastInput ?: return current
        return Input(
            cached.forward(),
            cached.backward(),
            cached.left(),
            cached.right(),
            current.jump(),
            current.shift(),
            cached.sprint() || current.sprint()
        )
    }
}
