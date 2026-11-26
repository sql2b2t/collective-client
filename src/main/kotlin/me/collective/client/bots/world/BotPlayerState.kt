package me.collective.client.bots.world

import com.mojang.authlib.GameProfile
import me.collective.client.bots.control.BotInputState
import me.collective.mixin.LocalPlayerAccessor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.PacketProcessor
import net.minecraft.network.protocol.game.*
import net.minecraft.network.syncher.SynchedEntityData.DataValue
import net.minecraft.util.Mth
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.Vec3
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BotPlayerState {
    @Volatile var gameProfile: GameProfile? = null
    @Volatile var sessionId: UUID? = null
    @Volatile var entityId: Int = -1

    @Volatile var x: Double = 0.0
    @Volatile var y: Double = 0.0
    @Volatile var z: Double = 0.0
    @Volatile var yRot: Float = 0.0f
    @Volatile var xRot: Float = 0.0f
    @Volatile var onGround: Boolean = false
    @Volatile var horizontalCollision: Boolean = false

    @Volatile var deltaX: Double = 0.0
    @Volatile var deltaY: Double = 0.0
    @Volatile var deltaZ: Double = 0.0

    @Volatile var lastX: Double = 0.0
    @Volatile var lastY: Double = 0.0
    @Volatile var lastZ: Double = 0.0
    @Volatile var lastYRot: Float = 0.0f
    @Volatile var lastXRot: Float = 0.0f
    @Volatile var lastOnGround: Boolean = true
    @Volatile var lastHorizontalCollision: Boolean = false
    @Volatile var positionReminder: Int = 0

    @Volatile var hasSentBaseline: Boolean = false
    @Volatile var wasSprinting: Boolean = false
    @Volatile var isSprinting: Boolean = false
    @Volatile var gameType: GameType = GameType.SURVIVAL

    @Volatile var health: Float = DEFAULT_HEALTH
    @Volatile var foodLevel: Int = 20
    @Volatile var saturation: Float = 0.0f
    @Volatile var experienceLevel: Int = 0
    @Volatile var experienceProgress: Float = 0.0f
    @Volatile var selectedSlot: Int = 0
    @Volatile var teleportId: Int = 0

    @Volatile var positionInitialized: Boolean = false
    @Volatile var positionDirty: Boolean = true
    @Volatile var rotationDirty: Boolean = false
    @Volatile var stateDirty: Boolean = true

    val inputState: BotInputState = BotInputState()

    @Volatile var abilitiesPacket: ClientboundPlayerAbilitiesPacket? = null
    @Volatile var healthPacket: ClientboundSetHealthPacket? = null
    @Volatile var experiencePacket: ClientboundSetExperiencePacket? = null
    @Volatile var heldSlotPacket: ClientboundSetHeldSlotPacket? = null
    @Volatile var containerContentPacket: ClientboundContainerSetContentPacket? = null
    @Volatile var containerStateId: Int = 0

    val containerSlotPackets: MutableMap<Int, ClientboundContainerSetSlotPacket> = ConcurrentHashMap()
    val playerInventoryPackets: MutableMap<Int, ClientboundSetPlayerInventoryPacket> = ConcurrentHashMap()

    @Volatile var cursorItemPacket: ClientboundSetCursorItemPacket? = null
    @Volatile var entityData: List<DataValue<*>>? = null
    @Volatile var entityDataDirty: Boolean = false
    @Volatile private var lastEntityDataConsumer: Any? = null

    companion object {
        const val DEFAULT_HEALTH: Float = 20.0f
        const val MIN_PITCH: Float = -90.0f
        const val MAX_PITCH: Float = 90.0f
    }

    fun pollEntityData(consumer: Any?): List<DataValue<*>>? {
        val data = entityData ?: return null
        if (consumer != lastEntityDataConsumer || entityDataDirty) {
            lastEntityDataConsumer = consumer
            entityDataDirty = false
            return data
        }
        return null
    }

    fun reset() {
        entityId = -1
        x = 0.0
        y = 0.0
        z = 0.0
        yRot = 0.0f
        xRot = 0.0f
        onGround = false
        horizontalCollision = false
        health = DEFAULT_HEALTH
        foodLevel = 20
        saturation = 0.0f
        experienceLevel = 0
        experienceProgress = 0.0f
        selectedSlot = 0
        teleportId = 0
        positionInitialized = false
        positionDirty = true
        deltaX = 0.0
        deltaY = 0.0
        deltaZ = 0.0
        lastX = 0.0
        lastY = 0.0
        lastZ = 0.0
        lastYRot = 0.0f
        lastXRot = 0.0f
        lastOnGround = true
        lastHorizontalCollision = false
        positionReminder = 0
        hasSentBaseline = false
        wasSprinting = false
        gameType = GameType.SURVIVAL
        abilitiesPacket = null
        healthPacket = null
        experiencePacket = null
        heldSlotPacket = null
        containerContentPacket = null
        containerStateId = 0
        cursorItemPacket = null
        containerSlotPackets.clear()
        playerInventoryPackets.clear()
        entityData = null
        entityDataDirty = false
        lastEntityDataConsumer = null
    }

    fun updatePosition(posX: Double, posY: Double, posZ: Double, dX: Double, dY: Double, dZ: Double, ground: Boolean, horizCollision: Boolean) {
        x = posX
        y = posY
        z = posZ
        deltaX = dX
        deltaY = dY
        deltaZ = dZ
        onGround = ground
        horizontalCollision = horizCollision
    }

    fun setDelta(dX: Double, dY: Double, dZ: Double) {
        deltaX = dX
        deltaY = dY
        deltaZ = dZ
        positionDirty = true
    }

    fun setRotation(yaw: Float, pitch: Float) {
        yRot = yaw
        xRot = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        rotationDirty = true
    }

    fun adjustRotation(yawDelta: Float, relativeYaw: Boolean, pitchDelta: Float, relativePitch: Boolean) {
        yRot = if (relativeYaw) yRot + yawDelta else yawDelta
        val targetPitch = if (relativePitch) xRot + pitchDelta else pitchDelta
        xRot = targetPitch.coerceIn(MIN_PITCH, MAX_PITCH)
        rotationDirty = true
    }

    fun applyPositionMoveRotation(posMoveRot: PositionMoveRotation, tId: Int = teleportId) {
        teleportId = tId
        positionDirty = true
        x = posMoveRot.position().x
        y = posMoveRot.position().y
        z = posMoveRot.position().z
        yRot = posMoveRot.yRot()
        xRot = posMoveRot.xRot()
        deltaX = posMoveRot.deltaMovement().x
        deltaY = posMoveRot.deltaMovement().y
        deltaZ = posMoveRot.deltaMovement().z
        positionInitialized = true
    }

    fun toPositionMoveRotation(): PositionMoveRotation {
        return PositionMoveRotation(
            Vec3(x, y, z),
            Vec3(deltaX, deltaY, deltaZ),
            yRot,
            xRot
        )
    }

    fun createPositionPacket(): ClientboundPlayerPositionPacket {
        return ClientboundPlayerPositionPacket(teleportId, toPositionMoveRotation(), emptySet())
    }

    fun checkAndClearPositionDirty(): Boolean {
        val dirty = positionDirty
        positionDirty = false
        return dirty
    }

    fun checkAndClearRotationDirty(): Boolean {
        val dirty = rotationDirty
        rotationDirty = false
        return dirty
    }

    fun checkAndClearStateDirty(): Boolean {
        val dirty = stateDirty
        stateDirty = false
        return dirty
    }

    fun markStateDirty() {
        stateDirty = true
    }

    fun updateHealth(packet: ClientboundSetHealthPacket) {
        healthPacket = packet
        health = packet.health
        foodLevel = packet.food
        saturation = packet.saturation
        stateDirty = true
    }

    fun updateExperience(packet: ClientboundSetExperiencePacket) {
        experiencePacket = packet
        experienceLevel = packet.experienceLevel
        experienceProgress = packet.experienceProgress
    }

    fun updateHeldSlot(packet: ClientboundSetHeldSlotPacket) {
        heldSlotPacket = packet
        selectedSlot = packet.slot
        stateDirty = true
    }

    fun updateAbilities(packet: ClientboundPlayerAbilitiesPacket) {
        abilitiesPacket = packet
        stateDirty = true
    }

    fun updateContainerContent(packet: ClientboundContainerSetContentPacket) {
        if (packet.containerId == 0) {
            containerContentPacket = packet
            containerStateId = packet.stateId
            stateDirty = true
            containerSlotPackets.clear()
            playerInventoryPackets.clear()
        }
    }

    fun updateContainerSlot(packet: ClientboundContainerSetSlotPacket) {
        if (packet.containerId == 0) {
            containerSlotPackets[packet.slot] = packet
            containerStateId = packet.stateId
            stateDirty = true
        }
    }

    fun updatePlayerInventory(packet: ClientboundSetPlayerInventoryPacket) {
        playerInventoryPackets[packet.slot] = packet
        stateDirty = true
    }

    fun updateEntityData(data: List<DataValue<*>>) {
        entityData = data
        entityDataDirty = true
    }

    fun syncFromLocalPlayer(player: LocalPlayer, type: GameType) {
        positionInitialized = true
        positionDirty = true
        entityId = player.id
        x = player.x
        y = player.y
        z = player.z
        yRot = player.yRot
        xRot = player.xRot
        onGround = player.onGround()
        horizontalCollision = player.horizontalCollision
        deltaX = player.deltaMovement.x
        deltaY = player.deltaMovement.y
        deltaZ = player.deltaMovement.z
        teleportId = 0

        val accessor = player as? LocalPlayerAccessor
        if (accessor != null) {
            lastOnGround = accessor.lastOnGround()
            lastYRot = accessor.lastYaw()
            positionReminder = accessor.`collective$getTicksSinceLastPositionPacketSent`()
        }

        hasSentBaseline = true
        gameType = type
        health = player.health
        foodLevel = player.foodData.foodLevel
        saturation = player.foodData.saturationLevel
        experienceLevel = player.experienceLevel
        experienceProgress = player.experienceProgress
        selectedSlot = player.inventory.selectedSlot

        healthPacket = ClientboundSetHealthPacket(health, foodLevel, saturation)
        experiencePacket = ClientboundSetExperiencePacket(experienceProgress, player.totalExperience, experienceLevel)
        heldSlotPacket = ClientboundSetHeldSlotPacket(selectedSlot)
        abilitiesPacket = ClientboundPlayerAbilitiesPacket(player.abilities)
        containerStateId = player.inventoryMenu.stateId
        containerContentPacket = ClientboundContainerSetContentPacket(
            0, player.inventoryMenu.stateId, player.inventoryMenu.items, player.inventoryMenu.carried
        )
        cursorItemPacket = ClientboundSetCursorItemPacket(player.inventoryMenu.carried.copy())
        stateDirty = true

        val nonDefaultData = player.entityData.nonDefaultValues
        if (nonDefaultData != null && nonDefaultData.isNotEmpty()) {
            updateEntityData(nonDefaultData)
        }

        containerSlotPackets.clear()
        playerInventoryPackets.clear()
    }

    fun replayPackets(processor: PacketProcessor, listener: ClientGamePacketListener) {
        abilitiesPacket?.let { BotWorldState.dispatchPacket(processor, listener, it) }
        healthPacket?.let { BotWorldState.dispatchPacket(processor, listener, it) }
        experiencePacket?.let { BotWorldState.dispatchPacket(processor, listener, it) }
        heldSlotPacket?.let { BotWorldState.dispatchPacket(processor, listener, it) }
        containerContentPacket?.let { BotWorldState.dispatchPacket(processor, listener, it) }

        for (slotPacket in containerSlotPackets.values) {
            BotWorldState.dispatchPacket(processor, listener, slotPacket)
        }
        for (invPacket in playerInventoryPackets.values) {
            BotWorldState.dispatchPacket(processor, listener, invPacket)
        }

        cursorItemPacket?.let { BotWorldState.dispatchPacket(processor, listener, it) }

        val data = entityData
        if (data != null && data.isNotEmpty() && entityId >= 0) {
            BotWorldState.dispatchPacket(processor, listener, ClientboundSetEntityDataPacket(entityId, data))
        }
    }
}
