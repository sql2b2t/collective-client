package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.MultiPlayerGameModeStartDestroyBlockEvent
import me.collective.client.render.WorldBoxRenderer
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.managers.RotationManager
import me.collective.client.utils.combat.ExtraBreakTarget
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.render.ColorUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.render.FontRenderer
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.world.NukerBlockQueue
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.NukerShapeChoice
import me.collective.client.settings.options.NukerSortChoice

import java.awt.Color
import java.util.ArrayList
import java.util.Comparator
import java.util.function.Predicate
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.ShulkerBoxBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

class NukerModule : Module("Nuker", "Breaks blocks nearby.", ModuleCategory.PLAYER) {
    internal val blocksSetting: Setting<Set<Block>>
    internal val modeSetting: Setting<NukerModeChoice>
    internal val shapeSetting: Setting<NukerShapeChoice>
    private val sortSetting: Setting<NukerSortChoice>
    private val bPTSetting: Setting<Int>
    private val delaySetting: Setting<Int>
    val rangeSetting: Setting<Float>
    private val wallRangeSetting: Setting<Float>
    val heightSetting: Setting<Int>
    private val strictDirectionSetting: Setting<Boolean>
    private val rotateSetting: Setting<Boolean>
    private val creativeSetting: Setting<Boolean>
    private val flattenSetting: Setting<Boolean>
    private val baritoneAreaSetting: Setting<Boolean>
    internal val playerProximitySetting: Setting<Boolean>
    private val colorSetting: Setting<Color>
    private val blockQueue: NukerBlockQueue
    private val recentBreaks: MutableList<RecentBreak>
    private val delayTimer: ElapsedTimer
    private var lastMinedPos: BlockPos? = null
    private var blocksMinedThisTick: Int = 0
    private var clickedBlock: Block? = null
    private var internalMining: Boolean = false
    private var instantBreak: Boolean = false

    fun getBlockQueue(): NukerBlockQueue {
        return this.blockQueue
    }

    private fun shouldUseInstantBlockFilter(): Boolean {
        val sm = speedMineModule ?: return false
        if (!sm.isToggled) {
            return false
        }

        val primaryTarget: BlockPos? = sm.miningPos ?: sm.rebreakPos
        val extraTarget: BlockPos? = sm.extraBreakTarget?.position

        if (!WorldUtils.canMineBlock(primaryTarget) && !WorldUtils.canMineBlock(extraTarget)) {
            return false
        }

        return primaryTarget == null || primaryTarget != extraTarget
    }

    private fun getMiningBatchLimit(): Int {
        if (speedMineModule!!.isToggled()) {
            val extraBreakEnabled = speedMineModule!!.extraBreakSetting.value
            val batchLimit: Int
            if (extraBreakEnabled) {
                batchLimit = 2
            } else {
                batchLimit = 1
            }

            return batchLimit
        } else {
            return this.bPTSetting.value
        }
    }

    private fun isZeroHardnessBlock(blockPos: BlockPos): Boolean {
        val blockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
        val blockLevel: ClientLevel = ClientAccess.collectiveMinecraft.level!!
        return blockState.getDestroySpeed(blockLevel, blockPos) == 0.0F
    }

    fun getClickedBlock(): Block? {
        return this.clickedBlock
    }

    override fun getInfo(): String? {
        val modeSettingRef = this.modeSetting
        return FontRenderer.capitalize(modeSettingRef.value)
    }

    override fun onDisable() {
        ClientAccess.collectiveMinecraft.options.keyAttack.setDown(false)
        this.clickedBlock = null
    }

