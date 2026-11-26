package me.collective.client.modules.combat

import net.minecraft.world.entity.EntityTypes
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.BlockUpdateAppliedEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockerTargetOffsets
import me.collective.client.utils.combat.BlockRaycastClassifier
import me.collective.client.utils.combat.BlockRaycastOverride
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.utils.world.BlockInteractionUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import kotlin.math.ceil
import kotlin.math.floor

class FeetPlaceModule : PlacementModule("FeetPlace", "Surrounds your feet with obby.", ModuleCategory.COMBAT, "surround") {
    private val fireworkSetting: Setting<Boolean>
    private val sequentialSetting: Setting<SequentialMode>
    private val centerSetting: Setting<Boolean>
    private val flattenSetting: Setting<Boolean>
    private val coverSetting: Setting<Boolean>
    private val smartFaceSetting: Setting<Boolean>
    private val blockIgnoreSetting: Setting<BlockIgnoreMode>
    private val onlyObbySetting: Setting<Boolean>
    private val instantSetting: Setting<Boolean>
    private val fireworkSupport: FeetPlaceFireworkSupport
    private var recentPositionCorrection: Boolean = false
    private var sequentialPlacementComplete: Boolean = false
    private var nearbyCrystalCount: Int = 0
    private var centered: Boolean = false

    init {
        this.fireworkSetting = this.add(BooleanSetting("Firework", false))
        this.sequentialSetting = this.add(EnumSetting("Sequential", SequentialMode.NONE))
        this.centerSetting = this.add(BooleanSetting("Center", true))
        this.flattenSetting = this.add(BooleanSetting("Flatten", true))
        this.coverSetting = this.add(BooleanSetting("Cover", false))
        this.smartFaceSetting = this.add(BooleanSetting("SmartFace", false))
        this.blockIgnoreSetting = this.add(EnumSetting("BlockIgnore", BlockIgnoreMode.NONE))
        this.onlyObbySetting = this.add(BooleanSetting("OnlyObby", false))
        this.instantSetting = this.add(BooleanSetting("Instant", false))
        this.fireworkSupport = FeetPlaceFireworkSupport()

        this.setDisableOnRespawn(true)
    }

    override fun getPlacementOrigin(): BlockPos {
        val player = ClientAccess.collectiveMinecraft.player
        return if (player != null && !EntityUtils.hasCollisionBelow(player)) {
            BlockPos.containing(player.x, player.y, player.z)
        } else {
            super.getPlacementOrigin()
        }
    }

    override fun disableForJumpOrSleep(): Boolean {
        return if (this.recentPositionCorrection) {
            false
        } else {
            super.disableForJumpOrSleep()
        }
    }

    override fun shouldDiscardAttackedCrystal(): Boolean {
        if (!this.instantSetting.value) {
            if (!RotationManager.isMovementSyncEnabled()) {
                return false
            }
        }
        return true
    }

    override fun getPlacementRotationPriority(): Int {
        return 999
    }

