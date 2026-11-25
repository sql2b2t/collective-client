package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.access.LocalPlayerTickAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.FireworkRocketEntityTickEvent
import me.collective.client.events.LivingEntityRotationEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.SneakStateChangeEvent
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.render.RenderUtils
import me.collective.client.settings.options.AntiCheatRotationsChoice
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.RotationRequest
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.movement.MovementInputProcessor
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Position
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque
import java.util.Queue
import kotlin.math.atan2
import kotlin.math.sqrt

class RotationManager : ClientAccess {
    private var yawValue: Float = 0.0f
    private var pitchValue: Float = 0.0f
    private var bodyYawValue: Float = 0.0f
    private var previousPitchValue: Float = 0.0f
    private var previousBodyYawValue: Float = 0.0f
    private var previousHeadYawValue: Float = 0.0f
    private var headYawValue: Float = 0.0f
    private var lastRotations: FloatArray? = null
    private var lastRotationTick: Int = 0
    private var rotationHoldTicks: Int = 0
    private val movementInput = MovementInputProcessor()
    private val deferredActions: Queue<Runnable> = ArrayDeque()
    private var collectingActions: Boolean = false
    private var executingActionValue: Boolean = false
    private var requestValue: RotationRequest? = null
    private var forcedOverride: Boolean = false
    private var movementYaw: Float = 0.0f
    private var requestedYawForMovement: Boolean = false

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val headYaw: Float
        @JvmName("getHeadYawProp")
        get() = headYawValue

    fun getHeadYaw(): Float = headYawValue

    val previousHeadYaw: Float
        @JvmName("getPreviousHeadYawProp")
        get() = previousHeadYawValue

    fun getPreviousHeadYaw(): Float = previousHeadYawValue

    val bodyYaw: Float
        @JvmName("getBodyYawProp")
        get() = bodyYawValue

    fun getBodyYaw(): Float = bodyYawValue

    val previousPitch: Float
        @JvmName("getPreviousPitchProp")
        get() = previousPitchValue

    fun getPreviousPitch(): Float = previousPitchValue

    val isExecutingAction: Boolean
        @JvmName("isExecutingActionProp")
        get() = executingActionValue

    fun isExecutingAction(): Boolean = executingActionValue

    val previousBodyYaw: Float
        @JvmName("getPreviousBodyYawProp")
        get() = previousBodyYawValue

    fun getPreviousBodyYaw(): Float = previousBodyYawValue

    val isRequestedYawUsedForMovement: Boolean
        @JvmName("isRequestedYawUsedForMovementProp")
        get() = requestedYawForMovement

    fun isRequestedYawUsedForMovement(): Boolean = requestedYawForMovement

    val pitch: Float
        @JvmName("getPitchProp")
        get() = pitchValue

    fun getPitch(): Float = pitchValue

    val yaw: Float
        @JvmName("getYawProp")
        get() = yawValue

    fun getYaw(): Float = yawValue

    val request: RotationRequest?
        @JvmName("getRequestProp")
        get() = requestValue

    fun getRequest(): RotationRequest? = requestValue

    private fun shouldOverrideRotation(): Boolean {
        return requestValue != null && (isMovementSyncEnabled() || forcedOverride)
    }

    fun forceRotationOverride() {
        forcedOverride = true
    }

    private fun runDeferredActions() {
        var index = 0
        while (deferredActions.isNotEmpty() && index < 500) {
            val action = deferredActions.poll()
            if (action != null) {
                executeAction(action)
            }
            index++
        }
        deferredActions.clear()
    }

    private fun collectRotationRequests() {
        collectingActions = true
        try {
            ClientAccess.eventBus.post(RotationRequestEvent())
        } finally {
            collectingActions = false
        }
        if (requestValue == null) {
            runDeferredActions()
        }
    }

    fun useRequestedYawForPriority(priority: Int) {
        val currentRequest = requestValue
        if (currentRequest != null && currentRequest.priority != priority) {
            return
        }
        requestedYawForMovement = true
    }

    fun executeAction(action: Runnable) {
        val alreadyExecuting = executingActionValue
        executingActionValue = true
        try {
            action.run()
        } finally {
            executingActionValue = alreadyExecuting
        }
    }

