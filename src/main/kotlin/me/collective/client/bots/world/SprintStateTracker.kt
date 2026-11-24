package me.collective.client.bots.world

import me.collective.mixin.LocalPlayerAccessor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.syncher.SynchedEntityData.DataValue

class SprintStateTracker {
    companion object {
        const val SHARED_FLAGS_DATA_ID: Int = 0
        const val SPRINTING_FLAG_MASK: Int = 8
    }

    private var timer: Int = 0

    fun reset() {
        timer = 0
    }

    fun update(player: LocalPlayer) {
        if (timer != 0) {
            timer = if (wasSprinting(player)) timer - 1 else 0
        }
    }

    fun arm(player: LocalPlayer) {
        if (wasSprinting(player)) {
            timer = 40
        }
    }

    private fun hasSprintFlagCleared(values: List<DataValue<*>>): Boolean {
        for (value in values) {
            if (value.id() == SHARED_FLAGS_DATA_ID && value.value() is Byte) {
                val b = value.value() as Byte
                return (b.toInt() and SPRINTING_FLAG_MASK) == 0
            }
        }
        return false
    }

    fun checkDesync(player: LocalPlayer, metadata: List<DataValue<*>>): Boolean {
        if (timer != 0 && wasSprinting(player) && hasSprintFlagCleared(metadata)) {
            timer = 0
            return true
        }
        return false
    }

    fun checkMetadata(player: LocalPlayer, metadata: List<DataValue<*>>): Boolean = checkDesync(player, metadata)

    private fun wasSprinting(player: LocalPlayer): Boolean {
        return (player as? LocalPlayerAccessor)?.lastOnGround() ?: false
    }
}
