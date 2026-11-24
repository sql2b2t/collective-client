package me.collective.client.modules.combat

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.commands.FakeplayerCommand
import me.collective.client.events.BlockUpdateAppliedEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.render.LogoutSpotsModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.EndCrystalState
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SidePlayer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.movement.MovementPhysics
import me.collective.client.utils.movement.WallsVelocityHandler
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.Optional
import kotlin.math.abs

open class AutoTrapModule : PlacementModule {
    @JvmField
    val rangeSetting: Setting<Float>
    private val sequentialSetting: Setting<Boolean>
    @JvmField
    val targetsSetting: Setting<Boolean>
    @JvmField
    val headSetting: Setting<Boolean>
    @JvmField
    val faceSetting: Setting<Boolean>
    @JvmField
    val legsSetting: Setting<Boolean>
    @JvmField
    val belowSetting: Setting<Boolean>
    @JvmField
    val antiStepSetting: Setting<Boolean>
    @JvmField
    val onlyPhasedSetting: Setting<Boolean>
    @JvmField
    val ignoreNakedsSetting: Setting<Boolean>
    @JvmField
    val ignoreCrawlSetting: Setting<Boolean>
    @JvmField
    val logoutsSetting: Setting<Boolean>
    private val onlyObbySetting: Setting<Boolean>
    private val completeSetting: Setting<Boolean>
    private val miningSupport: TrapMiningSupport
    private var targetPlayer: Player? = null
    private var sequentialHandled = false
    private var miningSupportMode = false
    @JvmField
    var miningSupportAllowed = false

    constructor(name: String, description: String, category: ModuleCategory) : super(name, description, category) {
        val rangeDefinition = NumberSetting("Range", 5.0f, 1.0f, 6.5f).withUnit("m")
        rangeSetting = add(rangeDefinition)
        sequentialSetting = add(BooleanSetting("Sequential", false))
        targetsSetting = add(BooleanSetting("Targets", true).markAsGroup())
        headSetting = add(BooleanSetting("Head", true).dependsOn(targetsSetting))
        faceSetting = add(BooleanSetting("Face", true).dependsOn(targetsSetting))
        legsSetting = add(BooleanSetting("Legs", false).dependsOn(targetsSetting))
        belowSetting = add(BooleanSetting("Below", false).dependsOn(targetsSetting))
        antiStepSetting = add(BooleanSetting("AntiStep", false))
        onlyPhasedSetting = add(BooleanSetting("OnlyPhased", false))
        ignoreNakedsSetting = add(BooleanSetting("IgnoreNakeds", false))
        ignoreCrawlSetting = add(BooleanSetting("IgnoreCrawl", false))
        logoutsSetting = add(BooleanSetting("Logouts", false))
        onlyObbySetting = add(BooleanSetting("OnlyObby", false), placementRaytraceSetting)
        completeSetting = createCompleteSetting()
        miningSupport = TrapMiningSupport(this)
        miningSupportAllowed = false
    }

    constructor() : super("AutoTrap", "Traps your enemies with obby.", ModuleCategory.COMBAT) {
        rangeSetting = add(NumberSetting("Range", 5.0f, 1.0f, 6.5f).withUnit("m"))
        sequentialSetting = add(BooleanSetting("Sequential", false))
        targetsSetting = add(BooleanSetting("Targets", true).markAsGroup())
        headSetting = add(BooleanSetting("Head", true).dependsOn(targetsSetting))
        faceSetting = add(BooleanSetting("Face", true).dependsOn(targetsSetting))
        legsSetting = add(BooleanSetting("Legs", false).dependsOn(targetsSetting))
        belowSetting = add(BooleanSetting("Below", false).dependsOn(targetsSetting))
        antiStepSetting = add(BooleanSetting("AntiStep", false))
        onlyPhasedSetting = add(BooleanSetting("OnlyPhased", false))
        ignoreNakedsSetting = add(BooleanSetting("IgnoreNakeds", false))
        ignoreCrawlSetting = add(BooleanSetting("IgnoreCrawl", false))
        logoutsSetting = add(BooleanSetting("Logouts", false))
        onlyObbySetting = add(BooleanSetting("OnlyObby", false))
        completeSetting = createCompleteSetting()
        miningSupport = TrapMiningSupport(this)
        miningSupportAllowed = true
        setDisableOnRespawn(true)
        placementJumpSetting.setDefaultValue(false)
    }

