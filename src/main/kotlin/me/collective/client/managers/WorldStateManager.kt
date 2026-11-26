package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.BlockCollisionShapeEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.SelectedSlotChangeEvent
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.options.DimensionType
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.PendingPlacement
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.BlockStateSnapshot
import me.collective.client.utils.world.WorldUtils
import me.collective.mixin.BlockBehaviourAccessor
import me.collective.mixin.ClientLevelAccessor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Predicate
import kotlin.math.min

class WorldStateManager : ClientAccess {
    private val blockEntitiesList: MutableList<BlockEntity> = Collections.synchronizedList(ArrayList())
    private val pendingPlacementsMap: MutableMap<BlockPos, PendingPlacement> = Collections.synchronizedMap(HashMap())
    private val confirmedPlacements: MutableSet<BlockPos> = Collections.synchronizedSet(HashSet())
    private val currentTickPlacements: MutableSet<BlockPos> = Collections.synchronizedSet(HashSet())
    private val rollbackBlocks: MutableMap<BlockPos, BlockStateSnapshot> = Collections.synchronizedMap(HashMap())
    private val glassUpdateWindows: MutableMap<BlockPos, GlassUpdateWindow> = ConcurrentHashMap()

    @Deprecated("")
    private var unconfirmedBreakPos: BlockPos? = null
    private val unconfirmedBreakTimer = ElapsedTimer()

    private var extraBreakPos: BlockPos? = null
    private val extraBreakTimer = ElapsedTimer()

    private var dimensionState: DimensionType = DimensionType.OVERWORLD
    private var mainHandSnapshot: ItemStack = ItemStack.EMPTY
    private var offHandSnapshot: ItemStack = ItemStack.EMPTY
    private var chunkRadiusState: Int = 0

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val dimension: DimensionType
        @JvmName("getDimensionProp")
        get() = dimensionState

    fun getDimension(): DimensionType {
        return dimensionState
    }

    val chunkRadius: Int
        @JvmName("getChunkRadiusProp")
        get() = chunkRadiusState

    fun getChunkRadius(): Int {
        return chunkRadiusState
    }

    val blockEntities: List<BlockEntity>
        @JvmName("getBlockEntitiesProp")
        get() = blockEntitiesList

    fun getBlockEntities(): List<BlockEntity> {
        return blockEntitiesList
    }

    val pendingPlacements: Map<BlockPos, PendingPlacement>
        @JvmName("getPendingPlacementsProp")
        get() = pendingPlacementsMap

    fun getPendingPlacements(): Map<BlockPos, PendingPlacement> {
        return pendingPlacementsMap
    }

    val unconfirmedBreak: BlockPos?
        @JvmName("getUnconfirmedBreakProp")
        get() = unconfirmedBreakPos

    fun getUnconfirmedBreak(): BlockPos? {
        return unconfirmedBreakPos
    }

    val extraBreak: BlockPos?
        @JvmName("getExtraBreakProp")
        get() = extraBreakPos

    fun getExtraBreak(): BlockPos? {
        return extraBreakPos
    }

    fun nextPredictionSequence(): Int {
        val level = ClientAccess.collectiveMinecraft.level ?: return 0
        val handler = (level as ClientLevelAccessor).getPendingUpdateManager()
        val prediction = handler.startPredicting()
        prediction.close()
        return prediction.currentSequence()
    }