    private fun calculateBodyYaw(yaw: Float, previousBodyYaw: Float): Float {
        val player = ClientAccess.collectiveMinecraft.player ?: return yaw
        if (player.isPassenger) {
            return yaw
        }

        var bodyYaw = previousBodyYaw
        val deltaX = player.x - player.xo
        val deltaZ = player.z - player.zo
        val distanceSquared = deltaX * deltaX + deltaZ * deltaZ
        if (distanceSquared > 0.002500000176951289) {
            val movementHeading = Mth.atan2(deltaZ, deltaX).toFloat() * 57.295776f - MathConstants.NINETY_DEGREES
            val headingDifference = Mth.wrapDegrees(yaw) - movementHeading
            val absoluteDifference = Mth.abs(headingDifference)

            bodyYaw = movementHeading
            if (absoluteDifference > 95.0f && absoluteDifference < 265.0f) {
                bodyYaw = movementHeading - 180.0f
            }
        }

        if (player.attackAnim > 0.0f) {
            bodyYaw = yaw
        }

        val wrappedBodyDelta = Mth.wrapDegrees(bodyYaw - previousBodyYaw)
        bodyYaw = previousBodyYaw + wrappedBodyDelta * 0.3f

        var wrappedHeadDelta = Mth.wrapDegrees(yaw - bodyYaw)
        if (wrappedHeadDelta < -75.0f) {
            wrappedHeadDelta = -75.0f
        } else if (wrappedHeadDelta >= 75.0f) {
            wrappedHeadDelta = 75.0f
        }

        bodyYaw = yaw - wrappedHeadDelta
        if (wrappedHeadDelta * wrappedHeadDelta > 2500.0f) {
            bodyYaw += wrappedHeadDelta * 0.2f
        }

        return bodyYaw
    }

    fun updateRenderRotations(angles: FloatArray) {
        setRenderRotations(angles, 0)
    }

