package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.ListFilterMode
import me.collective.client.utils.LockUtils
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.mixin.BlockBehaviourAccessor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.ArrayList

open class SelfFillModule : Module("SelfFill", "Places solid blocks inside you blocking your lower hitbox.", ModuleCategory.COMBAT, "burrow") {
    protected val blocksSetting: Setting<Set<Block>> = add(
        RegistrySetSetting("Blocks", BuiltInRegistries.BLOCK, Blocks.OBSIDIAN, Blocks.ENDER_CHEST)
    )
    private val selectionSetting: Setting<ListFilterMode> = add(EnumSetting("Selection", ListFilterMode.WHITELIST))
    private val modeSetting: Setting<ModeChoice> = add(EnumSetting("Mode", ModeChoice.PLAIN))
    private val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", true))
    protected val attackSetting: Setting<Boolean> = add(BooleanSetting("Attack", true))
    protected val centerSetting: Setting<Boolean> = add(BooleanSetting("Center", false))
    private val alternativeSetting: Setting<Boolean> = add(BooleanSetting("Alternative", false))
    protected val headSetting: Setting<Boolean> = add(BooleanSetting("Head", false))
    protected val multiPlaceSetting: Setting<Boolean> = add(BooleanSetting("MultiPlace", true) { !centerSetting.value })
    private val persistentSetting: Setting<Boolean> = add(BooleanSetting("Persistent", false).markVisibilityDependency())
    private val jumpDisableSetting: Setting<Boolean> = add(BooleanSetting("JumpDisable", true).dependsOn(persistentSetting))

    private var obstructionPosition: BlockPos? = null
    private var fillCompleted = false
    private var skipCenteredTick = false

    @JvmField
    var placementBaselineY: Double = 0.0

    init {
        persistentSetting.addVisibilityPredicate { modeSetting.value != ModeChoice.NO_LAG }
        jumpDisableSetting.addVisibilityPredicate { modeSetting.value != ModeChoice.NO_LAG }
        headSetting.addVisibilityPredicate { isCobwebWhitelisted() }
    }

    override fun onEnable() {
        if (isWorldUnavailable) {
            disable()
        } else {
            if (centerSetting.value) {
                FeetPlaceModule.centerPlayer()
                skipCenteredTick = true
            }

            val player = ClientAccess.collectiveMinecraft.player
            placementBaselineY = player?.y ?: 0.0
            fillCompleted = false
        }
    }

    private fun findClearVerticalOffset(): Int {
        val player = ClientAccess.collectiveMinecraft.player ?: return 999
        val level = ClientAccess.collectiveMinecraft.level ?: return 999
        val playerPosition = player.blockPosition()

        for (offsetY in -8 until 8) {
            if (offsetY == 0 || offsetY == 1) {
                continue
            }
            val position = playerPosition.offset(0, offsetY, 0)
            if (level.getBlockState(position).getCollisionShape(level, position).isEmpty) {
                return offsetY
            }
        }
        return 999
    }

    private fun findFillBlockSlot(): InventoryUtils.SwapSlot {
        val blockFilter = { stack: ItemStack ->
            val item = stack.item
            if (item is BlockItem) {
                val selection = selectionSetting.value
                val block = item.block
                ListFilterMode.matches(selection, block, blocksSetting)
            } else {
                false
            }
        }
        return InventoryUtils.findSwapSlot(blockFilter, alternativeSetting.value)
    }

