package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.KeyboardInputTickEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.utils.InputUtils
import net.minecraft.world.entity.player.Input

class InputOverrideManager {
    private val pendingSneak = PendingValue<Boolean>()
    private val pendingJump = PendingValue<Boolean>()
    private val pendingForward = PendingValue<Boolean>()
    private val pendingBack = PendingValue<Boolean>()
    private val pendingLeft = PendingValue<Boolean>()
    private val pendingRight = PendingValue<Boolean>()
    private val pendingSprint = PendingValue<Boolean>()
    private val pendingForwardMovement = PendingValue<Float>()
    private val pendingSidewaysMovement = PendingValue<Float>()
    private val sprintBlock = PendingFlag()

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val isSprintBlocked: Boolean
        @JvmName("isSprintBlockedProp")
        get() = sprintBlock.isPending()

    fun isSprintBlocked(): Boolean = isSprintBlocked

    val input: Input
        @JvmName("getInputProp")
        get() = getInput()

    fun getInput(): Input {
        val player = ClientAccess.collectiveMinecraft.player
            ?: throw IllegalStateException("Player is null")
        return player.input.keyPresses
    }

    fun requestSneak(value: Boolean) {
        pendingSneak.set(value)
    }

    fun requestJump(value: Boolean) {
        pendingJump.set(value)
    }

    fun requestForward(value: Boolean) {
        pendingForward.set(value)
    }

    fun requestBack(value: Boolean) {
        pendingBack.set(value)
    }

    fun requestLeft(value: Boolean) {
        pendingLeft.set(value)
    }

    fun requestRight(value: Boolean) {
        pendingRight.set(value)
    }

    fun requestSprint(value: Boolean) {
        pendingSprint.set(value)
    }

    fun requestForwardMovement(value: Float) {
        pendingForwardMovement.set(value)
    }

    fun requestSidewaysMovement(value: Float) {
        pendingSidewaysMovement.set(value)
    }

    fun blockSprint() {
        sprintBlock.markPending()
    }

    fun requestMovement(forward: Float, sideways: Float) {
        requestForwardMovement(forward)
        requestSidewaysMovement(sideways)
    }

    fun requestReset() {
        requestSneak(false)
        requestSprint(false)
        requestJump(false)
        requestForward(false)
        requestBack(false)
        requestLeft(false)
        requestRight(false)
        requestMovement(0.0f, 0.0f)
    }

    @EventHandler(priority = -100)
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        sprintBlock.reset()
    }

    @EventHandler(priority = -100)
    private fun onKeyboardInputTickEvent(event: KeyboardInputTickEvent) {
        val input = event.input
        pendingSneak.consume { InputUtils.setSneak(input, it) }
        pendingJump.consume { InputUtils.setJump(input, it) }
        pendingForward.consume { InputUtils.setForward(input, it) }
        pendingBack.consume { InputUtils.setBack(input, it) }
        pendingLeft.consume { InputUtils.setLeft(input, it) }
        pendingRight.consume { InputUtils.setRight(input, it) }
        pendingSprint.consume { InputUtils.setSprint(input, it) }
        pendingForwardMovement.consume { InputUtils.setForwardMovement(input, it) }
        pendingSidewaysMovement.consume { InputUtils.setSidewaysMovement(input, it) }
    }

    private class PendingValue<T> : PendingFlag() {
        private var value: T? = null

        fun set(value: T) {
            this.value = value
            markPending()
        }

        inline fun consume(consumer: (T) -> Unit) {
            if (isPending()) {
                value?.let(consumer)
                reset()
            }
        }
    }

    private open class PendingFlag {
        private var pending: Boolean = false

        fun isPending(): Boolean = pending

        fun markPending() {
            pending = true
        }

        fun reset() {
            pending = false
        }
    }
}