    @EventHandler
    private fun onTick(event: RotationRequestEvent) {
        if (this.isWorldUnavailable()) {
            return
        }
        this.recentBreaks.removeIf { recentBreak -> System.currentTimeMillis() > recentBreak.expiresAtMillis }
        this.instantBreak = false
        this.lastMinedPos = null
        this.blocksMinedThisTick = 0
        var delayTimerWork = this.delayTimer
        val delayMillis: Long = this.delaySetting.value.toLong()
        if (!delayTimerWork.hasElapsed(delayMillis)) {
            return
        }
        if (speedMineModule!!.isToggled()) {
            val speedMineForDelaySnapshot = speedMineModule
            val speedMineDelayCapture: Long = this.delaySetting.value.toLong()
            if (!speedMineForDelaySnapshot!!.hasCrystalDelayElapsed(speedMineDelayCapture)) {
                return
            }
        }

        val candidates = this.shapeSetting.value.collectPositions(this) as MutableList<BlockPos>

        this.blockQueue.pruneInvalidPositions()
        if (this.shapeSetting.value != NukerShapeChoice.QUEUE) {
            candidates.sortWith(Comparator.comparingDouble { sortPos: BlockPos ->
                this.sortSetting.value.getSortKey(sortPos)
            })
        }

        val restrictToInstantBlocks = this.shouldUseInstantBlockFilter()
        for (candidatePos in candidates) {
            if (this.flattenSetting.value) {
                val candidateY: Double = candidatePos.y.toDouble()
                val playerForHeight = ClientAccess.collectiveMinecraft.player!!
                if (candidateY < playerForHeight.y) {
                    continue
                }
            }

            if (restrictToInstantBlocks) {
                if (!this.isZeroHardnessBlock(candidatePos)) {
                    continue
                }
            }

            if (!this.canMineBlock(candidatePos)) {
                continue
            }
            if (antiCheatModule!!.isPacketLimitReached()) {
                break
            }
            this.internalMining = true

            this.mineBlock(candidatePos)
            this.internalMining = false
            delayTimerWork = this.delayTimer

            delayTimerWork.reset()

            if (this.blocksMinedThisTick >= this.getMiningBatchLimit()) {
                return
            }
            if (this.blocksMinedThisTick > 0 && !this.instantBreak) {
                val creativeSetting = this.creativeSetting
                val creativeEnabled = creativeSetting.value
                if (!creativeEnabled) {
                    val speedMine = speedMineModule
                    if (!speedMine!!.isToggled()) {
                        return
                    }
                }
            }

        }
    }

    @EventHandler
    private fun onRenderQueue(event: WorldRenderEvent.Main) {
        if (this.shapeSetting.value != NukerShapeChoice.QUEUE) {
            return
        }

        val extraTask: ExtraBreakTarget? = speedMineModule!!.extraBreakTarget
        val queueSnapshot = this.blockQueue

        for (queuedPos in queueSnapshot.getPositions()) {
            if (speedMineModule!!.isToggled()) {
                val speedMineForPrimarySnapshot = speedMineModule

                if (queuedPos == speedMineForPrimarySnapshot!!.miningPos) {
                    continue
                }

                if (extraTask != null && queuedPos == extraTask.position) {
                    continue
                }
            }

            var queueColor = this.colorSetting.value
            if (queuedPos == this.lastMinedPos) {
                val selectedColor = ColorUtils.withAlpha(
                    queueColor, 1.0F
                )

                queueColor = ColorUtils.pulse(
                    selectedColor, queueColor, 1000.0, 0.0
                )
            }

            val poseStack = event.poseStack!!

            WorldBoxRenderer.outlineBox(
                poseStack, queuedPos, queueColor, 1.0F
            )
        }
    }

    @EventHandler(priority = 200)
    private fun onStartDestroyBlock(
        event: MultiPlayerGameModeStartDestroyBlockEvent
    ) {
        if (event.isCancelled || this.internalMining) {
            return
        }
        if (this.shapeSetting.value == NukerShapeChoice.QUEUE) {
            val queueSnapshot = this.blockQueue
            val clickedPos = event.pos
            queueSnapshot.enqueue(clickedPos)
            event.cancel()
        } else {
            if (this.modeSetting.value == NukerModeChoice.CLICK) {
                val clickedPosSnapshot = event.pos

                if (WorldUtils.canMineBlock(clickedPosSnapshot)) {
                    val clickedPosForBlock = event.pos

                    this.clickedBlock = WorldUtils.getBlock(clickedPosForBlock)
                }
            }
        }
    }