    private fun executeFill(
        positions: Iterable<BlockPos>, previousSlot: Int, fillSlot: InventoryUtils.SwapSlot, verticalOffset: Int
    ) {
        val inventoryPlayer = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        val fillStack = fillSlot.stack
        val collidableBlock = isCollidableBlock(fillStack)
        var clearAbove = true

        for (clearancePosition in positions) {
            for (clearanceY in 1 until 3) {
                val abovePosition = clearancePosition.above(clearanceY)
                val aboveState = level.getBlockState(abovePosition)
                if (!aboveState.canBeReplaced()) {
                    clearAbove = false
                    break
                }
            }
            if (!clearAbove) break
        }

        if (!collidableBlock) {
            clearAbove = true
        }

        if (clearAbove) {
            if (!fillSlot.hotbar && fillSlot.hand != InteractionHand.OFF_HAND &&
                inventoryPlayer.containerMenu !== inventoryPlayer.inventoryMenu
            ) {
                return
            }

            InventoryUtils.swapTo(fillSlot)
            try {
                var placementPosition = inventoryPlayer.position()
                if (collidableBlock) {
                    for (jumpOffset in modeSetting.value.jumpOffsets) {
                        val packetX = inventoryPlayer.x
                        val packetY = inventoryPlayer.y + jumpOffset
                        val packetZ = inventoryPlayer.z
                        placementPosition = Vec3(packetX, packetY, packetZ)

                        val packetYaw = inventoryPlayer.yRot
                        val packetPitch = MathConstants.NINETY_DEGREES.toFloat()
                        PacketSender.sendPositionRotation(packetX, packetY, packetZ, packetYaw, packetPitch, false)
                    }
                }

                val originalBounds = inventoryPlayer.boundingBox
                val width = originalBounds.xsize
                val height = originalBounds.ysize
                val depth = originalBounds.zsize
                val placementBounds = AABB(-width / 2.0, 0.0, -depth / 2.0, width / 2.0, height, depth / 2.0).move(placementPosition)
                inventoryPlayer.boundingBox = placementBounds

                try {
                    for (position in positions) {
                        val face = BlockInteractionUtils.findSupportDirection(position)
                        if (modeSetting.value == ModeChoice.NO_LAG) {
                            BlockInteractionUtils.placeBlock(position, null, face, false, InteractionHand.MAIN_HAND)
                        } else {
                            BlockInteractionUtils.placeBlockIgnoringCrystals(position, face, false, InteractionHand.MAIN_HAND)
                        }
                    }
                } finally {
                    inventoryPlayer.boundingBox = originalBounds
                }

                if (collidableBlock) {
                    applyPostFillShift(verticalOffset.toDouble())
                }
            } finally {
                InventoryUtils.swapBack(previousSlot, fillSlot)
            }
            fillCompleted = true
        }
    }

    @EventHandler
    private fun disableAfterJump(event: LocalPlayerTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.y <= placementBaselineY) {
            return
        }

        if (!persistentSetting.value || !jumpDisableSetting.value) {
            return
        }

