package me.collective.client.bots.world

import me.collective.mixin.LocalPlayerAccessor
import net.minecraft.client.ClientRecipeBook
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.chat.ChatAbilities
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.syncher.SynchedEntityData.DataValue
import net.minecraft.stats.StatsCounter
import net.minecraft.world.entity.player.Input
import net.minecraft.world.level.GameType
import java.util.ArrayList

class BotLocalPlayer(
    minecraft: Minecraft,
    val botLevel: BotClientLevel,
    connection: ClientPacketListener
) : LocalPlayer(
    minecraft,
    botLevel,
    connection,
    StatsCounter(),
    ClientRecipeBook(),
    Input.EMPTY,
    false,
    ChatAbilities.NO_RESTRICTIONS
) {
    private var customGameType: GameType = GameType.SURVIVAL
    var isSynced: Boolean = false
        private set
    private val sprintStateTracker = SprintStateTracker()
    private var sprintCoolDownTicks: Int = 0

    init {
        input = BotKeyboardInput()
    }

    override fun updateAutoJump(f: Float, g: Float) {}

    override fun isControlledCamera(): Boolean = true

    override fun gameMode(): GameType = customGameType

    fun setGameType(type: GameType) {
        customGameType = type
    }

    fun ridingTick() {
        setOldPosAndRot()
        tickCount++
        rideTick()
    }

    fun physicsTick() {
        setOldPosAndRot()
        tickCount++
        tick()
    }

    fun armSprintResync() {
        sprintStateTracker.arm(this)
    }

    fun aimFrom(state: BotPlayerState) {
        yRot = state.yRot
        xRot = state.xRot
        yHeadRot = state.yRot
    }

    fun setPlayerInput(newInput: Input) {
        var applied = newInput
        sprintStateTracker.update(this)
        if (sprintCoolDownTicks > 0) {
            sprintCoolDownTicks--
            applied = Input(applied.forward(), applied.backward(), applied.left(), applied.right(), applied.jump(), applied.shift(), false)
        }
        (input as? BotKeyboardInput)?.apply(applied)
    }

    fun syncFrom(state: BotPlayerState) {
        snapTo(state.x, state.y, state.z, state.yRot, state.xRot)
        setOldPosAndRot()
        yHeadRot = state.yRot
        setOnGround(state.onGround)
        setDeltaMovement(state.deltaX, state.deltaY, state.deltaZ)
        if (!isSynced) {
            fallDistance = 0.0
        }
        isSynced = true
    }

    fun seedSentBaseline(state: BotPlayerState) {
        val accessor = this as? LocalPlayerAccessor ?: return
        accessor.`collective$setLastSprinting`(state.wasSprinting)
        if (state.hasSentBaseline) {
            accessor.setLastX(state.lastX)
            accessor.setLastY(state.lastY)
            accessor.setLastZ(state.lastZ)
            accessor.setLastYRot(state.lastYRot)
            accessor.setLastXRot(state.lastXRot)
            accessor.setLastOnGround(state.lastOnGround)
            accessor.`collective$setTicksSinceLastPositionPacketSent`(state.positionReminder)
        } else {
            accessor.setLastX(state.x)
            accessor.setLastY(state.y)
            accessor.setLastZ(state.z)
            accessor.setLastYRot(state.yRot)
            accessor.setLastXRot(state.xRot)
            accessor.setLastOnGround(state.onGround)
            accessor.`collective$setTicksSinceLastPositionPacketSent`(0)
        }
    }

    fun applyMetadata(dataValues: List<DataValue<*>>) {
        if (sprintStateTracker.checkMetadata(this, dataValues)) {
            isSprinting = false
            sprintCoolDownTicks = 1
        }

        val filtered = ArrayList<DataValue<*>>(dataValues.size)
        for (value in dataValues) {
            if (value.id != SprintStateTracker.SHARED_FLAGS_DATA_ID) {
                filtered.add(value)
            }
        }

        if (filtered.isNotEmpty()) {
            entityData.assignValues(filtered)
        }
    }
}