    override fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
        return if (this.onlyObbySetting.value) {
            InventoryUtils.findSwapSlot(Items.OBSIDIAN, false)
        } else {
            super.findPlacementSwapSlot()
        }
    }

    override fun onEnable() {
        this.nearbyCrystalCount = 0
        if (!this.isWorldUnavailable) {
            this.centered = false
            this.countNearbyCrystals()
        }

        super.onEnable()
        this.fireworkSupport.onEnable()
    }

    private fun countNearbyCrystals() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val occupiedPositions = PlayerStateUtils.getSurroundPositions(player)

        for (entity in level.entitiesForRendering()) {
            if (entity is EndCrystal) {
                val crystalBase = entity.blockPosition().below()
                if (occupiedPositions.contains(crystalBase)) {
                    this.nearbyCrystalCount++
                }
            }
        }
    }

    private fun isIgnoredMiningPosition(position: BlockPos): Boolean {
        if (!speedMine.isToggled) {
            return false
        }
        val miningPosition = speedMine.rebreakPos
        return miningPosition != null && miningPosition == position
    }

    private fun isIgnoredBlockPosition(position: BlockPos, enemyPositions: Set<BlockPos>): Boolean {
        return when (this.blockIgnoreSetting.value) {
            BlockIgnoreMode.NONE -> false
            BlockIgnoreMode.MINING -> this.isIgnoredMiningPosition(position)
            BlockIgnoreMode.ENEMY -> enemyPositions.contains(position)
            BlockIgnoreMode.BOTH -> this.isIgnoredMiningPosition(position) || enemyPositions.contains(position)
        }
    }

    private fun collectEnemyPositions(): Set<BlockPos> {
        val positions = HashSet<BlockPos>()
        val level = ClientAccess.collectiveMinecraft.level ?: return positions
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return positions

        for (player in level.players()) {
            if (!player.isAlive || player == localPlayer || player.distanceTo(localPlayer) > 4.0f
                || ClientContext.socialRegistry.isFriendPlayer(player)) {
                continue
            }
            for (occupied in PlayerStateUtils.getOccupiedBlockPositions(player)) {
                val state = level.getBlockState(occupied)
                if (state.isAir || BlockRaycastClassifier.IGNORE_LOW_RESISTANCE.test(occupied, state) == BlockRaycastOverride.IGNORE) {
                    for (offset in BlockerTargetOffsets.SURROUND.offsets) {
                        positions.add(occupied.offset(offset))
                    }
                }
            }
        }
        return positions
    }

    private fun getPlayerHeightInBlocks(): Int {
        val player = ClientAccess.collectiveMinecraft.player ?: return 2
        val height = player.bbHeight.toDouble()
        return ceil(height).toInt()
    }

    private fun shouldSkipFlattenPosition(position: BlockPos): Boolean {
        val placementPosition = ClientContext.worldStateManager.unconfirmedBreak
        val player = ClientAccess.collectiveMinecraft.player

        if (ClientContext.holeManager.intersectsHole(position)) {
            if (player != null && !player.onGround()) {
                return true
            }
        }

        if (PlayerStateUtils.isLocalLowProfile()) {
            if (!antiCheat.is2b2tEnabled) {
                return true
            }
        }

        if (position != placementPosition) {
            val miningAction = speedMine.extraBreakTarget
            if (miningAction != null) {
                if (position == miningAction.position) {
                    return true
                }
            }
            return false
        } else {
            return true
        }
    }

    private fun getPlacementComparator(costs: Map<BlockPos, Float>): Comparator<BlockPos> {
        val directionComparator: Comparator<BlockPos> = Comparator.comparing { position: BlockPos ->
            costs[position] ?: Float.MAX_VALUE
        }

        val pendingPlacementComparator: Comparator<BlockPos> = Comparator.comparing { candidate: BlockPos ->
            ClientContext.worldStateManager.hasPendingPlacement(candidate)
        }
        return pendingPlacementComparator.thenComparing(directionComparator)
    }

    private fun computePlacementCosts(positions: List<BlockPos>): HashMap<BlockPos, Float> {
        val costs = HashMap<BlockPos, Float>(positions.size * 2)
        val latency = ClientContext.movementSpeedTracker.currentSpeed
        val player = ClientAccess.collectiveMinecraft.player
        val distanceMode = latency <= 10.0

        if (distanceMode) {
            val playerYaw = player?.yRot ?: 0.0f
            val playerBlockPosition = PlayerStateUtils.getLocalRoundedBlockPosition()
            // Тяжёлая проверка коллизий — один раз на сортировку, а не на каждое сравнение.
            val nontrivialCollision = player != null && this.hasNontrivialSurroundCollision(player)
            for (position in positions) {
                costs[position] = if (nontrivialCollision && position.y == playerBlockPosition.y) {
                    playerBlockPosition.distToLowCornerSqr(
                        position.x.toDouble(), position.y.toDouble(), position.z.toDouble()
                    ).toFloat()
                } else {
                    this.getYawPlacementCost(playerYaw, position)
                }
            }
        } else {
            val movementYaw = MovementPhysics.getMovementYaw()
            for (position in positions) {
                costs[position] = this.getYawPlacementCost(movementYaw, position)
            }
        }
        return costs
    }

    override fun runPlacementCycle() {
        val player = ClientAccess.collectiveMinecraft.player
        if (player != null && !player.isPassenger) {
            val timer = ClientContext.serverStateManager.teleportAckTimer
            this.recentPositionCorrection = !timer.hasElapsed(100L)
            if (this.recentPositionCorrection) {
                this.placementBaselineY = player.y
            }

            try {
                if (this.fireworkSetting.value) {
                    val multiTask = multiTaskModule
                    if (!multiTask.isItemUseRestricted()) {
                        ClientAccess.collectiveMinecraft.execute {
                            val airPlaceEnabled = this.placementAirPlaceSetting.value
                            val raytraceEnabled = this.placementRaytraceSetting.value
                            this.fireworkSupport.processFireworks(airPlaceEnabled, raytraceEnabled)
                        }
                    }
                }

                super.runPlacementCycle()
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun collectPlacementPositions(
        visitedHashes: MutableSet<Int>,
        anchor: BlockPos,
        offsetY: Int,
        offsets: List<Vec3>,
        collisionBounds: AABB?
    ): List<BlockPos> {
        val placements = ArrayList<BlockPos>()
        visitedHashes.add(anchor.hashCode())

        val level = ClientAccess.collectiveMinecraft.level ?: return placements
        val player = ClientAccess.collectiveMinecraft.player ?: return placements

        for (offset in offsets) {
            val blockOffset = BlockPos.containing(offset)
            val position = anchor.offset(blockOffset)

            if (this.flattenSetting.value) {
                if (offset.y == -1.0) {
                    if (this.shouldSkipFlattenPosition(position)) {
                        continue
                    }
                }
            }

            if (this.coverSetting.value) {
                if (offset.y == this.getPlayerHeightInBlocks().toDouble()) {
                    if (PlayerStateUtils.isLocalLowProfile()) {
                        if (!RotationManager.is2b2tMode()) {
                            continue
                        }
                    }
                }
            }

            var workingBounds: AABB = if (collisionBounds == null) {
                AABB(position)
            } else {
                BoundingBoxUtils.placeAtBlock(collisionBounds, position)
            }
            val placementBounds = workingBounds

            this.breakInstantlyMineableBlock(position)

            val positionState = level.getBlockState(position)
            var occupied = !positionState.canBeReplaced()

            if (ClientContext.worldStateManager.hasActiveGlassUpdate(position)) {
                occupied = false
            }

            if (occupied) {
                workingBounds = player.boundingBox
                val positionBounds = AABB(position)
                if (!BoundingBoxUtils.hasVolumeOverlap(workingBounds, positionBounds)) {
                    continue
                }
            }

            val blockingEntities = level.getEntitiesOfClass(LivingEntity::class.java, placementBounds) { entity ->
                if (entity != player) {
                    val entityBounds = entity.boundingBox
                    BoundingBoxUtils.hasVolumeOverlap(placementBounds, entityBounds)
                } else {
                    true
                }
            }

            if (blockingEntities.isNotEmpty()) {
                val positionHash = position.hashCode()
                if (!visitedHashes.contains(positionHash) && offset.y == offsetY.toDouble()) {
                    placements.addAll(this.collectPlacementPositions(visitedHashes, position, offsetY, offsets, collisionBounds))
                    continue
                }
            }

            if (!occupied) {
                val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
                val placementFace = BlockInteractionUtils.findSupportDirection(position, strictDirection)
                if (placementFace == null) {
                    if (!this.placementAirPlaceSetting.value) {
                        val strictSupportDirection = BlockInteractionUtils.isStrictDirectionEnabled()
                        val supportPosition = BlockInteractionUtils.findSupportPosition(
                            position, 2, true, strictSupportDirection
                        )
                        if (supportPosition != null) {
                            placements.add(supportPosition)
                        }
                    }
                }

                placements.add(position)
            }
        }

        return placements
    }

    @Suppress("UNCHECKED_CAST")
    override fun sortPlacementPositions(positions: MutableList<BlockPos>) {
        if (positions.size <= 1) {
            return
        }
        positions.sortWith(this.getPlacementComparator(this.computePlacementCosts(positions)))
    }

    private fun getRelativePlacementOffsets(offsetY: Int): List<Vec3> {
        val offsets = ArrayList<Vec3>(
            listOf(
                Vec3(-1.0, offsetY.toDouble(), 0.0),
                Vec3(1.0, offsetY.toDouble(), 0.0),
                Vec3(0.0, offsetY.toDouble(), 1.0),
                Vec3(0.0, offsetY.toDouble(), -1.0)
            )
        )

        if (this.nearbyCrystalCount > 3) {
            if (this.smartFaceSetting.value) {
                val raisedOffsets = offsets.map { it.add(0.0, 1.0, 0.0) }
                offsets.addAll(raisedOffsets)
            }
        }

        if (this.flattenSetting.value) {
            offsets.add(Vec3(0.0, (offsetY - 1).toDouble(), 0.0))
        }

        if (this.coverSetting.value) {
            offsets.add(Vec3(0.0, (offsetY + this.getPlayerHeightInBlocks()).toDouble(), 0.0))
        }

        return offsets
    }

    @EventHandler
    private fun runSequentialPlacement(event: BlockUpdateAppliedEvent) {
        if (this.sequentialSetting.value != SequentialMode.NONE
            && (this.sequentialSetting.value == SequentialMode.STRONG || !this.sequentialPlacementComplete)) {
            this.runPlacementCycle()
            this.sequentialPlacementComplete = this.hasPlacedThisCycle()
        }
    }

    @EventHandler
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        this.sequentialPlacementComplete = false
        if (!this.centered) {
            if (this.centerSetting.value) {
                val player = ClientAccess.collectiveMinecraft.player
                if (player != null && !player.isPassenger) {
                    this.centered = centerPlayer()
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getPlacementPositions(): MutableList<BlockPos> {
        val fireworkPositions = if (this.fireworkSetting.value) {
            EntityUtils.getFireworkPositions()
        } else {
            emptySet()
        }

        val visitedHashes = HashSet<Int>()
        val placementOrigin = this.getPlacementOrigin()
        // Переборы/шейпы переиспользуются на весь цикл, а не пересоздаются на каждый кандидат.
        val blockSlot = this.findPlacementSwapSlot()
        var placementState = Blocks.OBSIDIAN.defaultBlockState()
        if (blockSlot != null && blockSlot.isFound) {
            val placementStack = blockSlot.stack
            val item = placementStack.item
            if (item is BlockItem) {
                placementState = item.block.defaultBlockState()
            }
        }
        val level = ClientAccess.collectiveMinecraft.level
        var collisionBounds: AABB? = null
        if (level != null) {
            val collisionShape = placementState.getCollisionShape(level, BlockPos.ZERO)
            if (!collisionShape.isEmpty) {
                collisionBounds = collisionShape.bounds()
            }
        }
        val offsets = this.getRelativePlacementOffsets(0)
        val positions = this.collectPlacementPositions(
            visitedHashes, placementOrigin, 0, offsets, collisionBounds
        ).toMutableList()
        val enemyPositions = if (this.blockIgnoreSetting.value == BlockIgnoreMode.ENEMY || this.blockIgnoreSetting.value == BlockIgnoreMode.BOTH) {
            this.collectEnemyPositions()
        } else {
            emptySet()
        }

        positions.removeIf { position ->
            if (!this.isIgnoredBlockPosition(position, enemyPositions)) {
                if (this.fireworkSetting.value) {
                    if (this.fireworkSupport.isTrackedFireworkPosition(fireworkPositions, position)) {
                        return@removeIf true
                    }
                }
                false
            } else {
                true
            }
        }
        return positions
    }

    @EventHandler
    private fun observeSurroundPackets(event: PacketEvent.Receive) {
        val player = ClientAccess.collectiveMinecraft.player
        val packet = event.packet

        if (packet is ClientboundPlayerPositionPacket && player != null) {
            val packetPosition = packet.change().position()
            val distanceSquared = player.position().distanceToSqr(packetPosition)

            if (distanceSquared > 9.0) {
                // Как и соседняя ветка с кристаллом: мутации только на клиентском потоке
                // (событие приходит из netty-потока).
                ClientAccess.collectiveMinecraft.execute {
                    this.placementBaselineY = packet.change().position().y
                    ClientContext.serverStateManager.teleportAckTimer.reset()
                }
            }
        }

        if (packet is ClientboundAddEntityPacket) {
            if (packet.type == EntityTypes.END_CRYSTAL) {
                if (this.smartFaceSetting.value) {
                    val crystalPosition = BlockPos.containing(packet.x, packet.y, packet.z)
                    ClientAccess.collectiveMinecraft.execute {
                        val localP = ClientAccess.collectiveMinecraft.player
                        if (localP != null) {
                            val spawnSurroundPositions = PlayerStateUtils.getSurroundPositions(localP)
                            val crystalBase = crystalPosition.below()
                            if (spawnSurroundPositions.contains(crystalBase)) {
                                this.nearbyCrystalCount++
                            }
                        }
                    }
                }
            }
        }

        if (this.fireworkSetting.value) {
            this.fireworkSupport.observeSpawnPacket(event)
        }
    }

    private fun getYawPlacementCost(yaw: Float, position: BlockPos): Float {
        val targetCenter = Vec3.atCenterOf(position)
        val targetYaw = RotationManager.getRotationsToPosition(targetCenter)[0]
        val yawDifference = Mth.degreesDifferenceAbs(yaw, targetYaw)
        val height = position.y.toFloat()
        return yawDifference + height * 0.001f
    }

    private fun hasNontrivialSurroundCollision(entity: LivingEntity): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val occupiedPositions = PlayerStateUtils.getOccupiedBlockPositions(entity)
        for (position in occupiedPositions) {
            val blockState = level.getBlockState(position)
            val shape = blockState.getCollisionShape(level, BlockPos.ZERO)
            if (!shape.isEmpty) {
                val bounds = shape.bounds()
                if (!BoundingBoxUtils.hasUnitDimensions(bounds)) {
                    return true
                }
            }
        }
        return false
    }

    override fun shouldHandleCrystalSpawn(): Boolean {
        return if (RotationManager.isMovementSyncEnabled()) {
            this.instantSetting.value
        } else {
            ClientContext.crystalInteractionManager.isInstantAttackEnabled
        }
    }

    private enum class BlockIgnoreMode(private val displayName: String) : NamedEntry {
        NONE("None"),
        MINING("Mining"),
        ENEMY("Enemy"),
        BOTH("Both");

        override fun getName(): String = this.displayName
    }

    private enum class SequentialMode(private val displayName: String) : NamedEntry {
        NONE("None"),
        STRICT("Strict"),
        STRONG("Strong");

        override fun getName(): String = this.displayName
    }

    companion object {
        lateinit var antiCheat: AntiCheatModule
        lateinit var speedMine: SpeedMineModule

        @JvmStatic
        fun centerPlayer(): Boolean {
            val player = ClientAccess.collectiveMinecraft.player ?: return false
            if (!player.onGround()) {
                return false
            }

            if (ClientContext.holeManager.isInHole) {
                return false
            }

            val occupiedPositions = PlayerStateUtils.getSurroundPositions(player)
            if (occupiedPositions.size <= 4) {
                return false
            }

            if (WallsVelocityHandler.hasRaisedPlayerCollision()) {
                return false
            }

            val originalPosition = player.position()
            var centerPosition = Vec3(
                floor(player.x) + 0.5,
                player.y,
                floor(player.z) + 0.5
            )
            val centerDelta = centerPosition.subtract(originalPosition)
            val distance = centerDelta.length()

            val baseMoveSpeed = MovementPhysics.getBaseMoveSpeed(false)
            val maximumStep = baseMoveSpeed + 0.02
            if (distance > maximumStep) {
                val stepFraction = maximumStep / distance
                val remainingDelta = centerPosition.subtract(originalPosition)
                val stepDelta = remainingDelta.scale(stepFraction)
                centerPosition = originalPosition.add(stepDelta)
            }

            player.setPos(centerPosition)
            val movedDistance = originalPosition.distanceTo(centerPosition)
            return movedDistance <= 0.01
        }

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            antiCheat = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            speedMine = modules.getModule(SpeedMineModule::class.java) as SpeedMineModule
        }
    }
}
