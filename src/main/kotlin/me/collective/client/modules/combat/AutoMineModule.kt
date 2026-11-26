package me.collective.client.modules.combat

import net.minecraft.world.phys.Vec3
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.AutoMineCandidates
import me.collective.client.utils.inventory.AutoMineFeetProvider
import me.collective.client.utils.inventory.AutoMineHeadProvider
import me.collective.client.utils.inventory.AutoMineProvider
import me.collective.client.utils.inventory.AutoMineProviderBase
import me.collective.client.utils.inventory.AutoMineTerrainProvider
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import java.util.ArrayList
import kotlin.math.ceil

class AutoMineModule : Module(
    "AutoMine",
    "Mines out traps for your enemies and farms obsidian. \n${ChatFormatting.RED}Requires SpeedMine module enabled.",
    ModuleCategory.COMBAT,
    *createAliases()
) {
    val strictDirectionSetting: Setting<Boolean>
    val raytraceSetting: Setting<Boolean>
    val rotateSetting: Setting<Boolean>
    val ignoreNakedSetting: Setting<Boolean>
    val assistantSetting: Setting<Boolean>
    private val targetsSetting: Setting<Boolean>
    val burrowSetting: Setting<Boolean>
    val fishSetting: Setting<Boolean>
    val terrainSetting: Setting<Boolean>
    val enderChestsSetting: Setting<Boolean>
    val rangeSetting: Setting<Float>
    val headSetting: Setting<Boolean>
    val inCrawlSetting: Setting<Boolean>
    val trapOverrideSetting: Setting<Boolean>
    val feetSetting: Setting<Boolean>
    val cornersSetting: Setting<Boolean>
    val safeSetting: Setting<Boolean>
    val selfSetting: Setting<Boolean>
    val faceSetting: Setting<Boolean>
    val onlyCrawlSetting: Setting<Boolean>
    val tryCrawlSetting: Setting<Boolean>
    val instantSetting: Setting<Boolean>
    val downSetting: Setting<Boolean>
    val downPrioritySetting: Setting<Boolean>
    private val pendingBreaks: MutableList<ScheduledMiningBreak> = ArrayList()
    private val providerStages: List<AutoMineProvider>
    val targetSelector: AutoMineTargetSelector
    val miningTracker: AutoMineMiningTracker
    var isMiningActive: Boolean = false
        @JvmName("getMiningActiveProp") get
        @JvmName("setMiningActiveProp") set

    fun setMiningActive(active: Boolean) {
        this.isMiningActive = active
    }

    fun isMiningActive(): Boolean = this.isMiningActive

    init {
        this.strictDirectionSetting = this.add(BooleanSetting("StrictDirection", false))
        this.raytraceSetting = this.add(BooleanSetting("Raytrace", false))
        this.rotateSetting = this.add(BooleanSetting("Rotate", false))
        this.ignoreNakedSetting = this.add(BooleanSetting("IgnoreNaked", false))
        this.assistantSetting = this.add(BooleanSetting("Assistant", false))
        this.targetsSetting = this.add(BooleanSetting("Targets", false).markAsGroup().markVisibilityDependency())
        this.burrowSetting = this.add(BooleanSetting("Burrow", true).dependsOn(this.targetsSetting))
        this.fishSetting = this.add(BooleanSetting("Fish", false).dependsOn(this.targetsSetting))
        this.terrainSetting = this.add(BooleanSetting("Terrain", false).dependsOn(this.targetsSetting))
        this.enderChestsSetting = this.add(BooleanSetting("EnderChests", true).markVisibilityDependency().dependsOn(this.targetsSetting))

        this.rangeSetting = this.add(NumberSetting("Range", 2.5f, 0.1f, 6.0f).dependsOn(this.targetsSetting, this.enderChestsSetting))
        this.headSetting = this.add(BooleanSetting("Head", false).markVisibilityDependency().dependsOn(this.targetsSetting))
        this.inCrawlSetting = this.add(BooleanSetting("InCrawl", false).dependsOn(this.targetsSetting, this.headSetting))
        this.trapOverrideSetting = this.add(BooleanSetting("TrapOverride", false).dependsOn(this.targetsSetting, this.headSetting))
        this.feetSetting = this.add(BooleanSetting("Feet", true).markVisibilityDependency().dependsOn(this.targetsSetting))
        this.cornersSetting = this.add(BooleanSetting("Corners", true).dependsOn(this.targetsSetting, this.feetSetting))
        this.safeSetting = this.add(BooleanSetting("Safe", true).dependsOn(this.targetsSetting, this.feetSetting))
        this.selfSetting = this.add(BooleanSetting("Self", true).markVisibilityDependency().dependsOn(this.targetsSetting))
        this.faceSetting = this.add(BooleanSetting("Face", false).dependsOn(this.targetsSetting, this.selfSetting))
        this.onlyCrawlSetting = this.add(BooleanSetting("OnlyCrawl", false).dependsOn(this.targetsSetting, this.selfSetting))
        this.tryCrawlSetting = this.add(BooleanSetting("TryCrawl", false).dependsOn(this.targetsSetting, this.selfSetting))
        this.instantSetting = this.add(BooleanSetting("Instant", false).dependsOn(this.targetsSetting, this.selfSetting))
        this.downSetting = this.add(BooleanSetting("Down", true).dependsOn(this.targetsSetting, this.selfSetting))
        this.downPrioritySetting = this.add(BooleanSetting("DownPriority", false).dependsOn(this.targetsSetting, this.selfSetting, this.downSetting))

        val enderChestProvider = AutoMineEnderChestProvider(this)
        val burrowProvider = AutoMineBurrowProvider(this)
        val fishProvider = AutoMineFishProvider(this)
        val feetProvider = AutoMineFeetProvider(this)
        val selfProvider = AutoMineSelfProvider(this)
        val terrainProvider = AutoMineTerrainProvider(this)
        val headProvider = AutoMineHeadProvider(this)
        this.providerStages = listOf(
            enderChestProvider, burrowProvider, fishProvider, feetProvider, selfProvider, headProvider, terrainProvider
        )
        this.targetSelector = AutoMineTargetSelector(this)
        this.miningTracker = AutoMineMiningTracker()
    }

    private fun getBreakDelayValue(): Float {
        val noDelaySetting = speedMine.noBreakDelaySetting
        if (!noDelaySetting.value) {
            if (antiCheat.isMovementSyncEnabled()) {
                val timerSnapshot = speedMine.breakDelayTimer
                val normalizedElapsed = timerSnapshot.elapsedMillis().toFloat() / 300.0f
                val remaining = 1.0f - Mth.clamp(normalizedElapsed, 0.0f, 1.0f)
                val scaled = remaining * 6.0f
                return scaled + 1.5f
            }
        }
        return 0.0f
    }

    private fun updateMiningState(): Boolean {
        if (this.isMiningActive) {
            if (speedMine.miningPos == null) {
                this.isMiningActive = false
            } else {
                val miningPosition = speedMine.miningPos
                val blockPresent = miningPosition != null && !WorldUtils.isAir(miningPosition)
                this.isMiningActive = blockPresent
            }
        }
        return this.isMiningActive
    }

    fun getRange(): Float {
        return speedMine.rangeSetting.value
    }

    private fun selectMiningDirection(position: BlockPos): Direction? {
        return if (this.strictDirectionSetting.value) {
            val directions = BlockInteractionUtils.getStrictDirections(position)
            directions.firstOrNull()
        } else {
            Direction.UP
        }
    }

    private fun processPendingBreaks() {
        this.pendingBreaks.removeIf { pending ->
            if (pending.isReady) {
                this.startMining(pending.position, pending.direction, pending.miningFlag)
                true
            } else {
                false
            }
        }
    }

    private fun startMining(position: BlockPos, direction: Direction, miningFlag: Boolean) {
        if (this.rotateSetting.value) {
            var angles = RotationManager.getRotationsToPosition(Vec3.atCenterOf(position))
            if (this.raytraceSetting.value) {
                val hitPoint = WorldUtils.findVisiblePoint(position, BlockSampleMode.X8)
                if (hitPoint != null) {
                    angles = RotationManager.getRotationsToPosition(hitPoint)
                }
            }
            ClientContext.rotationManager.requestRotation(angles, 100)
        }

        SpeedMineModule.automatedMiningStart = miningFlag
        val gameMode = ClientAccess.collectiveMinecraft.gameMode
        gameMode?.startDestroyBlock(position, direction)
        SpeedMineModule.automatedMiningStart = false
    }

    private fun scheduleMining(position: BlockPos, direction: Direction, miningFlag: Boolean, delay: Float) {
        if (delay == 0.0f) {
            this.startMining(position, direction, miningFlag)
        } else {
            this.pendingBreaks.add(ScheduledMiningBreak(position, direction, miningFlag, this.getMiningDeadline(delay)))
        }
    }

    private fun getMiningDeadline(delay: Float): Long {
        val now = System.currentTimeMillis()
        // delay задан в тиках: длительность тика масштабируется под реальный TPS
        // (фиксированные 50 мс путали время и тики на плавающем TPS).
        val tickDurationMillis = (50.0f / Math.max(0.1f, ClientContext.serverStateManager.tickRateScale)).toLong()
        val delayMillis = (tickDurationMillis * delay).toLong()
        return now + delayMillis
    }

    @EventHandler(priority = 300)
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        if (!speedMine.isToggled) {
            return
        }

        if (multiTask.shouldPauseModule(this)) {
            return
        }
        if (this.pendingBreaks.isNotEmpty()) {
            this.processPendingBreaks()
            return
        }

        val candidates = AutoMineCandidates()
        for (provider in this.providerStages) {
            if (provider.isEnabled()) {
                provider.collectCandidates(candidates)
            }
        }

        if (this.updateMiningState()) {
            val priority = candidates.priority
            if (priority < 1500) {
                return
            }
        }

        if (candidates.isMiningCancellationRequested) {
            SpeedMineModule.clearRebreakAndReset(speedMine)
            return
        }

        if (candidates.primaryPosition == null) {
            if (candidates.secondaryPosition == null) {
                return
            }

            val fallbackPosition = candidates.secondaryPosition
            if (fallbackPosition == this.miningTracker.currentPosition) {
                return
            }

            candidates.swapPositions()
        }

        if (this.isExtraBreakEnabled()) {
            if (this.miningTracker.hasMiningActivity()) {
                val priority = candidates.priority
                if (priority < 1500) {
                    return
                }
            }
        }

        val primary = candidates.primaryPosition ?: return
        val secondary = candidates.secondaryPosition

        if (primary == speedMine.miningPos) {
            return
        }

        val direction = this.selectMiningDirection(primary) ?: return

        var primaryDelay = 0.0f

        if (this.isExtraBreakEnabled() && secondary != null && secondary != primary) {
            if (!this.miningTracker.hasMiningActivity()) {
                val rawSecondaryDelay = this.getBreakDelayValue()
                val secondaryDelay = ceil(rawSecondaryDelay.toDouble()).toFloat()
                if (secondaryDelay > 0.0f) {
                    SpeedMineModule.reset(speedMine)
                }

                this.scheduleMining(secondary, direction, false, secondaryDelay)
                primaryDelay += secondaryDelay + this.getBreakMode()
            }
        }

        this.scheduleMining(primary, direction, true, primaryDelay)
    }

    fun hasAssistantBreakProgress(position: BlockPos): Boolean {
        if (this.assistantSetting.value) {
            if (ClientContext.blockBreakTracker.getBreakProgress(position) > 0.0f) {
                return true
            }
        }
        return false
    }

    private fun getBreakMode(): Int {
        return if (antiCheat.isMovementSyncEnabled()) {
            val noBreakDelay = speedMine.noBreakDelaySetting.value
            if (noBreakDelay) 2 else 6
        } else {
            if (this.isExtraBreakEnabled()) 1 else 0
        }
    }

    fun isExtraBreakEnabled(): Boolean {
        return speedMine.extraBreakSetting.value
    }

    private data class ScheduledMiningBreak(
        val position: BlockPos,
        val direction: Direction,
        val miningFlag: Boolean,
        val deadlineMillis: Long
    ) {
        val isReady: Boolean
            get() = System.currentTimeMillis() >= this.deadlineMillis
    }

    companion object {
        lateinit var speedMine: SpeedMineModule
        lateinit var antiCheat: AntiCheatModule
        lateinit var multiTask: MultiTaskModule

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            speedMine = modules.getModule(SpeedMineModule::class.java) as SpeedMineModule
            antiCheat = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            multiTask = modules.getModule(MultiTaskModule::class.java) as MultiTaskModule
            AutoMineProviderBase.bindModules(modules)
            AutoMineFeetProvider.bindModules(modules)
            AutoMineHeadProvider.bindModules(modules)
            AutoMineTerrainProvider.bindModules(modules)
            AutoMineMiningTracker.bindModules(modules)
        }

        private fun createAliases(): Array<String> = arrayOf("combatmine", "autocity")
    }
}