    private fun mineBlock(blockPos: BlockPos) {
        if (!WorldUtils.canMineBlock(blockPos)) {
            return
        }
        var miningDirection: Direction = Direction.UP
        if (this.strictDirectionSetting.value) {
            val availableFaces = BlockInteractionUtils.getStrictDirections(blockPos)
            if (availableFaces.isEmpty()) {
                return
            }

            miningDirection = availableFaces.first()
        }

        if (this.rotateSetting.value) {
            val rotationManagerSnapshot = ClientContext.rotationManager
            val centerSnapshot: Vec3 = blockPos.center
            val rotations = RotationManager.getRotationsToFace(centerSnapshot, miningDirection)

            rotationManagerSnapshot.requestRotation(rotations, 5)
        }

        this.blocksMinedThisTick++
        if (speedMineModule!!.isToggled()) {
            SpeedMineModule.automatedMiningStart = true
            ClientAccess.collectiveMinecraft.gameMode!!.startDestroyBlock(blockPos, miningDirection)
            SpeedMineModule.automatedMiningStart = false
            this.lastMinedPos = blockPos

            return
        }
        val selectedSlot = ClientAccess.collectiveMinecraft.player!!.inventory.getSelectedSlot()
        var bestSlot = -1

        var bestBreakProgress = -1.0
        val blockState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
        var hotbarSlot = 0
        while (true) {
            if (hotbarSlot >= 9) {
                break
            }

            val stack = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(hotbarSlot)
            val onGround = ClientAccess.collectiveMinecraft.player!!.onGround()
            val breakProgress = WorldUtils.getBreakProgress(
                stack, blockState, onGround
            )
            if (itemSaverModule!!.isToggled()) {
                if (!ItemSaverModule.isSafeToUse(stack)) {
                    hotbarSlot++
                    continue
                }
            }

            if (breakProgress > bestBreakProgress || breakProgress == bestBreakProgress && selectedSlot == hotbarSlot) {
                bestBreakProgress = breakProgress
                bestSlot = hotbarSlot
            }

            hotbarSlot++
        }

        if (bestSlot == -1) {
            // Ни один инструмент не подошёл (ItemSaver/сломанные инструменты) —
            // раньше код шёл дальше с bestSlot = -1 и падал на Inventory.getItem(-1).
            return
        }

        this.lastMinedPos = blockPos

        val playerForCreative = ClientAccess.collectiveMinecraft.player!!
        val survivalPlayer = !playerForCreative.isCreative
        if (this.creativeSetting.value) {
            if (antiCheatModule!!.is2b2tEnabled() && survivalPlayer) {
                InventoryUtils.selectHotbarSlotForPlacement(bestSlot)
            }

            val startPos = this.lastMinedPos
            val startAction = Action.START_DESTROY_BLOCK
            PacketSender.sendPlayerAction(startAction, startPos!!, miningDirection)

            if (bestBreakProgress < 1.0) {
                val stopPos = this.lastMinedPos
                val stopAction = Action.STOP_DESTROY_BLOCK
                PacketSender.sendPlayerAction(stopAction, stopPos!!, miningDirection)
            }

            val swingManager = ClientContext.swingManager
            val swingHand = InteractionHand.MAIN_HAND
            val swingType = SwingAction.INTERACT
            swingManager.swingPacket(swingHand, swingType)
            val recentBreaksSnapshot = this.recentBreaks
            recentBreaksSnapshot.add(RecentBreak(this.lastMinedPos!!, System.currentTimeMillis() + 1000L))
        } else {
            if (survivalPlayer) {
                InventoryUtils.selectHotbarSlotForPlacement(bestSlot)
            }

            val selectedTool = ClientAccess.collectiveMinecraft.player!!.inventory.getItem(bestSlot)
            val selectedState = ClientAccess.collectiveMinecraft.level!!.getBlockState(blockPos)
            val selectedOnGround = ClientAccess.collectiveMinecraft.player!!.onGround()
            this.instantBreak = WorldUtils.getBreakProgress(selectedTool, selectedState, selectedOnGround) >= 1.0
            if (this.instantBreak && RotationManager.isMovementSyncEnabled() && RotationManager.is2b2tMode()) {
                val startAction = Action.START_DESTROY_BLOCK
                PacketSender.sendPlayerAction(startAction, blockPos, miningDirection)
                val recentBreaksSnapshot = this.recentBreaks
                recentBreaksSnapshot.add(RecentBreak(this.lastMinedPos!!, System.currentTimeMillis() + 300L))
            } else {
                ClientAccess.collectiveMinecraft.gameMode!!.continueDestroyBlock(blockPos, miningDirection)
            }
            val playerForSwing = ClientAccess.collectiveMinecraft.player!!
            val mainHand = InteractionHand.MAIN_HAND
            playerForSwing.swing(mainHand)
        }
    }

    fun canMineBlock(blockPos: BlockPos): Boolean {
        if (speedMineModule!!.isToggled()) {
            if (speedMineModule!!.extraBreakTracker.isTracked(blockPos)) {
                return false
            }
        }

        if (this.recentBreaks.stream().anyMatch { recentBreak -> recentBreak.blockPos == blockPos }) {
            return false
        }

        val eyePosition = ClientAccess.collectiveMinecraft.player!!.eyePosition
        val squaredDistance = blockPos.distToCenterSqr(eyePosition)
        val distanceToEye = Math.sqrt(squaredDistance)
        val selectedRangeValue: Float
        if (WorldUtils.canSeeBlock(blockPos)) {
            selectedRangeValue = this.rangeSetting.value
        } else {
            selectedRangeValue = this.wallRangeSetting.value
        }

        var allowedRange = selectedRangeValue.toDouble()
        if (speedMineModule!!.isToggled()) {
            val speedMineRangeLimit = speedMineModule!!.rangeSetting.value.toDouble()
            allowedRange = Math.min(allowedRange, speedMineRangeLimit)
        }

        if (distanceToEye > allowedRange) {
            return false
        }

        if (WorldUtils.getBlock(blockPos) is ShulkerBoxBlock
        ) {
            if (ClientContext.worldStateManager.hasKnownPlacement(blockPos)) {
                return false
            }
        }

        if (this.baritoneAreaSetting.value &&
         ClientContext.baritoneBridge.isAvailable() &&
         !ClientContext.baritoneBridge.isInSelection(blockPos)
        ) {
            return false
        }

        val modeSnapshot = this.modeSetting.value

        val blockSnapshot = BlockInteractionUtils.getBlock(blockPos)
        return modeSnapshot.matchesBlock(blockPos, blockSnapshot, this)
    }