    private fun refreshHandSnapshots() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        mainHandSnapshot = player.mainHandItem.copy()
        offHandSnapshot = player.offhandItem.copy()
    }

    fun hasActiveGlassUpdate(position: BlockPos): Boolean {
        val window = glassUpdateWindows[position]
        return window != null && window.isActive()
    }

    fun hasPendingPlacement(position: BlockPos): Boolean {
        return pendingPlacementsMap.containsKey(position)
    }

    fun isPlacementOccupied(position: BlockPos): Boolean {
        if (hasActiveGlassUpdate(position) && RotationManager.isMovementSyncEnabled()) {
            return false
        }
        if (antiCheatModule().assumeBlockBreakingSetting.value && rollbackBlocks.containsKey(position)) {
            return false
        }
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(position)
        return !state.canBeReplaced() || currentTickPlacements.contains(position)
    }

    fun destroyBlockPredicted(position: BlockPos?) {
        if (position == null) return
        saveBlockForRollback(position)
        ClientAccess.collectiveMinecraft.gameMode?.destroyBlock(position)
    }

    fun markUnconfirmedBreak(position: BlockPos?) {
        unconfirmedBreakPos = position
        unconfirmedBreakTimer.reset()
    }

    fun markExtraBreak(position: BlockPos?) {
        extraBreakPos = position
        extraBreakTimer.reset()
    }

    @EventHandler(priority = -200)
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        currentTickPlacements.clear()
        if (unconfirmedBreakTimer.hasElapsed(150L)) {
            unconfirmedBreakPos = null
        }
        if (extraBreakTimer.hasElapsed(150L)) {
            extraBreakPos = null
        }
        confirmedPlacements.removeIf { position ->
            val player = ClientAccess.collectiveMinecraft.player ?: return@removeIf false
            !position.closerThan(player.blockPosition(), 128.0)
        }
        synchronized(pendingPlacementsMap) {
            val now = System.currentTimeMillis()
            val expiredPlacements = ArrayList<BlockPos>()
            for ((pos, placement) in pendingPlacementsMap) {
                if (now > placement.createdAtMillis + 250L) {
                    expiredPlacements.add(pos)
                }
            }
            for (expiredPosition in expiredPlacements) {
                pendingPlacementsMap.remove(expiredPosition)
            }
        }
        synchronized(blockEntitiesList) {
            blockEntitiesList.clear()
            blockEntitiesList.addAll(WorldUtils.getLoadedBlockEntities().filterNotNull())
        }
        synchronized(rollbackBlocks) {
            val level = ClientAccess.collectiveMinecraft.level
            if (level != null) {
                rollbackBlocks.forEach { (_, snapshot) ->
                    if (snapshot.hasExpired()) {
                        level.setBlockAndUpdate(snapshot.position, snapshot.state)
                    }
                }
            }
            rollbackBlocks.entries.removeIf { entry -> entry.value.hasExpired() }
        }
        glassUpdateWindows.values.removeIf { it.hasExpired() }
    }

    @EventHandler
    private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
        val packet = event.packet as? ServerboundUseItemOnPacket ?: return
        if (ClientContext.rotationManager.isExecutingAction()) {
            return
        }
        val hand = packet.hand
        val stack = resolveHandStack(hand)
        val item = stack.item as? BlockItem ?: return
        val hit = packet.hitResult
        var placementPosition = hit.blockPos
        val face = hit.direction
        val adjacentPosition = placementPosition.relative(face)
        val block = item.block
        var replacesClickedBlock = true
        if (!WorldUtils.isReplaceable(placementPosition)) {
            placementPosition = adjacentPosition
            replacesClickedBlock = false
        }
        if (!WorldUtils.isReplaceable(placementPosition)) {
            if (replacesClickedBlock) {
                return
            }
            if (block != WorldUtils.getBlock(placementPosition)) {
                return
            }
        }
        pendingPlacementsMap[placementPosition] = PendingPlacement(System.currentTimeMillis(), block)
        if (!replacesClickedBlock) {
            currentTickPlacements.add(placementPosition)
        }
    }

    @EventHandler
    private fun onHandSnapshotEvent(event: SelectedSlotChangeEvent) {
        refreshHandSnapshots()
    }

    @EventHandler
    private fun onCollisionShapeEvent(event: BlockCollisionShapeEvent) {
        val pos = event.pos ?: return
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        if (pendingPlacementsMap.containsKey(pos)) {
            val placement = pendingPlacementsMap[pos]
            val placementBlock = placement?.block
            if (placementBlock != null) {
                val blockBounds = AABB(pos)
                if (!player.boundingBox.intersects(blockBounds) &&
                    (placementBlock as BlockBehaviourAccessor).isCollidable
                ) {
                    val collisionEntityFilter = Predicate<Entity> { entity ->
                        entity !is ThrownExperienceBottle &&
                            entity !is ItemEntity &&
                            entity !is ExperienceOrb &&
                            entity !is Arrow &&
                            entity !is ThrownEnderpearl
                    }
                    if (level.getEntitiesOfClass(Entity::class.java, blockBounds, collisionEntityFilter).isEmpty()) {
                        val shape = placementBlock.defaultBlockState().getShape(level, pos)
                        event.shape = shape
                    }
                }
            }
        }

        if (RotationManager.isMovementSyncEnabled() &&
            ClientContext.serverStateManager.getSetbackTimer().hasElapsed(300L) &&
            pos == unconfirmedBreakPos
        ) {
            event.shape = Shapes.empty()
        }
    }

    @EventHandler
    private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
        pendingPlacementsMap.clear()
        confirmedPlacements.clear()
        blockEntitiesList.clear()
        rollbackBlocks.clear()
    }

    @EventHandler(priority = 200)
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        currentTickPlacements.clear()
        dimensionState = WorldUtils.getDimension()
        refreshHandSnapshots()
    }

    private fun trackGlassUpdate(position: BlockPos, state: BlockState) {
        if (!BlockInteractionUtils.isGlass(state)) {
            return
        }
        val now = System.currentTimeMillis()
        val latencyDurationMillis = ClientContext.serverStateManager.getLatencyTicks() * 50 + 50
        val activeDurationMillis = min(250, latencyDurationMillis).toLong()
        val immutableWindowPosition = position.immutable()
        val activeUntilMillis = now + activeDurationMillis
        glassUpdateWindows.putIfAbsent(immutableWindowPosition, GlassUpdateWindow(activeUntilMillis, now + 10000L))
    }

    private fun resolveHandStack(hand: InteractionHand): ItemStack {
        val previous = if (hand == InteractionHand.MAIN_HAND) mainHandSnapshot else offHandSnapshot
        refreshHandSnapshots()
        val updated = if (hand == InteractionHand.MAIN_HAND) mainHandSnapshot else offHandSnapshot
        return if (updated.isEmpty) previous else updated
    }

    fun hasKnownPlacement(position: BlockPos): Boolean {
        return confirmedPlacements.contains(position) || pendingPlacementsMap.containsKey(position)
    }

    @EventHandler
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet = event.packet
        if (packet is ClientboundBlockUpdatePacket) {
            rollbackBlocks.remove(packet.pos)
            val state = packet.blockState
            if (state.isAir) {
                confirmedPlacements.remove(packet.pos)
                pendingPlacementsMap.remove(packet.pos)
                glassUpdateWindows.remove(packet.pos)
            } else {
                if (pendingPlacementsMap.remove(packet.pos) != null) {
                    confirmedPlacements.add(packet.pos)
                }
            }
            trackGlassUpdate(packet.pos, state)
        }
        if (packet is ClientboundSectionBlocksUpdatePacket) {
            synchronized(rollbackBlocks) {
                val sectionUpdates = BiConsumer<BlockPos, BlockState> { position, state ->
                    rollbackBlocks.remove(position)
                    if (state.isAir) {
                        glassUpdateWindows.remove(position.immutable())
                    } else {
                        trackGlassUpdate(position, state)
                    }
                }
                packet.runUpdates(sectionUpdates)
            }
        }
        if (packet is ClientboundLoginPacket) {
            chunkRadiusState = packet.chunkRadius()
        }
        if (packet is ClientboundSetChunkCacheRadiusPacket) {
            chunkRadiusState = packet.radius
        }
    }

    fun saveBlockForRollback(position: BlockPos) {
        if (!rollbackBlocks.containsKey(position)) {
            rollbackBlocks[position] = BlockStateSnapshot(
                BlockStateSnapshot.captureState(position),
                position,
                System.currentTimeMillis()
            )
        }
    }

    private data class GlassUpdateWindow(
        val activeUntilMillis: Long,
        val expiresAtMillis: Long,
    ) {
        fun isActive(): Boolean = System.currentTimeMillis() < activeUntilMillis
        fun hasExpired(): Boolean = System.currentTimeMillis() >= expiresAtMillis
    }

    companion object {
        private var antiCheatModule: AntiCheatModule? = null

        private fun antiCheatModule(): AntiCheatModule {
            if (antiCheatModule == null) {
                antiCheatModule = ClientContext.getModule(AntiCheatModule::class.java)
            }
            return antiCheatModule!!
        }
    }
}