    @EventHandler
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        if (isMovementSyncEnabled()) {
            runDeferredActions()
            collectRotationRequests()
        }
    }

    @EventHandler
    private fun onSneakStateChangeEvent(event: SneakStateChangeEvent) {
        if (!collectingActions || event.isCancelled || executingActionValue) {
            return
        }
        val sneaking = event.isSneaking
        deferredActions.add(Runnable {
            ClientContext.playerStateTracker.setSneaking(sneaking)
        })
        event.cancel()
    }

    fun requestRotation(incomingRequest: RotationRequest) {
        val current = requestValue
        if (current != null && current.priority > incomingRequest.priority) {
            return
        }
        requestValue = incomingRequest
        requestedYawForMovement = false
    }

    fun requestRotation(yaw: Float?, pitch: Float?, priority: Int, force: Boolean) {
        val currentRequest = getRequest()
        val player = ClientAccess.collectiveMinecraft.player
        val fallbackYaw = currentRequest?.angles?.get(0) ?: player?.yRot ?: 0.0f
        val fallbackPitch = currentRequest?.angles?.get(1) ?: player?.xRot ?: 0.0f
        val yawValue = yaw ?: fallbackYaw
        val pitchValue = pitch ?: fallbackPitch
        requestRotation(floatArrayOf(yawValue, pitchValue), priority, force)
    }

    @JvmOverloads
    fun requestRotation(angles: FloatArray, priority: Int, force: Boolean = false) {
        if (antiCheat()?.is2b2tEnabled() == true &&
            ClientAccess.collectiveMinecraft.screen is AbstractContainerScreen<*> &&
            !force
        ) {
            return
        }
        if (!isSilentRotationMode() || force) {
            requestRotation(RotationRequest(angles, priority))
            return
        }
        if (!collectingActions) {
            return
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return
        updateRenderRotations(angles)
        val packetAngles = unwrapYaw(angles)
        val onGround = ClientContext.playerStateTracker.isPacketOnGround()
        PacketSender.sendPositionRotation(
            player.x,
            player.y,
            player.z,
            packetAngles[0],
            packetAngles[1],
            onGround
        )
    }

    @EventHandler
    private fun onLivingEntityAiStepEvent(event: LivingEntityRotationEvent.LivingEntityAiStepEvent) {
        val currentRequest = requestValue ?: return
        if (isWorldUnavailable() || forcedOverride || !isMovementSyncEnabled()) {
            return
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return

        val angles = currentRequest.angles
        var correctedMovementYaw = MovementPhysics.getMovementYaw(movementYaw)
        if (isRequestedYawUsedForMovement()) {
            correctedMovementYaw = angles[0]
        }

        val input = player.input
        val direction = movementInput.getRelativeDirection(correctedMovementYaw, angles[0])
        val previousLeftImpulse = input.leftImpulse
        val previousForwardImpulse = input.forwardImpulse

        if (MovementPhysics.isLocalPlayerMoving()) {
            val directionVector = Vec2(direction.sidewaysInput, direction.forwardInput)
            val normalized = movementInput.normalizeInput(directionVector)
            input.leftImpulse = normalized.x
            input.forwardImpulse = normalized.y
            input.keyPresses = direction.createInput(input.keyPresses)
        }

        movementInput.applyInputPreservingBobbing()
        input.leftImpulse = previousLeftImpulse
        input.forwardImpulse = previousForwardImpulse
        if (player.zza <= 0.0f) {
            player.isSprinting = false
        }
    }

    @EventHandler
    private fun onRotationYawEvent(event: LivingEntityRotationEvent) {
        val currentRequest = requestValue ?: return
        if (shouldOverrideRotation()) {
            movementYaw = Mth.wrapDegrees(event.yaw)
            event.cancel()
            event.yaw = currentRequest.angles[0]
            event.pitch = currentRequest.angles[1]
        }
    }

    @EventHandler
    fun onFireworkRocketEntityTickEvent(event: FireworkRocketEntityTickEvent) {
        val currentRequest = requestValue ?: return
        if (shouldOverrideRotation()) {
            val angles = currentRequest.angles
            if (event.getYaw() == angles[0] && event.getPitch() == angles[1]) {
                return
            }
            event.setYaw(angles[0])
            event.setPitch(angles[1])
        }
    }

    @EventHandler
    private fun onMultiPlayerGameModeHandleInventoryMouseClickEvent(
        event: MultiPlayerGameModeHandleInventoryMouseClickEvent
    ) {
        if (!collectingActions || event.isCancelled || executingActionValue) {
            return
        }
        deferredActions.add(Runnable { replayInventoryClick(event) })
        event.cancel()
    }

    @EventHandler
    private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
        if (!collectingActions || event.isCancelled || executingActionValue) {
            return
        }
        val packet = event.packet
        if (packet is ServerboundMovePlayerPacket) {
            event.cancel()
            PacketSender.sendDirect(packet)
            if (packet.hasRotation()) {
                val packetYaw = packet.getYRot(0.0f)
                val packetPitch = packet.getXRot(0.0f)
                (ClientAccess.collectiveMinecraft.player as? LocalPlayerTickAccess)
                    ?.`collective$updateServer`(packetYaw, packetPitch)
            }
            runDeferredActions()
            return
        }

        deferredActions.add(Runnable { PacketSender.send(packet) })
        event.cancel()
    }

    @EventHandler(priority = 200)
    private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
        forcedOverride = false
        requestedYawForMovement = false
        requestValue = null
    }

    fun setRenderRotations(angles: FloatArray, holdTicks: Int) {
        lastRotationTick = ClientAccess.collectiveMinecraft.player?.tickCount ?: 0
        previousPitchValue = pitchValue
        previousBodyYawValue = bodyYawValue
        bodyYawValue = calculateBodyYaw(angles[0], previousBodyYawValue)
        previousHeadYawValue = headYawValue
        headYawValue = angles[0]
        yawValue = angles[0]
        pitchValue = angles[1]
        lastRotations = angles
        if (holdTicks > 0) {
            rotationHoldTicks = holdTicks
        }
    }

    fun updateRenderRotationsIfNeeded(yaw: Float, pitch: Float) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.tickCount != lastRotationTick) {
            updateRenderRotations(floatArrayOf(yaw, pitch))
        }
    }

    @EventHandler
    private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
        val cachedRotations = lastRotations
        if (rotationHoldTicks > 0 && cachedRotations != null) {
            updateRenderRotations(cachedRotations)
            rotationHoldTicks--
        }

        if (event.stage == EventStage.Post) {
            runDeferredActions()
        } else {
            if (!isMovementSyncEnabled()) {
                collectRotationRequests()
            }
            val currentRequest = requestValue
            if (currentRequest != null) {
                event.setRotation(unwrapYaw(currentRequest.angles))
            }
        }
    }

    companion object {
        private var antiCheat: AntiCheatModule? = null
        private var frustum: Frustum? = null

        @JvmField
        var renderingInventory: Boolean = false

        @JvmStatic
        fun is2b2tMode(): Boolean {
            return antiCheat()?.is2b2tEnabled() ?: false
        }

        @JvmStatic
        fun isMovementSyncEnabled(): Boolean {
            val module = antiCheat() ?: return false
            return module.movementSyncSetting.value
        }

        private fun antiCheat(): AntiCheatModule? {
            if (antiCheat == null) {
                antiCheat = ClientContext.getModule(AntiCheatModule::class.java)
            }
            return antiCheat
        }

        @JvmStatic
        fun isVisible(bounds: AABB): Boolean {
            val currentFrustum = frustum ?: return true
            return currentFrustum.isVisible(bounds)
        }

        @JvmStatic
        fun isSilentRotationMode(): Boolean {
            val module = antiCheat() ?: return false
            return isMovementSyncEnabled() && module.rotationsSetting.value == AntiCheatRotationsChoice.SILENT
        }

        @JvmStatic
        fun getYawSector(yaw: Float): Int {
            val scaledYaw = yaw * 8.0f / MathConstants.FULL_TURN_DEGREES + MathConstants.ONE_HALF
            return Mth.floor(scaledYaw) and 7
        }

        @JvmStatic
        fun getRotationsToBlock(position: BlockPos): FloatArray {
            val center = Vec3(
                blockCenterX(position),
                position.y + MathConstants.ONE_HALF,
                position.z + MathConstants.ONE_HALF
            )
            return getRotationsToPosition(center)
        }

        private fun blockCenterX(position: BlockPos): Double {
            return position.x + MathConstants.ONE_HALF
        }

        @JvmStatic
        fun calculateRotations(from: Vec3, to: Vec3): FloatArray {
            val deltaX = to.x - from.x
            val verticalDelta = (to.y - from.y) * -1.0
            val deltaZ = to.z - from.z
            val horizontalDistance = sqrt(deltaX * deltaX + deltaZ * deltaZ)

            val yawDegrees = Math.toDegrees(atan2(deltaZ, deltaX)) - MathConstants.NINETY_DEGREES
            val pitchDegrees = Math.toDegrees(atan2(verticalDelta, horizontalDistance))
            return floatArrayOf(
                Mth.wrapDegrees(yawDegrees).toFloat(),
                Mth.clamp(pitchDegrees.toFloat(), -90.0f, 90.0f)
            )
        }

        @JvmStatic
        fun getRotationsToEntity(entity: Entity?): FloatArray {
            if (entity == null) {
                return floatArrayOf(0.0f, 0.0f)
            }
            val player = ClientAccess.collectiveMinecraft.player ?: return floatArrayOf(0.0f, 0.0f)
            val targetPosition = BoundingBoxUtils.clampToBounds(player.eyePosition, entity.boundingBox)
            return getRotationsToPosition(targetPosition)
        }

        @JvmStatic
        fun setFrustum(sourceFrustum: Frustum) {
            frustum = Frustum(sourceFrustum)
        }

        @JvmStatic
        fun getRotationsToFace(position: Vec3, face: Direction?): FloatArray {
            val player = ClientAccess.collectiveMinecraft.player ?: return floatArrayOf(0.0f, 0.0f)
            val eyePosition = player.getEyePosition(RenderUtils.getPartialTick())
            val targetPosition = if (face == null) {
                position
            } else {
                position.relative(face, MathConstants.ONE_HALF)
            }
            return calculateRotations(eyePosition, targetPosition)
        }

        private fun replayInventoryClick(event: MultiPlayerGameModeHandleInventoryMouseClickEvent) {
            val gameMode = ClientAccess.collectiveMinecraft.gameMode ?: return
            val player = ClientAccess.collectiveMinecraft.player ?: return
            gameMode.handleInventoryMouseClick(
                event.containerId,
                event.slotId,
                event.button,
                event.clickType,
                player
            )
        }

        @JvmStatic
        fun addYawJitter(angles: FloatArray): FloatArray {
            val tickCount = ClientAccess.collectiveMinecraft.player?.tickCount ?: 0
            val offset = AntiCheatModule.ROTATION_JITTER_STEP * (tickCount % 6)
            return floatArrayOf(Mth.wrapDegrees(angles[0] + offset), angles[1])
        }

        @JvmStatic
        fun isPositionVisible(position: Vec3): Boolean {
            val bounds = AABB(BlockPos.containing(position as Position))
            return isVisible(bounds)
        }

        @JvmStatic
        fun getRotationsToPosition(position: Vec3): FloatArray {
            val player = ClientAccess.collectiveMinecraft.player ?: return floatArrayOf(0.0f, 0.0f)
            val partialTick = RenderUtils.getPartialTick()
            return calculateRotations(player.getEyePosition(partialTick), position)
        }

        @JvmStatic
        fun unwrapYaw(angles: FloatArray): FloatArray {
            val packetYaw = ClientContext.playerStateTracker.packetRotations[0]
            val wrappedYawDifference = Mth.wrapDegrees(angles[0] - packetYaw)
            return floatArrayOf(packetYaw + wrappedYawDifference, angles[1])
        }

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            antiCheat = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule?
        }
    }
}