        if (modeSetting.value != ModeChoice.NO_LAG) {
            toggle()
        }
    }

    private fun prepareObstruction(position: BlockPos): Boolean {
        if (!attackSetting.value) {
            return false
        }

        obstructionPosition = null
        val obstruction = BlockInteractionUtils.attackCrystalAt(position, 0)
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val mainHandStack = player.mainHandItem

        val state = level.getBlockState(position)
        if (!state.canBeReplaced()) {
            val breakDelta = WorldUtils.getBreakProgress(mainHandStack, state, true)
            if (breakDelta >= 1.0) {
                PacketSender.sendPlayerAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, position, Direction.UP)
                PacketSender.sendPlayerAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, position, Direction.UP)
                ClientContext.worldStateManager.destroyBlockPredicted(position)
                obstructionPosition = position
                return obstructionPosition != null
            }
        }

        if (obstruction != null) {
            obstructionPosition = obstruction.blockPosition()
        }

        return obstructionPosition != null
    }

    private fun selectFillPositions(positions: Collection<BlockPos>): Iterable<BlockPos> {
        val selectedPositions = ArrayList<BlockPos>(positions)
        val player = ClientAccess.collectiveMinecraft.player

        if (!multiPlaceSetting.value || centerSetting.value) {
            var nearestPosition: BlockPos? = null
            var nearestDistanceSquared = 999.0
            val playerPosition = player?.position() ?: Vec3.ZERO
            for (position in positions) {
                val distanceSquared = playerPosition.distanceToSqr(position.center)
                if (distanceSquared < nearestDistanceSquared) {
                    nearestPosition = position
                    nearestDistanceSquared = distanceSquared
                }
            }
            selectedPositions.clear()
            nearestPosition?.let { selectedPositions.add(it) }
        }

        if (headSetting.value && headSetting.isVisible) {
            val headPositions = ArrayList<BlockPos>()
            for (position in selectedPositions) {
                headPositions.add(position.above())
            }
            selectedPositions.addAll(headPositions)
        }
        return selectedPositions
    }

    private fun isCobwebWhitelisted(): Boolean {
        val blocks = blocksSetting.value
        return blocks.contains(Blocks.COBWEB) && selectionSetting.value == ListFilterMode.WHITELIST
    }

    private fun isCollidableBlock(stack: ItemStack): Boolean {
        val item = stack.item
        return if (item is BlockItem) {
            (item.block as BlockBehaviourAccessor).isCollidable
        } else {
            false
        }
    }

    private fun canFillPosition(position: BlockPos): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val mainHandStack = player.mainHandItem

        val collisionBounds = AABB(position)
        val intersectingPlayers = level.getEntitiesOfClass(Player::class.java, collisionBounds) { it !== player }
        if (intersectingPlayers.isNotEmpty()) {
            return false
        }

        if (!player.onGround()) {
            return false
        }

        val state = level.getBlockState(position)
        var replaceable = state.canBeReplaced()

        if (attackSetting.value) {
            val breakDelta = WorldUtils.getBreakProgress(mainHandStack, state, true)
            if (breakDelta >= 1.0) {
                replaceable = true
            }
        }

        if (!replaceable || state.liquid()) {
            return false
        }
        return BlockInteractionUtils.findSupportDirection(position) != null
    }

    private fun applyPostFillShift(verticalOffset: Double) {
        val player = ClientAccess.collectiveMinecraft.player ?: return

        when (modeSetting.value) {
            ModeChoice.NO_LAG -> {
                player.setPos(player.x, player.y + 1.0, player.z)
                player.setOnGround(true)
            }
            ModeChoice.PACKET_SHIFT -> {
                PacketSender.sendPositionRotation(
                    player.x, player.y + 1.15, player.z, player.yRot, MathConstants.NINETY_DEGREES.toFloat(), false
                )
                PacketSender.sendPositionRotation(
                    player.x, player.y, player.z, player.yRot, MathConstants.NINETY_DEGREES.toFloat(), false
                )
            }
            else -> {
                PacketSender.sendPositionRotation(
                    player.x, player.y + verticalOffset, player.z, player.yRot, MathConstants.NINETY_DEGREES.toFloat(), false
                )
            }
        }
    }

    @EventHandler
    private fun prepareFillTick(event: LocalPlayerPreTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.isPassenger) {
            return
        }

        if (skipCenteredTick) {
            skipCenteredTick = false
            return
        }

        val playerBounds = player.boundingBox
        val bounds = playerBounds.inflate(-BoundingBoxUtils.blockCornerInset)
        val feetBounds = bounds.setMaxY(bounds.minY)
        val boundsPositions = BlockPos.betweenClosedStream(feetBounds)
        val multiplePositions = boundsPositions.count() != 1L
        if (multiplePositions && MovementPhysics.isLocalPlayerMoving()) {
            return
        }

        val verticalOffset = findClearVerticalOffset()
        if (verticalOffset == 999) {
            return
        }

        val fillSlot = findFillBlockSlot()
        val previousSlot = player.inventory.selected

        if (!fillSlot.isFound) {
            val outOfBlocksMessage = Component.literal(name).append(" is out of blocks!")
            ChatUtils.printChat(outOfBlocksMessage, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.HIGH)
            disable()
            return
        }

        val positions = PlayerStateUtils.getOccupiedBlockPositions(player).toMutableSet()
        positions.removeIf { !canFillPosition(it) }

        if (positions.isEmpty()) {
            return
        }

        for (position in positions) {
            if (prepareObstruction(position)) {
                return
            }
        }

        var rotation = floatArrayOf(player.yRot, MathConstants.NINETY_DEGREES.toFloat())
        val obstruction = obstructionPosition
        if (obstruction != null && rotateSetting.value) {
            rotation = RotationManager.getRotationsToPosition(obstruction.center)
        }

        LockUtils.runWithSharedLock {
            val selectedPositions = selectFillPositions(positions)
            executeFill(selectedPositions, previousSlot, fillSlot, verticalOffset)
        }

        if (rotateSetting.value) {
            ClientContext.rotationManager.requestRotation(rotation, 101)
        }

        if (fillCompleted) {
            if (!persistentSetting.value) {
                fillCompleted = false
                disable()
            }
        }
    }

    @EventHandler
    private fun observePositionCorrection(event: PacketEvent.Receive) {
        val packet = event.packet
        if (packet is ClientboundPlayerPositionPacket) {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            val packetPosition = packet.change().position()
            val distanceSquared = player.position().distanceToSqr(packetPosition)

            if (distanceSquared > 9.0) {
                placementBaselineY = packet.change().position().y
            }
        }
    }

    enum class ModeChoice(private val displayName: String, vararg val jumpOffsets: Double) : NamedEntry {
        PLAIN("Plain", 0.4, 0.75, 1.01, 1.15),
        PACKET_SHIFT("Shift", 0.4, 0.75, 1.01, 0.99999999),
        NO_LAG("NoLagBack", 0.42, 0.75, 1.0);

        override fun getName(): String = displayName
    }
}