    init {
        val blockRegistry: DefaultedRegistry<Block> = BuiltInRegistries.BLOCK
        this.blocksSetting = this.add(RegistrySetSetting<Block>("Blocks", blockRegistry))
        val defaultMode = NukerModeChoice.SHULKERS
        this.modeSetting = this.add(EnumSetting<NukerModeChoice>("Mode", defaultMode))
        val defaultShape = NukerShapeChoice.SPHERE
        this.shapeSetting = this.add(EnumSetting<NukerShapeChoice>("Shape", defaultShape))
        val defaultSort = NukerSortChoice.CLOSEST
        this.sortSetting = this.add(EnumSetting<NukerSortChoice>("Sort", defaultSort))
        this.bPTSetting = this.add(NumberSetting<Int>("BPT", 1, 1, 10))
        this.delaySetting = this.add(NumberSetting<Int>("Delay", 50, 0, 500).withUnit("ms"))
        this.rangeSetting = this.add(NumberSetting<Float>("Range", 4.5F, 0.0F, 6.0F).withUnit("m"))
        this.wallRangeSetting = this.add(NumberSetting<Float>("WallRange", 4.5F, 0.0F, 6.0F).withUnit("m"))
        val heightVisibility = Predicate<Any> { ignoredValue -> this.shapeSetting.value == NukerShapeChoice.TUNNEL }
        val heightEditor = NumberSetting<Int>("Height", 0, 0, 5, heightVisibility)
        val heightAutoMode = SettingLabelCondition.MIN
        this.heightSetting = this.add(heightEditor.setBoundaryLabel("Auto", heightAutoMode))
        this.strictDirectionSetting = this.add(BooleanSetting("StrictDirection", false))
        this.rotateSetting = this.add(BooleanSetting("Rotate", false))
        this.creativeSetting = this.add(BooleanSetting("Creative", false))
        this.flattenSetting = this.add(BooleanSetting("Flatten", false))
        this.baritoneAreaSetting = this.add(BooleanSetting("BaritoneArea", false))
        val proximityVisibility = Predicate<Any> { ignoredValue -> this.modeSetting.value == NukerModeChoice.SHULKERS }
        this.playerProximitySetting = this.add(BooleanSetting("PlayerProximity", false, proximityVisibility))
        val colorVisibility = Predicate<Any> { ignoredValue -> this.shapeSetting.value == NukerShapeChoice.QUEUE }
        val defaultColor = Color(1019058687, true)
        this.colorSetting = this.add(ColorSetting("Color", defaultColor, colorVisibility))
        this.blockQueue = NukerBlockQueue(this)
        this.recentBreaks = ArrayList()
        this.delayTimer = ElapsedTimer()
        this.lastMinedPos = null
        var visibilitySetting: Setting<*> = this.sortSetting
        val visibilityPredicate = Predicate<Any> { ignored ->
            this.shapeSetting.value != NukerShapeChoice.QUEUE
        }

        visibilitySetting.addVisibilityPredicate(visibilityPredicate)
        visibilitySetting = this.bPTSetting
        val batchLimitVisibility = Predicate<Int> { ignored ->
            val visibilitySnapshot = speedMineModule
            !visibilitySnapshot!!.isToggled()
        }

        this.bPTSetting.addVisibilityPredicate(batchLimitVisibility)
        this.baritoneAreaSetting.hideWithoutBaritone()
    }

    private data class RecentBreak(
        val blockPos: BlockPos,
        val expiresAtMillis: Long
    )

    companion object {
        private var antiCheatModule: AntiCheatModule? = null
        private var itemSaverModule: ItemSaverModule? = null
        private var speedMineModule: SpeedMineModule? = null

        @JvmStatic
        fun bindReferencedModules(moduleRegistry: ModuleRegistry) {
            antiCheatModule = moduleRegistry.getModule(
                AntiCheatModule::class.java
            ) as AntiCheatModule?
            itemSaverModule = moduleRegistry.getModule(
                ItemSaverModule::class.java
            ) as ItemSaverModule?
            speedMineModule = moduleRegistry.getModule(
                SpeedMineModule::class.java
            ) as SpeedMineModule?
            NukerShapeChoice.bindModules(moduleRegistry)
            NukerBlockQueue.bindModules(moduleRegistry)
        }
    }
}
