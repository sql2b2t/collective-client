package me.collective.client.modules

import net.minecraft.world.entity.EntityTypes
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.*
import me.collective.client.managers.CrystalInteractionManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.managers.TaskScheduler
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.combat.OffhandModule
import me.collective.client.modules.exploit.ChorusControlModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.AntiCheatRotationsChoice
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.LockUtils
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.combat.BlockRaycastClassifier
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.*
import java.util.function.Predicate

abstract class PlacementModule(
    name: String,
    description: String,
    category: ModuleCategory,
    vararg aliases: String
) : Module(name, description, category, *aliases) {

    @JvmField
    protected val placementDelaySetting: Setting<Int>

    @JvmField
    protected val placementBptSetting: Setting<Int>

    @JvmField
    protected val placementAirPlaceSetting: Setting<Boolean>

    @JvmField
    protected val placementRaytraceSetting: Setting<Boolean>

    @JvmField
    protected val placementRotateSetting: Setting<Boolean>

    @JvmField
    protected val placementAttackSetting: Setting<Boolean>

    @JvmField
    protected val placementAutoDisableSetting: Setting<Boolean>

    @JvmField
    protected val placementJumpSetting: Setting<Boolean>

    @JvmField
    val placementChorusSetting: Setting<Boolean>

    @JvmField
    val placementPearlSetting: Setting<Boolean>

    @JvmField
    val placementRubberBandSetting: Setting<Boolean>

    @JvmField
    val placementHeightSetting: Setting<Boolean>

    @JvmField
    protected val placementRenderSetting: Setting<Boolean>

    @JvmField
    protected val placementFillSetting: Setting<Color>

    @JvmField
    protected val placementOutlineSetting: Setting<Color>

    @JvmField
    protected val placementLineWidthSetting: Setting<Float>

    @JvmField
    protected val placementFadeSetting: Setting<Boolean>

    @JvmField
    protected val placementFadeTimeSetting: Setting<Float>

    private val placementTimer = ElapsedTimer()
    private val attackTimer = ElapsedTimer()
    private var lastPlacementHitPosition: Vec3? = null
    private var placementDirection: Direction? = null
    private var pendingAttackEntity: Entity? = null
    private var placementsThisCycle: Int = 0
    private var missingBlockAttempts: Int = 0

    @JvmField
    var placementBaselineY: Double = 0.0

    private var disableOnRespawn: Boolean = false
    private var ignoreMissingBlocks: Boolean = false
    private var inventoryPrepared: Boolean = false
    private var batchCooldownActive: Boolean = false
    private var placedThisCycle: Boolean = false
    private var skipNextCycle: Boolean = false

    @Volatile
    private var tickPlacementActive: Boolean = false

    constructor(name: String, category: ModuleCategory, vararg aliases: String) : this(name, "", category, *aliases)

    init {
        val delay = NumberSetting("Delay", 50, 0, 250).withUnit("ms")
        this.placementDelaySetting = this.add(delay)
        this.placementBptSetting = this.add(NumberSetting("BPT", 8, 1, 9))
        this.placementAirPlaceSetting = this.add(BooleanSetting("AirPlace", false))
        this.placementRaytraceSetting = this.add(BooleanSetting("Raytrace", false))
        this.placementRotateSetting = this.add(BooleanSetting("Rotate", false))
        this.placementAttackSetting = this.add(BooleanSetting("Attack", true))
        this.placementAutoDisableSetting = this.add(BooleanSetting("AutoDisable", false).markAsGroup())

        val jump = BooleanSetting("Jump", true)
        val autoDisableDependency = arrayOf<Setting<*>>(this.placementAutoDisableSetting)
        this.placementJumpSetting = this.add(jump.dependsOn(*autoDisableDependency))
        this.placementChorusSetting = this.add(BooleanSetting("Chorus", false).dependsOn(this.placementAutoDisableSetting))
        this.placementPearlSetting = this.add(BooleanSetting("Pearl", false).dependsOn(this.placementAutoDisableSetting))
        this.placementRubberBandSetting = this.add(BooleanSetting("RubberBand", false).dependsOn(this.placementAutoDisableSetting))
        this.placementHeightSetting = this.add(BooleanSetting("Height", false).dependsOn(this.placementAutoDisableSetting))

        this.placementRenderSetting = this.add(BooleanSetting("Render", false).markVisibilityDependency())
        this.placementFillSetting = this.add(ColorSetting("Fill", Color(189, 153, 255, 8)) {
            this.placementRenderSetting.isExpanded()
        })
        this.placementOutlineSetting = this.add(ColorSetting("Outline", Color(189, 153, 255, 91)) {
            this.placementRenderSetting.isExpanded()
        })
        this.placementLineWidthSetting = this.add(NumberSetting("LineWidth", 1.0f, 0.1f, 3.0f).dependsOn(this.placementRenderSetting))
        this.placementFadeSetting = this.add(BooleanSetting("Fade", true).dependsOn(this.placementRenderSetting))

        val fadeTime = NumberSetting("FadeTime", 1.0f, 0.1f, 1.0f).withUnit("s")
        val fadeDependencies = arrayOf(this.placementRenderSetting, this.placementFadeSetting)
        this.placementFadeTimeSetting = this.add(fadeTime.dependsOn(*fadeDependencies))

        val renderer = ClientContext.featureBoxRenderer
        val renderConfiguration = FeatureBoxRenderer.RenderConfiguration(
            this, this.placementFillSetting, this.placementOutlineSetting, this.placementLineWidthSetting, this.placementFadeTimeSetting,
            { true }, this.placementFadeSetting, 450
        )
        renderer.register(renderConfiguration)
    }

    override fun <S : Setting<*>> add(setting: S): S {
        if (!this.settings.contains(this.placementFadeTimeSetting)) {
            return super.add(setting)
        }
        val settingsRegistry = this.settings
        settingsRegistry.add(settingsRegistry.indexOf(this.placementAutoDisableSetting), setting)
        return setting
    }

    override fun onEnable() {
        if (!this.isWorldUnavailable()) {
            val playerForHeight: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            this.placementBaselineY = playerForHeight.y
        }
        this.placementsThisCycle = 0
        this.missingBlockAttempts = 0
        this.skipNextCycle = false
    }

    protected open fun disableForHeightChange(): Boolean {
        if (!this.placementHeightSetting.value) {
            return false
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (player.onGround()) {
            return false
        }
        val trackedHeight = ClientContext.playerStateTracker.lastGroundedY
        val heightDifference = trackedHeight - player.y
        if (Math.abs(heightDifference) >= 2.0) {
            this.setToggled(false)
            return true
        }
        return false
    }

    protected open fun getPlacementOrigin(): BlockPos {
        return PlayerStateUtils.getLocalRoundedBlockPosition()
    }

    protected open fun disableForJumpOrSleep(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val heightSinceBaseline = player.y - this.placementBaselineY
        if (player.onGround()) {
            this.placementBaselineY = player.y
        }
        val currentY = player.y
        val baselineY = this.placementBaselineY
        var jumpOrSleepingResult = currentY > baselineY + 0.1 && heightSinceBaseline >= 0.4
        if (!jumpOrSleepingResult) {
            jumpOrSleepingResult = player.isSleeping
        }
        if (this.placementJumpSetting.value && jumpOrSleepingResult) {
            this.setToggled(false)
            return true
        }
        return false
    }

    protected open fun shouldDiscardAttackedCrystal(): Boolean {
        return false
    }

    protected open fun usesBatchRotations(): Boolean {
        val movementSyncSettingRef = antiCheatModule.movementSyncSetting
        if (movementSyncSettingRef.value) {
            val rotationModeSettingRef = antiCheatModule.rotationsSetting
            if (rotationModeSettingRef.value == AntiCheatRotationsChoice.SILENT) {
                return false
            }
        }
        if (!this.placementRotateSetting.value) {
            return false
        }
        return this.placementBptSetting.value > 1
    }

    protected open fun getPlacementRotationPriority(): Int {
        return 20
    }

    protected open fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
        val obsidianSlot = InventoryUtils.findSwapSlot(Items.OBSIDIAN, false)
        if (obsidianSlot.isFound()) {
            return obsidianSlot
        }
        // Фолбэк: первый попавшийся взрывостойкий BlockItem (>= 600), порядок — по слотам.
        val fallbackPredicate = Predicate<ItemStack> { stack -> isFallbackBlock(stack, 0.0f, 600.0f) }
        return InventoryUtils.findSwapSlot(fallbackPredicate, false)
    }

    private fun getPlacementBatchLimit(): Int {
        if (!this.tickPlacementActive) {
            return 1
        }
        if (this.usesBatchRotations() && this.batchCooldownActive) {
            return 1
        }
        return this.placementBptSetting.value
    }

    protected fun setDisableOnRespawn(enabled: Boolean) {
        this.disableOnRespawn = enabled
    }

    protected open fun deduplicatePlacementPositions(positions: MutableList<BlockPos>?) {
        if (positions != null && positions.size > 1) {
            val uniquePositions = LinkedHashSet(positions)
            positions.clear()
            positions.addAll(uniquePositions)
        }
    }

    protected open fun runPlacementCycle() {
        this.placedThisCycle = false
        if (this.skipNextCycle) {
            this.skipNextCycle = false
            return
        }
        if (OffhandModule.isRecentSwapWindowActive() || SpeedMineModule.isSwapPending()) {
            return
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.isSpectator || !player.isAlive || player.isHandsBusy || player.isSleeping) {
            return
        }
        if (multiTaskModule.shouldPauseModule(this)) {
            return
        }
        val delayMillis = this.placementDelaySetting.value.toLong()
        if (!this.placementTimer.hasElapsed(delayMillis)) {
            return
        }
        val candidates = this.getPlacementPositions()
        val blockSlot = this.findPlacementSwapSlot()
        this.filterPlacementPositions(blockSlot, candidates)
        this.sortPlacementPositions(candidates)
        val placementTask = Runnable { this.placePositions(candidates) }
        LockUtils.runWithSharedLock(placementTask)
        this.placementsThisCycle = 0
    }

    protected open fun shouldSendBatchRotation(): Boolean {
        if (!this.tickPlacementActive || !this.usesBatchRotations()) {
            return false
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (!player.onGround()) {
            return false
        }
        return ClientContext.playerStateTracker.groundedTicks > 1 && !this.batchCooldownActive
    }

    @EventHandler
    private fun onItemStackFinishUsingItemEvent(event: ItemUseEvent.BeforeFinish) {
        val finishedStack = event.stack
        if (finishedStack.item != Items.CHORUS_FRUIT) {
            return
        }
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.isShiftKeyDown) {
            return
        }
        if (chorusControlModule.isToggled()) {
            return
        }
        if (this.placementChorusSetting.value) {
            this.disable()
        } else {
            this.skipNextCycle = true
        }
    }

    @EventHandler
    private fun onInboundPacket(event: PacketEvent.Receive) {
        val packet = event.packet
        if (this.placementAttackSetting.value && packet is ClientboundAddEntityPacket) {
            val spawnPacket = packet
            if (spawnPacket.type == EntityTypes.END_CRYSTAL && this.shouldHandleCrystalSpawn()) {
                if (OffhandModule.isRecentSwapWindowActive()) {
                    return
                }
                if (multiTaskModule.shouldPauseModule(this)) {
                    return
                }
                if (RotationManager.isMovementSyncEnabled()) {
                    return
                }

                val spawnPositionSnapshot = Vec3(spawnPacket.x, spawnPacket.y, spawnPacket.z)
                val spawnBounds = AABB.ofSize(spawnPositionSnapshot, 2.0, 2.0, 2.0)
                val crystalBounds = spawnBounds.move(0.0, 1.0, 0.0)
                val candidates = this.getPlacementPositions()
                this.filterPlacementPositions(this.findPlacementSwapSlot(), candidates)

                for (candidatePos in candidates) {
                    if (BoundingBoxUtils.intersectsBlock(crystalBounds, candidatePos)) {
                        val packetEntityId = spawnPacket.id
                        if (!ClientContext.crystalInteractionManager.attackEntity(packetEntityId, true)) {
                            return
                        }
                        break
                    }
                }

                val placementTask = Runnable { this.placePositions(candidates) }
                LockUtils.runWithSharedLock(placementTask)
            }
        }

        if (packet is ClientboundPlayerPositionPacket) {
            val positionChange = packet.change()
            val serverPosition = positionChange.position()
            this.placementBaselineY = serverPosition.y()

            val player = ClientAccess.collectiveMinecraft.player
            if (player != null) {
                val playerPositionSnapshot = player.position()
                val correctedPosition = packet.change().position()
                val correctionDistanceSquared = playerPositionSnapshot.distanceToSqr(correctedPosition)
                if (correctionDistanceSquared >= 1.0) {
                    if (this.placementRubberBandSetting.value) {
                        this.disable()
                        return
                    }
                }
            }
        }

        if (packet is ClientboundRespawnPacket && this.disableOnRespawn) {
            this.disable()
        }

        if (packet is ClientboundOpenScreenPacket) {
            if (!this.placementTimer.hasElapsed(100L)) {
                PacketSender.send(ServerboundContainerClosePacket(packet.containerId))
                event.cancel()
            }
        }
    }

    protected open fun sendBatchRotation(
        currentPos: BlockPos,
        nextPos: BlockPos,
        currentAngles: PlacementAngles,
        previousAngles: PlacementAngles?
    ): Boolean {
        if (previousAngles != null) {
            if (previousAngles.intersectsBlock(currentPos)) {
                return false
            }
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        if (currentPos == nextPos) {
            val playerAngles = PlacementAngles(player.yRot, player.xRot)
            if (playerAngles.intersectsBlock(currentPos)) {
                return false
            }
        }

        val playerX = player.x
        val playerY = player.y
        val playerZ = player.z
        val angleYaw = currentAngles.yaw
        val anglePitch = currentAngles.pitch
        val onGround = player.onGround()
        PacketSender.sendPositionRotation(playerX, playerY, playerZ, angleYaw, anglePitch, onGround)
        return true
    }

    private fun tryPlaceAtHit(blockPos: BlockPos, hitPosition: Vec3?, stack: ItemStack): Boolean {
        val directionSnapshot = this.placementDirection
        val airPlaceFlag = this.placementAirPlaceSetting.value
        val hand = InteractionHand.MAIN_HAND
        return BlockInteractionUtils.placeBlockIgnoringCrystals(blockPos, hitPosition, directionSnapshot, airPlaceFlag, hand, stack)
    }

    protected open fun onPlacementAttempt(blockPos: BlockPos, placementFailed: Boolean) {
    }

    protected open fun sortPlacementPositions(positions: MutableList<BlockPos>) {
        if (this.getPlacementBatchLimit() > 1) {
            positions.sortWith(Comparator.comparing { sortPos: BlockPos ->
                val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
                if (BlockInteractionUtils.findSupportDirection(sortPos, strictDirection) != null) -1 else 1
            })
        }

        if (!this.tickPlacementActive) {
            positions.sortWith(Comparator.comparing { sortPos: BlockPos ->
                val targetYaw = RotationManager.getRotationsToBlock(sortPos)[0]
                val packetYaw = ClientContext.playerStateTracker.packetRotations[0]
                Mth.degreesDifferenceAbs(targetYaw, packetYaw)
            })
        }
    }

    @EventHandler(priority = 100)
    private fun onPlacementTick(event: RotationRequestEvent) {
        this.runPlacementTick()
    }

    protected fun runPlacementTick() {
        this.lastPlacementHitPosition = null
        this.pendingAttackEntity = null
        this.tickPlacementActive = true
        this.runPlacementCycle()
        this.tickPlacementActive = false
    }

    @EventHandler
    private fun onAbstractClientPlayerTickEvent(event: AbstractClientPlayerTickEvent) {
        this.disableForJumpOrSleep()
        this.disableForHeightChange()
    }

    @EventHandler
    private fun onStepHeight(event: StepHeightEvent) {
        if (event.stage == EventStage.Post && event.height > MathConstants.ONE_HALF) {
            if (this.placementJumpSetting.value) {
                this.disable()
            }
        }
    }

    protected open fun filterPlacementPositions(blockSlot: InventoryUtils.SwapSlot, candidates: MutableList<BlockPos>) {
        this.deduplicatePlacementPositions(candidates)
        var placementBlock: Block? = null
        if (blockSlot.isFound()) {
            val placementStack = blockSlot.getStack()
            val item = placementStack.item
            if (item is BlockItem) {
                placementBlock = item.block
            }
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val eyeSnapshot = player.eyePosition
        val allowAttack = this.placementAttackSetting.value
        val maxPlaceDistanceSqr = 36.0

        candidates.removeIf { candidatePos ->
            val blockCenter = Vec3.atCenterOf(candidatePos)
            val squaredDistance = eyeSnapshot.distanceToSqr(blockCenter)
            !(BlockInteractionUtils.canPlaceBlock(candidatePos, placementBlock, true, allowAttack) && squaredDistance <= maxPlaceDistanceSqr)
        }
    }

    protected open fun placePositions(positions: MutableList<BlockPos>) {
        if (positions.isEmpty()) {
            return
        }

        this.attackCrystalsAtPositions(positions)
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val previousSlot = player.inventory.selectedSlot
        val blockSlot = this.findPlacementSwapSlot()
        var previousAngles: PlacementAngles? = null
        var retryPlacement = true
        val placedPositions = HashSet<BlockPos>()

        while (retryPlacement) {
            retryPlacement = false
            for (blockPos in positions) {
                if (ClientContext.swingManager.isCrystalPlacementLimited) {
                    break
                }
                if (!this.checkPlacementSwapSlot(blockSlot)) {
                    break
                }
                if (this.placementsThisCycle >= this.getPlacementBatchLimit()) {
                    break
                }
                if (placedPositions.contains(blockPos)) {
                    continue
                }

                val placementResult = this.placeBlock(blockSlot, blockPos)
                if (!placementResult.success) {
                    continue
                }
                ClientContext.swingManager.tryTrackCrystalPlacement(blockPos)
                placedPositions.add(blockPos)
                this.missingBlockAttempts = 0

                retryPlacement = antiCheatModule.isPaperPlacement
                if (blockPos != positions.last() && this.shouldSendBatchRotation()) {
                    val nextBlockPos = positions.first()
                    val currentAnglesSnapshot = placementResult.angles
                    if (currentAnglesSnapshot != null && this.sendBatchRotation(blockPos, nextBlockPos, currentAnglesSnapshot, previousAngles)) {
                        previousAngles = currentAnglesSnapshot
                    }
                }
            }
        }

        if (!this.batchCooldownActive) {
            this.batchCooldownActive = true
            ClientContext.taskScheduler.scheduleRotationTask({ this.batchCooldownActive = false }, 1)
        }

        if (this.inventoryPrepared) {
            InventoryUtils.swapBack(previousSlot, blockSlot)
            this.inventoryPrepared = false
        }
    }

    private fun placeBlock(blockSlot: InventoryUtils.SwapSlot, blockPos: BlockPos): PlacementResult {
        if (OffhandModule.isRecentSwapWindowActive()) {
            return PlacementResult.FAILED
        }

        val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
        val raytraceEnabled = this.placementRaytraceSetting.value

        this.placementDirection = BlockInteractionUtils.findSupportDirection(blockPos, strictDirection, raytraceEnabled)
        if (this.placementDirection == null) {
            if (!this.placementAirPlaceSetting.value) {
                return PlacementResult.FAILED
            }
        }

        val blockCenterSnapshot = Vec3.atCenterOf(blockPos)
        val rotationDirection = this.placementDirection
        val rotations = RotationManager.getRotationsToFace(blockCenterSnapshot, rotationDirection)

        var raytraceRequired = this.placementRaytraceSetting.value
        var hitPosition: Vec3? = null
        val dir = this.placementDirection
        val player = ClientAccess.collectiveMinecraft.player ?: return PlacementResult.FAILED

        if (dir != null) {
            val hitPointSamplerSnapshot = BlockSampleMode.X8
            val neighborPositionSnapshot = blockPos.relative(dir)
            val oppositeFace = dir.opposite

            val visibleHitPoints = hitPointSamplerSnapshot.sampleFace(neighborPositionSnapshot, oppositeFace).stream()
                .map { samplePoint ->
                    Vec3(
                        Math.round(samplePoint.x).toDouble(),
                        Math.round(samplePoint.y).toDouble(),
                        Math.round(samplePoint.z).toDouble()
                    )
                }
                .filter { samplePoint ->
                    val eyePosition = player.eyePosition
                    val neighborForVisibility = blockPos.relative(dir)
                    val oppositeForVisibility = dir.opposite
                    WorldUtils.canSee(eyePosition, samplePoint, neighborForVisibility, oppositeForVisibility)
                }

            val hitPointOptional = visibleHitPoints.min(Comparator.comparingDouble { samplePoint ->
                player.eyePosition.distanceToSqr(samplePoint)
            })
            hitPosition = hitPointOptional.orElse(null)
            if (hitPosition != null) {
                raytraceRequired = false
            }
        } else {
            raytraceRequired = raytraceRequired and !WorldUtils.canSeeBlock(blockPos)
        }

        var placementFailed = true
        if (!this.inventoryPrepared) {
            InventoryUtils.swapTo(blockSlot)
            this.inventoryPrepared = true
        }

        if (!raytraceRequired && this.tryPlaceAtHit(blockPos, hitPosition, blockSlot.getStack())) {
            if (RotationManager.isMovementSyncEnabled()) {
                if (antiCheatModule.is2b2tEnabled) {
                    val yawBeforeJitter = rotations[0].toDouble()
                    rotations[0] = (yawBeforeJitter + Math.random() * 0.009999999776482582).toFloat()
                    val pitchBeforeJitter = rotations[1].toDouble()
                    rotations[1] = (pitchBeforeJitter + Math.random() * 0.009999999776482582).toFloat()
                }
            }

            this.placementTimer.reset()

            if (this.placementRenderSetting.value) {
                ClientContext.featureBoxRenderer.addBox(this, blockPos)
            }

            this.lastPlacementHitPosition = Vec3.atCenterOf(blockPos)
            if (this.placementDirection != null) {
                val aimPointOffset = MathConstants.ONE_HALF
                val aimPointDirection = this.placementDirection!!
                this.lastPlacementHitPosition = this.lastPlacementHitPosition!!.relative(aimPointDirection, aimPointOffset.toDouble())
            }

            if (this.placementRotateSetting.value && this.tickPlacementActive) {
                val rotationPriority = this.getPlacementRotationPriority()
                ClientContext.rotationManager.requestRotation(rotations, rotationPriority)
            }

            this.placementsThisCycle++
            this.placedThisCycle = true
            placementFailed = false
        }

        this.onPlacementAttempt(blockPos, placementFailed)
        return PlacementResult(!placementFailed, PlacementAngles(rotations[0], rotations[1]))
    }

    abstract fun getPlacementPositions(): MutableList<BlockPos>

    open fun breakInstantlyMineableBlock(blockPos: BlockPos) {
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val blockState = level.getBlockState(blockPos)
        if (blockState.canBeReplaced()) {
            return
        }

        if (blockState.getDestroySpeed(level, blockPos) == 0.0f) {
            PacketSender.send(ServerboundPlayerActionPacket(Action.START_DESTROY_BLOCK, blockPos, Direction.DOWN))
            PacketSender.send(ServerboundPlayerActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, Direction.DOWN))
        }
    }

    protected open fun checkPlacementSwapSlot(blockSlot: InventoryUtils.SwapSlot): Boolean {
        var slotUsable = false
        if (blockSlot.isFound()) {
            val stack = blockSlot.getStack()
            slotUsable = !stack.isEmpty
        }
        if (!slotUsable) {
            this.missingBlockAttempts++
        }

        if (this.missingBlockAttempts >= 30 && !this.ignoreMissingBlocks) {
            this.missingBlockAttempts = 0
            val moduleName = this.name
            val nameComponentSnapshot = Component.literal(moduleName)
            val warningText = nameComponentSnapshot.append(" is out of blocks!")
            ChatUtils.printChat(warningText, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.HIGH)
            this.placementsThisCycle = 1337
            this.disable()
            slotUsable = false
        }

        return slotUsable
    }

    protected open fun attackCrystalsAtPositions(positions: MutableList<BlockPos>) {
        for (blockPos in positions) {
            if (!this.placementAttackSetting.value) {
                continue
            }
            val attackDelayMillis = ClientContext.crystalInteractionManager.attackDelayMillis
            if (!this.attackTimer.hasElapsed(attackDelayMillis.toLong())) {
                continue
            }

            val attackRange = ClientContext.crystalInteractionManager.minimumCrystalAge
            val entity = BlockInteractionUtils.attackCrystalAt(blockPos, attackRange)
            this.pendingAttackEntity = entity
            if (entity == null) {
                continue
            }

            if (RotationManager.isMovementSyncEnabled()) {
                val targetAngles = RotationManager.getRotationsToEntity(entity)
                val rotationPriority = this.getPlacementRotationPriority() + 1
                ClientContext.rotationManager.requestRotation(targetAngles, rotationPriority)
            }

            if (this.shouldDiscardAttackedCrystal()) {
                ClientAccess.collectiveMinecraft.execute {
                    entity.discard()
                }
            }

            this.attackTimer.reset()
            break
        }
    }

    protected fun ignoreMissingPlacementBlocks() {
        this.ignoreMissingBlocks = true
    }

    protected fun hasPlacedThisCycle(): Boolean {
        return this.placedThisCycle
    }

    protected open fun shouldHandleCrystalSpawn(): Boolean {
        return true
    }

    @JvmRecord
    data class PlacementAngles(
        val yaw: Float,
        val pitch: Float
    ) {
        private fun raycast(): HitResult {
            val player = ClientAccess.collectiveMinecraft.player ?: return BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO)
            val level = ClientAccess.collectiveMinecraft.level ?: return BlockHitResult.miss(player.eyePosition, Direction.UP, player.blockPosition())
            val pitchRadians = this.pitch * MathConstants.RADIANS_PER_DEGREE
            val negativeYawRadians = -this.yaw * MathConstants.RADIANS_PER_DEGREE
            val yawCos = Mth.cos(negativeYawRadians.toDouble())
            val yawSin = Mth.sin(negativeYawRadians.toDouble())
            val pitchCos = Mth.cos(pitchRadians.toDouble())
            val pitchSin = Mth.sin(pitchRadians.toDouble())
            val lookDirection = Vec3((yawSin * pitchCos).toDouble(), (-pitchSin).toDouble(), (yawCos * pitchCos).toDouble())

            val rayStart = player.eyePosition
            val scaledX = lookDirection.x * 5.0
            val scaledY = lookDirection.y * 5.0
            val scaledZ = lookDirection.z * 5.0
            val rayEnd = rayStart.add(scaledX, scaledY, scaledZ)
            val builder = BlockRaycastContext.Builder(player.eyePosition, rayEnd)

            return RaycastUtils.raycast(
                builder.withClassifier(BlockRaycastClassifier.solidPositions(ClientContext.worldStateManager.pendingPlacements.keys))
                    .build()
            )
        }

        fun intersectsBlock(blockPos: BlockPos): Boolean {
            val hit = this.raycast()
            if (hit.type != HitResult.Type.BLOCK) {
                return false
            }
            val hitBounds = AABB(hit.location.add(-0.01, -0.01, -0.01), hit.location.add(0.01, 0.01, 0.01))
            val blockBounds = AABB(blockPos)
            return hitBounds.intersects(blockBounds)
        }
    }

    @JvmRecord
    data class PlacementResult(
        val success: Boolean,
        val angles: PlacementAngles?
    ) {
        companion object {
            @JvmField
            val FAILED = PlacementResult(false, null)
        }
    }

    companion object {
        // Фолбэк-подбор блоков: взрывостойкий BlockItem. Anvil исключён (может разрушиться).
        private fun isFallbackBlock(stack: ItemStack, minDestroySpeed: Float, minResistance: Float): Boolean {
            val item = stack.item
            if (item !is BlockItem) {
                return false
            }
            val block = item.block
            if (block == Blocks.ANVIL) {
                return false
            }
            if (block.explosionResistance < minResistance) {
                return false
            }
            if (minDestroySpeed <= 0.0f) {
                return true
            }
            val destroySpeed = block.defaultBlockState().getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            return destroySpeed >= minDestroySpeed
        }

        lateinit var antiCheatModule: AntiCheatModule
        lateinit var chorusControlModule: ChorusControlModule
        lateinit var multiTaskModule: MultiTaskModule

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            antiCheatModule = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            chorusControlModule = modules.getModule(ChorusControlModule::class.java) as ChorusControlModule
            multiTaskModule = modules.getModule(MultiTaskModule::class.java) as MultiTaskModule
        }
    }
}