    protected fun isInsideTargetTopBounds(blockPos: BlockPos): Boolean {
        val targetSnapshot = targetPlayer ?: return false
        val boundsSnapshot = getTargetTopBounds(targetSnapshot)
        return boundsSnapshot.contains(blockPos.center)
    }

    open protected fun getTargetPosition(player: Player): Vec3 {
        val currentPosition = player.position()
        if (player is FakeplayerCommand.SimulatedPlayer ||
            player is SidePlayer ||
            player === ClientAccess.collectiveMinecraft.player ||
            !RotationManager.isMovementSyncEnabled()
        ) {
            return currentPosition
        }

        val latencyTicks = ClientContext.serverStateManager.latencyTicks
        val predictionTicks = Mth.clamp(latencyTicks, 0, 5)
        val predictedBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, predictionTicks)
        return BoundingBoxUtils.getBottomCenter(predictedBounds)
    }

    private fun getSupportSortKey(blockPos: BlockPos): Double {
        return if (placementRaytraceSetting.value) {
            val player = ClientAccess.collectiveMinecraft.player ?: return blockPos.y.toDouble()
            -player.position().distanceToSqr(blockPos.center)
        } else {
            blockPos.y.toDouble()
        }
    }

    override fun getPlacementRotationPriority(): Int = 150

    override fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
        if (!onlyObbySetting.value && !miningSupportMode) {
            return super.findPlacementSwapSlot()
        }
        return InventoryUtils.findSwapSlot(Items.OBSIDIAN, false)
    }

    private fun createCompleteSetting(): Setting<Boolean> {
        return add(BooleanSetting("Complete", false).dependsOn(placementAutoDisableSetting), placementHeightSetting)
    }

    protected fun shouldDisableWhenComplete(): Boolean = completeSetting.value

    override fun getInfo(): String? {
        return when {
            miningSupportMode -> "Single"
            targetPlayer != null -> ClientContext.aliasRegistry.getPlayerAlias(targetPlayer)
            else -> null
        }
    }

    protected open fun shouldPlaceBelow(): Boolean = belowSetting.value

    fun hasLogoutAbovePosition(blockPos: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        for (entity in level.entitiesForRendering()) {
            if (entity is EndCrystalState && !entity.isClientAttacked()) {
                if (entity.blockPosition() == blockPos.above()) {
                    return true
                }
            }
        }
        return false
    }

    protected fun shouldSkipPlacement(blockPos: BlockPos): Boolean {
        val target = targetPlayer ?: return true
        val player = ClientAccess.collectiveMinecraft.player ?: return true

        if (this !is SelfTrapModule) {
            if (hasLogoutAbovePosition(blockPos)) {
                return true
            }
        }

        val eyeSnapshot = player.eyePosition
        val distance = eyeSnapshot.distanceTo(blockPos.center)
        if (distance > rangeSetting.value) {
            return true
        }

        if (!legsSetting.value) {
            if (target.bbHeight > 1.0f) {
                if (blockPos.y == target.blockPosition().y) {
                    val abovePos = blockPos.above()
                    if (WorldUtils.isPlacementOccupied(abovePos)) {
                        return true
                    }
                }
            }
        }

        return false
    }

    protected open fun isValidTarget(player: Player): Boolean {
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return false
        if (!player.isDeadOrDying && player.isAlive && player !== localPlayer) {
            if (ClientContext.socialRegistry.isFriendPlayer(player)) {
                return false
            }

            if (ignoreNakedsSetting.value && !PlayerStateUtils.hasArmor(player)) {
                return false
            }

            if (onlyPhasedSetting.value && !isPhasedPlayer(player)) {
                return false
            }

            if (ignoreCrawlSetting.value && PlayerStateUtils.isLowProfile(player) && !player.isFallFlying) {
                return false
            }

            if (!RotationManager.isMovementSyncEnabled() && player !is SidePlayer) {
                if (MovementPhysics.getHorizontalSpeedKmh(player) > 20.0) {
                    return false
                }

                if (!EntityUtils.hasCollisionBelow(player)) {
                    return false
                }

                if (abs(player.yo - player.y) > 0.6) {
                    return false
                }
            }

            var maximumRange = rangeSetting.value + 2.0f
            if (player.isFallFlying) {
                maximumRange = 14.0f
            }

            return getTargetPosition(player).distanceTo(localPlayer.eyePosition) <= maximumRange
        }

        return false
    }

    protected open fun includeLowerSideOffsets(): Boolean = true

    protected open fun isHeadOnly(): Boolean = headSetting.value && !faceSetting.value

    private fun findSupportNeighbor(blockPos: BlockPos, plannedPositions: List<BlockPos>): BlockPos? {
        val directions = Direction.values()
        return Arrays.stream(directions)
            .map(blockPos::relative)
            .sorted(Comparator.comparingDouble(this::getSupportSortKey))
            .filter { neighborPos ->
                if (findSupportDirection(neighborPos, BlockInteractionUtils.isStrictDirectionEnabled(), plannedPositions) != null) {
                    if (BlockInteractionUtils.canPlaceAt(neighborPos, true, placementAttackSetting.value)) {
                        return@filter true
                    }
                }
                false
            }
            .findFirst()
            .orElse(null)
    }

    open protected fun collectTargetOffsets(targetPos: BlockPos, baseYOffset: Int, targetHeight: Float): List<Vec3> {
        val offsets = ArrayList<Vec3>()

        if (isHeadOnly()) {
            offsets.add(Vec3(0.0, getHeadOnlyOffsetY(baseYOffset, targetHeight), 0.0))
            val headOffsetIterator = collectBaseOffsets(baseYOffset, targetHeight).iterator()
            if (headOffsetIterator.hasNext()) {
                val headOffset = headOffsetIterator.next()
                if (!placementAirPlaceSetting.value) {
                    val headRelativePos = BlockPos.containing(headOffset)
                    val headNeighborPos = headRelativePos.offset(targetPos)
                    if (!WorldUtils.isPlacementOccupied(headNeighborPos)) {
                        val strictDirectionSnapshot = BlockInteractionUtils.isStrictDirectionEnabled()
                        if (BlockInteractionUtils.findSupportDirection(headNeighborPos, strictDirectionSnapshot, placementRaytraceSetting.value) == null) {
                            if (headOffset.y > baseYOffset) {
                                offsets.add(Vec3(-1.0, (baseYOffset + 1).toDouble(), 0.0))
                            } else {
                                offsets.add(Vec3(-1.0, baseYOffset.toDouble(), 0.0))
                            }
                        }
                    }
                }
            }
        } else {
            if (headSetting.value) {
                offsets.add(Vec3(0.0, getHeadOffsetY(baseYOffset, targetHeight), 0.0))
            }
        }

        for (sideOffset in collectBaseOffsets(baseYOffset, targetHeight)) {
            if (sideOffset.y <= targetHeight) {
                if (!faceSetting.value && sideOffset.y > baseYOffset) {
                    continue
                }
                if (!includeLowerSideOffsets() && sideOffset.y == baseYOffset.toDouble()) {
                    continue
                }
                offsets.add(sideOffset)
            }
        }

        if (shouldPlaceBelow()) {
            offsets.add(Vec3(0.0, (baseYOffset - 1).toDouble(), 0.0))
        }

        if (antiStepSetting.value) {
            offsets.add(Vec3(0.0, getAntiStepOffsetY(baseYOffset, targetHeight), 0.0))
        }

        return offsets
    }

    open protected fun collectBaseOffsets(baseYOffset: Int, targetHeight: Float): List<Vec3> {
        return listOf(
            Vec3(0.0, getBaseHeadOffsetY(baseYOffset, targetHeight), 0.0),
            Vec3(1.0, baseYOffset.toDouble(), 0.0),
            Vec3(0.0, baseYOffset.toDouble(), 1.0),
            Vec3(0.0, baseYOffset.toDouble(), -1.0),
            Vec3(-1.0, baseYOffset.toDouble(), 0.0),
            Vec3(1.0, (baseYOffset + 1).toDouble(), 0.0),
            Vec3(0.0, (baseYOffset + 1).toDouble(), 1.0),
            Vec3(0.0, (baseYOffset + 1).toDouble(), -1.0),
            Vec3(-1.0, (baseYOffset + 1).toDouble(), 0.0)
        )
    }

    private fun collectTrapPositions(visitedHashes: MutableList<Int>, plannedPositions: MutableList<BlockPos>, origin: BlockPos, baseYOffset: Int): MutableList<BlockPos> {
        val positions = ArrayList<BlockPos>()
        val originHash = origin.hashCode()
        visitedHashes.add(originHash)

        val target = targetPlayer ?: return positions
        val targetHeight = target.bbHeight

        for (offset in collectTargetOffsets(origin, baseYOffset, targetHeight)) {
            val candidatePos = origin.offset(offset.x.toInt(), offset.y.toInt(), offset.z.toInt())
            if (WorldUtils.isPlacementOccupied(candidatePos)) {
                val targetBoundsSnapshot = target.boundingBox
                val candidateBounds = AABB(candidatePos)
                if (!BoundingBoxUtils.hasVolumeOverlap(targetBoundsSnapshot, candidateBounds) || offset.y != 0.0) {
                    continue
                }
            }

            if (!shouldSkipAirPlacement(candidatePos, offset)) {
                val level = ClientAccess.collectiveMinecraft.level ?: continue
                val collisionBounds = AABB(candidatePos)
                if (level.getEntitiesOfClass(Player::class.java, collisionBounds) { true }.isNotEmpty()) {
                    val candidateHash = candidatePos.hashCode()
                    if (!visitedHashes.contains(candidateHash) && offset.y == 0.0) {
                        val nestedPositions = collectTrapPositions(visitedHashes, plannedPositions, candidatePos, baseYOffset)
                        positions.addAll(nestedPositions)
                        plannedPositions.addAll(nestedPositions)
                        continue
                    }
                }

                if (findSupportDirection(candidatePos, BlockInteractionUtils.isStrictDirectionEnabled(), plannedPositions) == null) {
                    if (!placementAirPlaceSetting.value) {
                        val supportNeighbor = findSupportNeighbor(candidatePos, plannedPositions)
                        if (supportNeighbor != null) {
                            positions.add(supportNeighbor)
                            plannedPositions.add(supportNeighbor)
                        }
                    }
                }

                positions.add(candidatePos)
                plannedPositions.add(candidatePos)
            }
        }

        if (!isHeadOnly()) {
            if (!legsSetting.value) {
                if (target.bbHeight > 1.0f) {
                    positions.removeIf { plannedPos -> plannedPos.y.toDouble() == target.y }
                }
            }
        }

        positions.remove(origin)
        return positions
    }

    private fun findSupportDirection(
        blockPos: BlockPos, strictDirection: Boolean, plannedPositions: List<BlockPos>
    ): Direction? {
        for (direction in Direction.values()) {
            val neighborPos = blockPos.relative(direction)
            if ((WorldUtils.isPlacementOccupied(neighborPos) || plannedPositions.contains(neighborPos)) &&
                (!strictDirection || isStrictSupport(neighborPos, direction))
            ) {
                return direction
            }
        }
        return null
    }

    @EventHandler
    private fun onSequentialPlacement(event: BlockUpdateAppliedEvent) {
        if (sequentialSetting.value && !sequentialHandled) {
            runPlacementCycle()
            sequentialHandled = hasPlacedThisCycle()
        }
    }

    @EventHandler
    private fun onPlayerTick(event: LocalPlayerPreTickEvent) {
        miningSupportMode = miningSupport.shouldUseMiningSupport()
        sequentialHandled = false
    }

    override fun sortPlacementPositions(positions: MutableList<BlockPos>) {
        val target = targetPlayer
        if (RotationManager.isMovementSyncEnabled() && target != null) {
            val targetYaw = target.yRot
            if (target.isFallFlying) {
                val movement = Vec3(target.x - target.xo, target.y - target.yo, target.z - target.zo)
                val movementDirection = movement.normalize()
                val predictedPosition = target.position().add(movementDirection.scale(8.0))
                positions.sortWith(Comparator.comparingDouble { sortPosition ->
                    sortPosition.distToCenterSqr(predictedPosition)
                })
            } else {
                positions.sortWith(Comparator.comparingDouble { sortPosition ->
                    val angleYaw = RotationManager.getRotationsToPosition(sortPosition.center)[0]
                    Mth.degreesDifferenceAbs(targetYaw, angleYaw).toDouble()
                })
            }
        } else {
            positions.sortWith(Comparator.comparingInt { sortPos -> -sortPos.y })
            if (placementRaytraceSetting.value) {
                val player = ClientAccess.collectiveMinecraft.player
                if (player != null) {
                    val playerPos = player.position()
                    positions.sortWith(Comparator.comparingDouble { sortPos ->
                        -playerPos.distanceTo(sortPos.center)
                    })
                }
            }
        }
    }

    protected open fun selectTarget(): Player? {
        val players = ObjectArrayList(worldPlayers)

        val logoutSpots = logoutSpotsModule
        if (logoutSpots != null && logoutSpots.isToggled) {
            if (logoutsSetting.value) {
                players.addAll(logoutSpots.getSpotPlayers())
            }
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val yawSnapshot = player.yRot
        return players.stream()
            .filter { isValidTarget(it) }
            .min(Comparator.comparingDouble { candidate ->
                val targetYaw = RotationManager.getRotationsToEntity(candidate)[0]
                Mth.degreesDifferenceAbs(yawSnapshot, targetYaw).toDouble()
            })
            .orElse(null)
    }

    private val worldPlayers: List<Player>
        get() = ClientAccess.collectiveMinecraft.level?.players() ?: emptyList()

    override fun placePositions(positions: MutableList<BlockPos>) {
        if (positions.isEmpty() && shouldDisableWhenComplete()) {
            disable()
        }
        super.placePositions(positions)
    }

    protected fun shouldSkipAirPlacement(blockPos: BlockPos, offset: Vec3): Boolean {
        if (placementAirPlaceSetting.value) {
            if (ClientContext.worldStateManager.hasPendingPlacement(blockPos)) {
                if (offset.y > 0.0) {
                    return true
                }
            }
        }
        return false
    }

    override fun getPlacementPositions(): MutableList<BlockPos> {
        if (miningSupportMode) {
            return miningSupport.collectMiningPositions().toMutableList()
        }

        targetPlayer = selectTarget()
        val target = targetPlayer ?: return mutableListOf()
        val targetPosition = getTargetPosition(target)

        val positions: MutableList<BlockPos> = try {
            collectTrapPositions(ArrayList(), ArrayList(), BlockPos.containing(targetPosition), 0)
        } catch (failure: Throwable) {
            return Collections.emptyList()
        }

        positions.removeIf { shouldSkipPlacement(it) }
        val priorityPos = positions.stream()
            .filter { isInsideTargetTopBounds(it) }
            .findFirst()
            .orElse(null)

        if (priorityPos != null) {
            positions.remove(priorityPos)
            positions.add(0, priorityPos)
        }
        return positions
    }

    private fun getTargetTopBounds(entity: Entity): AABB {
        val entityTop = entity.y + entity.bbHeight
        val topY = Math.ceil(entityTop)
        val minimumY = topY + 1.0
        val raisedBounds = entity.boundingBox.setMinY(minimumY)
        val maximumY = minimumY + 1.0
        return raisedBounds.setMaxY(maximumY)
    }

    private fun isPhasedPlayer(player: Player): Boolean {
        if (player.isFallFlying) {
            return false
        }
        val checkY = player.y + 0.1
        return WallsVelocityHandler.hasCollisionAtHeight(player, checkY)
    }

    companion object {
        private var logoutSpotsModule: LogoutSpotsModule? = null

        init {
            logoutSpotsModule = ClientContext.getModule(LogoutSpotsModule::class.java) as? LogoutSpotsModule
        }

        private fun isStrictSupport(position: BlockPos, direction: Direction): Boolean {
            val availableFaces = BlockInteractionUtils.getStrictDirections(position)
            return availableFaces.contains(direction.opposite)
        }

        private fun getHeadOnlyOffsetY(baseYOffset: Int, targetHeight: Float): Double {
            return (baseYOffset + targetHeight + 1.0f).toDouble()
        }

        private fun getHeadOffsetY(baseYOffset: Int, targetHeight: Float): Double {
            return (baseYOffset + targetHeight + 1.0f).toDouble()
        }

        private fun getAntiStepOffsetY(baseYOffset: Int, targetHeight: Float): Double {
            return (baseYOffset + targetHeight + 2.0f).toDouble()
        }

        private fun getBaseHeadOffsetY(baseYOffset: Int, targetHeight: Float): Double {
            return (baseYOffset + targetHeight + 1.0f).toDouble()
        }

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            logoutSpotsModule = modules.getModule(LogoutSpotsModule::class.java) as? LogoutSpotsModule
            TrapMiningSupport.bindReferencedModules(modules)
        }
    }
}
