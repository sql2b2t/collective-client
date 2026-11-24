package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.access.PlayerInfoJoinTime
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.events.SelectedSlotChangeEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.managers.CrystalInteractionManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.MovementPredictionManager
import me.collective.client.managers.RotationManager
import me.collective.client.managers.ServerStateManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.misc.KillEffectsModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.render.LineMeshRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.settings.options.AutoCrystalInstantChoice
import me.collective.client.settings.options.AutoCrystalSequentialChoice
import me.collective.client.settings.options.AutoCrystalSilentChoice
import me.collective.client.settings.options.AutoCrystalWeaknessChoice
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.settings.options.ItemSwapMode
import me.collective.client.settings.options.SpeedMineRebreakChoice
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.LockUtils
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.combat.EndCrystalState
import me.collective.client.utils.combat.ExplosionDamageCache
import me.collective.client.utils.combat.ExtraBreakTarget
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.ArmorSlot
import me.collective.client.utils.inventory.EnchantmentUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.math.Vec3Utils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.network.ServerSideDeathState
import me.collective.client.utils.world.BlockBreakEntry
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import me.collective.mixin.ClientboundEntityEventPacketAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Comparator
import java.util.HashSet
import java.util.LinkedList
import java.util.Objects
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class AutoCrystalModule : Module("AutoCrystal", "Kills people with end crystals (if you're good).", ModuleCategory.COMBAT, "crystalaura", "ca", "ac") {
    @JvmField
    val placeSetting: Setting<Boolean> = add(BooleanSetting("Place", true).markVisibilityDependency())
    @JvmField
    val placeDelaySetting: Setting<Int> = add(NumberSetting("Delay", 0, 0, 1000).withUnit("ms").dependsOn(placeSetting))
    @JvmField
    val placeRangeSetting: Setting<Float> = add(NumberSetting("Range", 6.0f, 0.0f, 6.0f).withUnit("m").dependsOn(placeSetting))
    @JvmField
    val placeWallRangeSetting: Setting<Float> = add(NumberSetting("WallRange", 6.0f, 0.0f, 6.0f).withUnit("m").dependsOn(placeSetting))
    @JvmField
    val minPlaceDamageSetting: Setting<Float> = add(NumberSetting("MinDamage", 6.0f, 0.1f, 20.1f).withUnit("hp").setBoundaryLabel("Lethal", SettingLabelCondition.MAX).dependsOn(placeSetting))
    @JvmField
    val maxPlaceSelfDamageSetting: Setting<Float> = add(NumberSetting("MaxSelfDamage", 12.0f, 0.0f, 20.0f).withUnit("hp").dependsOn(placeSetting))
    @JvmField
    val placeDamageRatioSetting: Setting<Float> = add(NumberSetting("DamageRatio", 1.0f, 0.5f, 2.0f).withUnit("x").dependsOn(placeSetting))
    @JvmField
    val sequentialSetting: Setting<AutoCrystalSequentialChoice> = add(EnumSetting("Sequential", AutoCrystalSequentialChoice.STRICT).dependsOn(placeSetting))
    @JvmField
    val requireUpperAirSetting: Setting<Boolean> = add(BooleanSetting("1.12", false).dependsOn(placeSetting))
    @JvmField
    val strictDirectionSetting: Setting<Boolean> = add(BooleanSetting("StrictDirection", false).dependsOn(placeSetting))
    @JvmField
    val lowerHitBoxSetting: Setting<Boolean> = add(BooleanSetting("LowerHitBox", false).dependsOn(placeSetting))
    @JvmField
    val mineIgnoreSetting: Setting<Boolean> = add(BooleanSetting("MineIgnore", true).dependsOn(placeSetting))
    @JvmField
    val alwaysIgnoreMiningSetting: Setting<Boolean> = add(BooleanSetting("Always", false).dependsOn(placeSetting, mineIgnoreSetting))
    @JvmField
    val miningIgnoreThresholdSetting: Setting<Int> = add(NumberSetting("Threshold", 1, 1, 5).dependsOn(placeSetting, mineIgnoreSetting))
    @JvmField
    val breakSetting: Setting<Boolean> = add(BooleanSetting("Break", true).markVisibilityDependency())
    @JvmField
    val breakDelaySetting: Setting<Int> = add(NumberSetting("Delay", 0, 0, 1000).withUnit("ms").dependsOn(breakSetting))
    @JvmField
    val attackFactorSetting: Setting<Int> = add(NumberSetting("Factor", 1, 1, 5).dependsOn(breakSetting))
    @JvmField
    val breakRangeSetting: Setting<Float> = add(NumberSetting("Range", 6.0f, 0.0f, 6.0f).withUnit("m").dependsOn(breakSetting))
    @JvmField
    val breakWallRangeSetting: Setting<Float> = add(NumberSetting("WallRange", 6.0f, 0.0f, 6.0f).withUnit("m").dependsOn(breakSetting))
    @JvmField
    val minBreakDamageSetting: Setting<Float> = add(NumberSetting("MinDamage", 3.0f, 0.1f, 20.0f).withUnit("hp").dependsOn(breakSetting))
    @JvmField
    val maxBreakSelfDamageSetting: Setting<Float> = add(NumberSetting("MaxSelfDamage", 8.0f, 0.0f, 20.0f).withUnit("hp").dependsOn(breakSetting))
    @JvmField
    val antiSuicideSetting: Setting<Boolean> = add(BooleanSetting("AntiSuicide", true).dependsOn(breakSetting))
    @JvmField
    val instantSetting: Setting<AutoCrystalInstantChoice> = add(EnumSetting("Instant", AutoCrystalInstantChoice.ALWAYS).dependsOn(breakSetting))
    @JvmField
    val awaitSetting: Setting<Boolean> = add(BooleanSetting("Await", true) { instantSetting.value != AutoCrystalInstantChoice.NONE }.dependsOn(breakSetting))
    @JvmField
    val ticksExistedSetting: Setting<Int> = add(NumberSetting("TicksExisted", 2, 0, 40).dependsOn(breakSetting))
    @JvmField
    val weaknessSetting: Setting<AutoCrystalWeaknessChoice> = add(EnumSetting("Weakness", AutoCrystalWeaknessChoice.NONE).dependsOn(breakSetting))
    @JvmField
    val basePlaceSetting: Setting<Boolean> = add(BooleanSetting("BasePlace", false).markVisibilityDependency())
    @JvmField
    val basePlaceDelaySetting: Setting<Int> = add(NumberSetting("Delay", 500, 0, 1000).withUnit("ms").dependsOn(basePlaceSetting))
    @JvmField
    val basePlaceAirSetting: Setting<Boolean> = add(BooleanSetting("Air", false).dependsOn(basePlaceSetting))
    @JvmField
    val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", false).markVisibilityDependency())
    @JvmField
    val rotatePlaceSetting: Setting<Boolean> = add(BooleanSetting("Place", true).dependsOn(rotateSetting))
    @JvmField
    val rotateBreakSetting: Setting<Boolean> = add(BooleanSetting("Break", true).dependsOn(rotateSetting))
    @JvmField
    val facePlaceSetting: Setting<Boolean> = add(BooleanSetting("FacePlace", false).markVisibilityDependency())
    @JvmField
    val facePlaceHealthSetting: Setting<Float> = add(NumberSetting("Health", 4.0f, 0.0f, 36.0f).withUnit("hp").dependsOn(facePlaceSetting))
    @JvmField
    val facePlaceArmorThresholdSetting: Setting<Int> = add(NumberSetting("ArmorThreshold", 10, 0, 100).withUnit("%").dependsOn(facePlaceSetting))
    @JvmField
    val facePlaceShieldBreakerSetting: Setting<Boolean> = add(BooleanSetting("ShieldBreaker", false).dependsOn(facePlaceSetting))
    @JvmField
    val damageSyncSetting: Setting<Boolean> = add(BooleanSetting("DamageSync", false).dependsOn(facePlaceSetting))
    @JvmField
    val predictionSetting: Setting<Boolean> = add(BooleanSetting("Extrapolation", true).markVisibilityDependency())
    @JvmField
    val predictionTicksSetting: Setting<Int> = add(NumberSetting("Ticks", 4, 1, 8).dependsOn(predictionSetting))
    @JvmField
    val drawPredictionSetting: Setting<Boolean> = add(BooleanSetting("Draw", false).dependsOn(predictionSetting))
    @JvmField
    val renderSetting: Setting<Boolean> = add(BooleanSetting("Render", true).markVisibilityDependency())
    @JvmField
    val fillSetting: Setting<Color> = add(ColorSetting("Fill", Color(1136499199, true)).dependsOn(renderSetting))
    @JvmField
    val outlineSetting: Setting<Color> = add(ColorSetting("Outline", Color(-910321153, true)).dependsOn(renderSetting))
    @JvmField
    val lineWidthSetting: Setting<Float> = add(NumberSetting("LineWidth", 1.0f, 0.1f, 3.0f).dependsOn(renderSetting))
    @JvmField
    val renderDamageSetting: Setting<Boolean> = add(BooleanSetting("Damage", false).dependsOn(renderSetting))
    @JvmField
    val fadeSetting: Setting<Boolean> = add(BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(renderSetting))
    @JvmField
    val multiFadeSetting: Setting<Boolean> = add(BooleanSetting("MultiFade", false).dependsOn(fadeSetting, renderSetting))
    @JvmField
    val fadeTimeSetting: Setting<Float> = add(NumberSetting("FadeTime", 1.0f, 0.1f, 1.5f).withUnit("s").dependsOn(fadeSetting, renderSetting))
    @JvmField
    val pauseSetting: Setting<Boolean> = add(BooleanSetting("Pause", false).markAsGroup())
    @JvmField
    val pauseMiningSetting: Setting<Boolean> = add(BooleanSetting("Mining", false).dependsOn(pauseSetting))
    @JvmField
    val pauseEatingSetting: Setting<Boolean> = add(BooleanSetting("Eating", false).dependsOn(pauseSetting))
    @JvmField
    val pauseMendingSetting: Setting<Boolean> = add(BooleanSetting("Mending", false).dependsOn(pauseSetting))
    @JvmField
    val targetingSetting: Setting<Boolean> = add(BooleanSetting("Targeting", false).markAsGroup())
    @JvmField
    val targetRangeSetting: Setting<Float> = add(NumberSetting("TargetRange", 12.0f, 1.0f, 16.0f).withUnit("m").dependsOn(targetingSetting))
    @JvmField
    val crystalRangeSetting: Setting<Float> = add(NumberSetting("CrystalRange", 8.0f, 0.1f, 12.0f).withUnit("m").dependsOn(targetingSetting))
    @JvmField
    val antiFriendKillSetting: Setting<Boolean> = add(BooleanSetting("AntiFriendKill", false).dependsOn(targetingSetting))
    @JvmField
    val antiQuiverSetting: Setting<Boolean> = add(BooleanSetting("AntiQuiver", false).dependsOn(targetingSetting))
    @JvmField
    val ignoreNakedsSetting: Setting<Boolean> = add(BooleanSetting("IgnoreNakeds", false).dependsOn(targetingSetting))
    @JvmField
    val assumeBestArmorSetting: Setting<Boolean> = add(BooleanSetting("AssumeBestArmor", false).dependsOn(targetingSetting))
    @JvmField
    val bossesSetting: Setting<Boolean> = add(BooleanSetting("Bosses", false).dependsOn(targetingSetting))
    @JvmField
    val forceSuicideSetting: Setting<Boolean> = add(BooleanSetting("ForceSuicide", false).markVisibilityDependency().dependsOn(targetingSetting))
    @JvmField
    val forceSuicideNoArmorSetting: Setting<Boolean> = add(BooleanSetting("NoArmor", false).dependsOn(targetingSetting, forceSuicideSetting))
    @JvmField
    val assumeInvincibilitySetting: Setting<Boolean> = add(BooleanSetting("AssumeInvincibility", false).markVisibilityDependency().dependsOn(targetingSetting))
    @JvmField
    val assumeSelfInvincibilitySetting: Setting<Boolean> = add(BooleanSetting("Self", true).dependsOn(assumeInvincibilitySetting, targetingSetting))
    @JvmField
    val invincibilityTimeoutSetting: Setting<Float> = add(NumberSetting("Timeout", 0.0f, 0.0f, 4.0f).withUnit("s").dependsOn(assumeInvincibilitySetting, targetingSetting))
    @JvmField
    val miscSetting: Setting<Boolean> = add(BooleanSetting("Misc", false).markAsGroup())
    @JvmField
    val autoSwapSetting: Setting<ItemSwapMode> = add(EnumSetting("AutoSwap", ItemSwapMode.NONE).dependsOn(miscSetting))
    @JvmField
    val silentSetting: Setting<AutoCrystalSilentChoice> = add(EnumSetting("Silent", AutoCrystalSilentChoice.NORMAL) { autoSwapSetting.value == ItemSwapMode.SILENT }.dependsOn(miscSetting))
    @JvmField
    val swapBackSetting: Setting<Boolean> = add(BooleanSetting("SwapBack", true) { autoSwapSetting.value == ItemSwapMode.NORMAL }.dependsOn(miscSetting))
    @JvmField
    val strictTimingSetting: Setting<Boolean> = add(BooleanSetting("StrictTiming", false) { autoSwapSetting.value == ItemSwapMode.SILENT && silentSetting.value == AutoCrystalSilentChoice.ALTERNATIVE }.dependsOn(miscSetting))
    @JvmField
    val swapDelaySetting: Setting<Float> = add(NumberSetting("SwapDelay", 0.0f, 0.0f, 10.0f) { autoSwapSetting.value == ItemSwapMode.NORMAL }.dependsOn(miscSetting))
    @JvmField
    val swapPenaltySetting: Setting<Float> = add(NumberSetting("SwapPenalty", 0.0f, 0.0f, 10.0f).dependsOn(miscSetting))
    @JvmField
    val multipointSetting: Setting<Boolean> = add(BooleanSetting("Multipoint", false).dependsOn(miscSetting))

    private val targeting = AutoCrystalTargeting(this)
    private val damageRenderer = CrystalDamageRenderer(this)
    private val crystalHoldingTimer = ElapsedTimer()

    @JvmField
    val placementData = AtomicReference<CrystalPlacementResult?>()

    @JvmField
    val rotationTarget = AtomicReference<Vec3?>()

    @JvmField
    val placementGate = AtomicBoolean()

    private val rotationTimer = ElapsedTimer()
    private val placeTimer = ElapsedTimer()
    private val breakTimer = ElapsedTimer()
    private val swapDelayTimer = ElapsedTimer()
    private val swapPenaltyTimer = ElapsedTimer()
    private val sequentialPlacementTimer = ElapsedTimer()
    private val attackRetryTimer = ElapsedTimer()
    private val basePlaceTimer = ElapsedTimer()
    private val placementTimes = LinkedList<Long>()
    private val predictedAttackIds: MutableList<Int> = Collections.synchronizedList(ArrayList())
    private val ignoredBlocks: MutableSet<BlockPos> = HashSet()
    private var calculationFuture: CompletableFuture<CrystalPlacementResult?>? = null
    private val calculationEpoch = AtomicLong(0L)
    @Volatile
    private var scheduledEpoch = 0L

    @Volatile
    @JvmField
    var lastPlacedBase: BlockPos? = null

    @Volatile
    @JvmField
    var lastAttackedBase: BlockPos? = null

    @Volatile
    @JvmField
    var lastForcedBase: BlockPos? = null

    @Volatile
    @JvmField
    var spawnFollowupQueued = false

    @Volatile
    @JvmField
    var breakRotationRequested = false

    @Volatile
    @JvmField
    var obsidianDropNearTarget = false

    @Volatile
    @JvmField
    var quiverThreatActive = false

    @Volatile
    @JvmField
    var selectedDamage: Float = 0f

    private var lastAttackedCrystalId = -1
    private var attackRetries = 0
    private var ignoredSlotChanges = 0
    private var restoreSlot = -1
    private var previousPlacement: BlockPos? = null
    private var skip = false
    private var auraHasAttackTarget = false

    @Volatile
    @JvmField
    var target: LivingEntity? = null

    @Volatile
    @JvmField
    var previousTarget: LivingEntity? = null

    @Volatile
    private var eyePosition: Vec3? = null

    init {
        val boxManagerSnapshot = ClientContext.featureBoxRenderer
        val boxSettings = FeatureBoxRenderer.RenderConfiguration(
            this, fillSetting, outlineSetting, lineWidthSetting, fadeTimeSetting,
            multiFadeSetting, fadeSetting, 1000
        )
        boxManagerSnapshot.register(boxSettings)
        ClientAccess.eventBus.subscribe(damageRenderer)

        placeDelaySetting.setConfigName("PlaceDelay")
        placeRangeSetting.setConfigName("PlaceRange")
        placeWallRangeSetting.setConfigName("PlaceWallRange")
        minPlaceDamageSetting.setConfigName("MinPlaceDamage")
        maxPlaceSelfDamageSetting.setConfigName("MaxSelfPlace")
        breakDelaySetting.setConfigName("HitDelay")
        breakRangeSetting.setConfigName("BreakRange")
        breakWallRangeSetting.setConfigName("BreakWallRange")
        minBreakDamageSetting.setConfigName("MinBreakDamage")
        maxBreakSelfDamageSetting.setConfigName("MaxSelfHit")
        rotatePlaceSetting.setConfigName("PlaceRotate")
        rotateBreakSetting.setConfigName("BreakRotate")
        facePlaceHealthSetting.setConfigName("FacePlaceHealth")
        facePlaceArmorThresholdSetting.setConfigName("FacePlaceArmor")
        predictionSetting.setConfigName("ExtraTicks")
        invincibilityTimeoutSetting.setConfigName("InvincTimeout")
        basePlaceDelaySetting.setConfigName("BaseDelay")
        ticksExistedSetting.addVisibilityPredicate {
            instantSetting.value != AutoCrystalInstantChoice.ALWAYS
        }
    }

    override fun getInfo(): String? {
        val currentTarget = target
        if (currentTarget != null && !isInvalidTarget(currentTarget)) {
            val targetName = if (currentTarget is Player) {
                ClientContext.aliasRegistry.getPlayerAlias(currentTarget)
            } else {
                currentTarget.plainTextName
            }
            val placementsPerSecond = synchronized(placementTimes) { placementTimes.size }
            return String.format("%s, %.1f, %d", targetName, selectedDamage, placementsPerSecond)
        }
        return super.getInfo()
    }

    override fun onEnable() {
        updateEyePosition()
        lastAttackedCrystalId = -1
        previousTarget = target
        target = null
        lastPlacedBase = null

        placementData.set(null)
        calculationEpoch.incrementAndGet()
        val future = calculationFuture
        if (future != null) {
            try {
                future.complete(null)
            } catch (ignored: CancellationException) {
            }
        }

        swapDelayTimer.reset()
        basePlaceTimer.clear()
        restoreSlot = -1
    }

    override fun onDisable() {
        restoreSelectedSlot()
        calculationEpoch.incrementAndGet()
        placementData.set(null)
        placementGate.set(false)
        calculationFuture?.complete(null)
    }

    fun setRotationTarget(point: Vec3?) {
        if (point != null) {
            rotationTimer.reset()
            rotationTarget.set(point)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun calculatePlacement(targets: List<LivingEntity>): CrystalPlacementResult? {
        val scanPosition = MutableBlockPos()
        val selectedCrystalRange = if (shouldIgnoreMiningBlock()) {
            999.0f
        } else {
            breakRangeSetting.value
        }

        var ignoreMiningBlock = shouldIgnoreMiningBlock()
        val speedMineRef = speedMine
        if (speedMineRef != null && speedMineRef.rebreakSetting.value == SpeedMineRebreakChoice.INSTANT) {
            if (!speedMineRef.asyncSetting.value) {
                ignoreMiningBlock = false
            }
        }

        val currentPlacement = placementData.get()
        var currentTargetDamage: CrystalTargetDamage? = null
        var bestPlacement: CrystalPlacementResult? = null

        val basePlaceCanRetry = basePlaceSetting.value && canRetryBasePlacement()
        val configuredValidator = CrystalPlacementValidator()
            .requireUpperAir(requireUpperAirSetting.value)
            .lowerHitbox(lowerHitBoxSetting.value)
            .expandMovingPlayers(!RotationManager.isMovementSyncEnabled() && !shouldIgnoreMiningBlock())
            .ignoreBaseBlock(basePlaceCanRetry)
            .ignoreYoungItems(ignoreMiningBlock)

        val selectedPredictionTicks = if (predictionSetting.value) predictionTicksSetting.value else 0
        val placementValidator = configuredValidator
            .predictionTicks(selectedPredictionTicks)
            .ignoredCrystalRange(selectedCrystalRange)

        // Инварианты скана снимаются один раз: раньше selfBounds/selfPlayer
        // пересчитывались на каждую позицию (predicted box синхронизированно).
        val eye = eyePosition ?: return null
        val selfPlayer = ClientAccess.collectiveMinecraft.player ?: return null
        val selfBounds = getTargetBounds(selfPlayer)
        val emptyIgnoredPositions = emptyList<AABB>()

        if (currentPlacement != null) {
            val storedBaseSnapshot = currentPlacement.base
            if (placementValidator.canPlace(storedBaseSnapshot)) {
                val currentHasBase = WorldUtils.isBedrockOrObsidian(currentPlacement.base)
                currentTargetDamage = CrystalTargetDamage.calculateForBase(this, currentPlacement.base, currentHasBase, targets)
            }
        }

        val rangeVal = placeRangeSetting.value.toDouble()
        val minOffset = -Math.floor(rangeVal)
        val maxOffset = Math.ceil(rangeVal)

        var offsetX = minOffset
        while (offsetX < maxOffset) {
            var offsetY = minOffset
            while (offsetY < maxOffset) {
                var offsetZ = minOffset
                while (offsetZ < maxOffset) {
                    scanPosition.set(eye.x + offsetX, eye.y + offsetY, eye.z + offsetZ)

                    val hasCrystalBase = WorldUtils.isBedrockOrObsidian(scanPosition)

                    if (bestPlacement != null && bestPlacement.kind != CrystalPlacementKind.OBSIDIAN && !hasCrystalBase) {
                        offsetZ += 1.0
                        continue
                    }

                    if (!placementValidator.canPlace(scanPosition)) {
                        offsetZ += 1.0
                        continue
                    }

                    val deltaX = eye.x - (scanPosition.x + MathConstants.ONE_HALF)
                    val deltaY = eye.y - (scanPosition.y + MathConstants.ONE_HALF)
                    val deltaZ = eye.z - (scanPosition.z + MathConstants.ONE_HALF)
                    val distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                    if (distance > placeRangeSetting.value || distance > selectedCrystalRange) {
                        offsetZ += 1.0
                        continue
                    }

                    var visible = true
                    if (placeWallRangeSetting.value != placeRangeSetting.value) {
                        val samplingMode = if (multipointSetting.value) BlockSampleMode.X8 else BlockSampleMode.FAST
                        val visibilitySamples = samplingMode.sampleBlock(scanPosition) as List<Vec3>
                        visible = WorldUtils.canSeeAny(eye, visibilitySamples)
                    }

                    if (!visible && distance > placeWallRangeSetting.value) {
                        offsetZ += 1.0
                        continue
                    }

                    if (strictDirectionSetting.value && BlockInteractionUtils.getStrictDirections(scanPosition).isEmpty()) {
                        offsetZ += 1.0
                        continue
                    }

                    val explosionPosition = Vec3.upFromBottomCenterOf(scanPosition, 1.0)

                    val candidateTargetDamage = CrystalTargetDamage.calculateForBase(this, scanPosition, hasCrystalBase, targets)
                    if (candidateTargetDamage == null) {
                        offsetZ += 1.0
                        continue
                    }

                    // selfDamage — чистая функция и нигде не влияет на проверку
                    // candidateTargetDamage == null: считается после неё, чтобы
                    // не платить exposure за позиции без кандидата. Значения и
                    // порядок всех проверок не меняются.
                    val selfDamage = DamageUtils.getExplosionDamage(
                        explosionPosition,
                        selfPlayer,
                        selfBounds,
                        6.0,
                        true,
                        emptyIgnoredPositions,
                        scanPosition
                    )

                    val lethality = CrystalLethality.classifyLethality(candidateTargetDamage.target, candidateTargetDamage.damage)
                    if (CrystalDamagePolicy.PLACE.shouldRejectDamage(
                            candidateTargetDamage.target, selfDamage.toDouble(), candidateTargetDamage.damage, lethality
                        )
                    ) {
                        offsetZ += 1.0
                        continue
                    }

                    var placementKind = CrystalPlacementKind.NONE
                    if (currentTargetDamage != null && currentPlacement != null) {
                        if (currentPlacement.base != scanPosition) {
                            val roundedPreviousDamage = Math.round(currentTargetDamage.damage).toDouble()
                            if (roundedPreviousDamage >= candidateTargetDamage.damage) {
                                offsetZ += 1.0
                                continue
                            }
                        }
                    }

                    if (!visible && distance > placeWallRangeSetting.value) {
                        placementKind = CrystalPlacementKind.RAYTRACE
                    }

                    if (basePlaceSetting.value && !hasCrystalBase) {
                        val predictedBounds = getTargetBounds(candidateTargetDamage.target)
                        val targetBoundsUnion = predictedBounds.minmax(candidateTargetDamage.target.boundingBox)
                        val baseBounds = AABB(scanPosition)
                        if (targetBoundsUnion.intersects(baseBounds)) {
                            offsetZ += 1.0
                            continue
                        }

                        if (!BlockInteractionUtils.canPlaceAt(scanPosition, false)) {
                            offsetZ += 1.0
                            continue
                        }

                        if (!basePlaceAirSetting.value && BlockInteractionUtils.findSupportDirection(scanPosition, BlockInteractionUtils.isStrictDirectionEnabled()) == null) {
                            offsetZ += 1.0
                            continue
                        }

                        placementKind = CrystalPlacementKind.OBSIDIAN
                    }

                    if (target !is Player) {
                        placementKind = CrystalPlacementKind.NON_PLAYER
                    }

                    val newPlacement = CrystalPlacementResult(
                        scanPosition.immutable(), candidateTargetDamage.target, candidateTargetDamage.damage, placementKind
                    )
                    bestPlacement = newPlacement.selectBetterPlacement(bestPlacement)

                    offsetZ += 1.0
                }
                offsetY += 1.0
            }
            offsetX += 1.0
        }

        return bestPlacement
    }

    private fun isPlacementFaceVisible(base: BlockPos, direction: Direction): Boolean {
        if (placeRangeSetting.value == placeWallRangeSetting.value) {
            return true
        }

        val eye = eyePosition ?: return true
        val distance = eye.distanceTo(base.center)
        if (distance < placeRangeSetting.value) {
            return true
        }

        @Suppress("UNCHECKED_CAST")
        val placementVisibilitySamples = BlockSampleMode.X8.sampleFace(base, direction) as List<Vec3>
        return WorldUtils.canSeeAny(placementVisibilitySamples)
    }

    private fun runPlacementCycle() {
        if (!shouldIgnoreMiningBlock()) {
            placementGate.set(false)
        }

        if (!placeSetting.value || isSwapPenaltyActive() || placementGate.get()) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val crystalInOffhand = player.offhandItem.item == Items.END_CRYSTAL
        val holdingCrystal = player.isHolding(Items.END_CRYSTAL)
        val missingTarget = target == null

        if (damageSyncSetting.value || obsidianDropNearTarget || quiverThreatActive || missingTarget) {
            if (missingTarget || selectedDamage < minPlaceDamageSetting.value) {
                if (missingTarget || selectedDamage < 5.0f) {
                    if (!placeTimer.hasElapsed(getTickDurationMillis())) {
                        return
                    }
                }
            }
        }

        val selectedPlacement = placementData.get()
        if (selectedPlacement == null) {
            restoreSelectedSlot()
            return
        }

        val base = selectedPlacement.base
        val currentTarget = target
        if (base != null && currentTarget != null) {
            val minimumDamage = CrystalDamagePolicy.PLACE.getMinimumDamage(currentTarget)
            val explosionPosition = Vec3.upFromBottomCenterOf(base, 1.0)
            val predictedBounds = getTargetBounds(currentTarget)

            var targetDamage = DamageUtils.getExplosionDamage(
                explosionPosition,
                currentTarget,
                predictedBounds,
                6.0,
                true,
                ignoredBlocks,
                base
            ).toDouble()

            if (predictionSetting.value && targetDamage > 2.0 && targetDamage < minimumDamage) {
                val currentBounds = BoundingBoxUtils.getInterpolationBounds(currentTarget)
                val currentDamage = calculateExplosionDamage(base, currentTarget, currentBounds, true)
                targetDamage = Math.max(targetDamage, currentDamage)
            }

            if (targetDamage < minimumDamage) {
                restoreSelectedSlot()
                return
            }

            if (!WorldUtils.isBedrockOrObsidian(base)) {
                if (selectedPlacement.kind == CrystalPlacementKind.OBSIDIAN && basePlaceSetting.value) {
                    runForcedPlacement(base)
                }
                return
            }

            var originalSlot = -1
            var crystalSlot = InventoryUtils.findHotbarSlot(Items.END_CRYSTAL)

            if (ClientAccess.collectiveMinecraft.isSameThread) {
                val level = ClientAccess.collectiveMinecraft.level
                val firePosition = base.above()
                if (level != null && level.getBlockState(firePosition).block == Blocks.FIRE) {
                    val fireStartPacket = ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, base.above(), Direction.DOWN)
                    PacketSender.send(fireStartPacket)
                    val fireStopPacket = ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, base.above(), Direction.DOWN)
                    PacketSender.send(fireStopPacket)
                    return
                }
            }

            var alternativeSlotSearch = false
            var alternativeSwap = isSilentSwap() && silentSetting.value == AutoCrystalSilentChoice.ALTERNATIVE
            var miningSwapReset = false

            if (!holdingCrystal && shouldIgnoreMiningBlock()) {
                val speedMineRef = speedMine
                if (speedMineRef != null && speedMineRef.swapResetSetting.value && speedMineRef.alternativeSetting.value) {
                    alternativeSwap = true
                    miningSwapReset = true
                }
            }

            if (alternativeSwap && (!skip || !player.containerMenu.carried.isEmpty)) {
                skip = true
                return
            }

            if (alternativeSwap && crystalSlot == -1) {
                crystalSlot = InventoryUtils.findMenuSlot(Items.END_CRYSTAL)
            }

            if (autoSwapSetting.value != ItemSwapMode.NONE && !holdingCrystal) {
                if (!isSwapDelayActive() && crystalSlot != -1) {
                    originalSlot = player.inventory.selectedSlot
                    if (alternativeSwap) {
                        InventoryUtils.swapIntoSelectedSlot(crystalSlot)
                        alternativeSlotSearch = true
                        placementGate.set(miningSwapReset)
                    } else {
                        if (autoSwapSetting.value == ItemSwapMode.SILENT) {
                            speedMine?.crystalAttackPending = true
                            ignoreNextSlotChange()
                        }

                        if (shouldRestoreSlot()) {
                            restoreSlot = originalSlot
                        }

                        InventoryUtils.selectHotbarSlotForPlacement(crystalSlot)
                    }
                }
            }

            if (!holdingCrystal && originalSlot == -1) {
                return
            }

            placeCrystal(base, crystalInOffhand, targetDamage)
            if (!isSilentSwap() && !alternativeSlotSearch) {
                return
            }

            if (originalSlot != -1) {
                if (alternativeSlotSearch) {
                    InventoryUtils.swapIntoSelectedSlot(crystalSlot)
                } else if (autoSwapSetting.value == ItemSwapMode.SILENT) {
                    ignoreNextSlotChange()
                    InventoryUtils.selectHotbarSlotForPlacement(originalSlot)
                }
            }

            if (strictTimingSetting.value || miningSwapReset || skip) {
                skip = !skip
            }
        } else {
            restoreSelectedSlot()
        }
    }

    private fun sendFactorAttacks(crystalId: Int) {
        if (attackFactorSetting.isAtMinimum) {
            return
        }

        var attackIndex = 1
        while (attackIndex < attackFactorSetting.value) {
            val nextCrystalId = crystalId + attackIndex
            val predictedCrystalId = nextCrystalId - (if (RotationManager.isMovementSyncEnabled()) 1 else 0)
            PacketSender.sendAttack(predictedCrystalId)
            PacketSender.sendSwing(InteractionHand.MAIN_HAND)
            predictedAttackIds.add(predictedCrystalId)
            attackIndex++
        }
    }

    val isSelfInvulnerable: Boolean
        @JvmName("isSelfInvulnerableProp") get() {
            if (!assumeSelfInvincibilitySetting.value) {
                return false
            }
            val player = ClientAccess.collectiveMinecraft.player ?: return false
            val scaledPlayerAge = player.tickCount / 20.0f
            return scaledPlayerAge < getInvulnerabilityTimeout()
        }

    fun isSelfInvulnerable(): Boolean = isSelfInvulnerable

    private fun getInvulnerabilityTimeout(): Float {
        return if (!assumeInvincibilitySetting.value) -1.0f else invincibilityTimeoutSetting.value
    }

    private fun isPlayerInvulnerable(target: LivingEntity?): Boolean {
        if (isSelfInvulnerable || target !is Player) {
            return false
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val connectionSnapshot = player.connection
        val playerInfo = connectionSnapshot.getPlayerInfo(target.gameProfile.id) ?: return false

        val elapsedMillis = (System.currentTimeMillis() - (playerInfo as PlayerInfoJoinTime).`collective$getJoinTime`()).toFloat()
        val timeout = getInvulnerabilityTimeout()
        return elapsedMillis <= timeout * 1000.0f
    }

    private fun shouldPause(): Boolean {
        if (isWorldUnavailable) return true
        if (BlockInteractionUtils.isInteractionCooldownActive()) return true
        if (OffhandModule.isRecentSwapWindowActive()) return true
        if (SpeedMineModule.isSwapPending()) return true
        if (isInvalidTarget(target)) return true

        val player = ClientAccess.collectiveMinecraft.player ?: return true
        if (player.isSleeping) return true
        if (player.isUsingItem && pauseEatingSetting.value) return true
        if (pauseMendingSetting.value && autoExp?.isActive() == true) return true

        val gameMode = ClientAccess.collectiveMinecraft.gameMode
        var miningFlag = gameMode?.isDestroying == true
        if (!miningFlag) {
            val speedMineRef = speedMine
            if (speedMineRef != null && WorldUtils.canMineBlock(speedMineRef.miningPos)) {
                miningFlag = speedMineRef.isToggled
            }
        }

        if (miningFlag && pauseMiningSetting.value) {
            return true
        }

        if (!forceSuicideSetting.value) {
            return false
        }

        val anchor = anchorAura
        val anchorDmg = anchor?.targetDamage
        if (anchor != null && anchor.isToggled && anchor.placementData.get() != null && anchorDmg != null) {
            val placement = placementData.get()
            if (placement != null && anchorDmg.damage > placement.damage) {
                return true
            }
        }

        if (RotationManager.isMovementSyncEnabled() && !RotationManager.isSilentRotationMode() && player.isFallFlying) {
            return true
        }

        return player.isDeadOrDying
    }

    fun shouldIgnoreMiningBlock(): Boolean {
        var nearlyBrokenFlag = false
        val speedMineRef = speedMine ?: return false

        val thresholdTicks = miningIgnoreThresholdSetting.value
        val predictionTicks = 2.0f + 0.5f * thresholdTicks

        val miningProgress = speedMineRef.miningProgress
        val predictedProgress = miningProgress + speedMineRef.tickBreakProgress * predictionTicks
        if (predictedProgress >= speedMineRef.damageSetting.value) {
            val miningPos = speedMineRef.miningOrNearlyBrokenPos
            if (miningPos != null && WorldUtils.canMineBlock(miningPos)) {
                nearlyBrokenFlag = true
            }
        }

        if (mineIgnoreSetting.value) {
            if (nearlyBrokenFlag || !speedMineRef.hasCrystalDelayElapsed(100L)) {
                return true
            }
        }

        return false
    }

    private fun runBreakCycle() {
        if (!breakSetting.value) return
        if (!breakTimer.hasElapsed(breakDelaySetting.value.toLong())) return
        if (isSwapPenaltyActive()) return
        if (isPlayerInvulnerable(target)) return

        val awaitDelayMillis = ClientContext.serverStateManager.latencyMillis
        if (!sequentialPlacementTimer.hasElapsed(awaitDelayMillis.toLong())) {
            if (instantSetting.value != AutoCrystalInstantChoice.NONE && isAwaitEnabled()) {
                return
            }
        }

        val registeredSpeedMine = ClientContext.moduleRegistry.getModule(SpeedMineModule::class.java) as? SpeedMineModule
        var bestCrystal: Entity? = null
        var bestDamage = -1337.0
        var ignoreMiningBlock = shouldIgnoreMiningBlock()

        val speedMineRef = speedMine
        if (speedMineRef != null && !WorldUtils.canMineBlock(speedMineRef.miningOrNearlyBrokenPos)) {
            ignoreMiningBlock = false
        }

        val miningBlock = registeredSpeedMine?.miningOrNearlyBrokenPos?.takeIf { WorldUtils.canMineBlock(it) }
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val currentTarget = target ?: return
        val targetBounds = getTargetBounds(currentTarget)

        for (entity in level.entitiesForRendering()) {
            if (entity !is EndCrystal || !canAttackCrystal(entity)) {
                continue
            }

            val targetDamage: Double
            if (skip) {
                targetDamage = DamageUtils.getCrystalDamage(entity.position(), currentTarget, targetBounds).toDouble()
            } else {
                val crystalPosition = BlockPos.containing(entity.position())
                val crystalBase = crystalPosition.below()
                targetDamage = calculateExplosionDamage(crystalBase, currentTarget, targetBounds, false)
            }

            var recentPlacementIntersectionFlag = false
            val lastBase = lastPlacedBase
            if (lastBase != null && entity.blockPosition() != lastBase.above()) {
                val entityBounds = entity.boundingBox
                val placementIntersectionBox = AABB(lastBase.above()).expandTowards(0.0, 1.0, 0.0)
                if (entityBounds.intersects(placementIntersectionBox) && !placeTimer.hasElapsed(200L)) {
                    recentPlacementIntersectionFlag = true
                }
            }

            val selfDamage = DamageUtils.getCrystalDamage(entity.position(), player, player.boundingBox).toDouble()
            var lethality = CrystalLethality.classifyLethality(currentTarget, targetDamage)
            if (recentPlacementIntersectionFlag) {
                lethality = CrystalLethality.INTERSECTION
            }

            if (targetDamage <= selfDamage) {
                if (!forceSuicideSetting.value && !lethality.lethalDamage && !recentPlacementIntersectionFlag) {
                    continue
                }
            }

            if (!CrystalDamagePolicy.HIT.shouldRejectDamage(currentTarget, selfDamage, targetDamage, lethality)) {
                if (recentPlacementIntersectionFlag && bestCrystal == null && (!ignoreMiningBlock || miningBlock == null)) {
                    bestCrystal = entity
                }

                if (targetDamage >= CrystalDamagePolicy.HIT.getMinimumDamage(currentTarget) && targetDamage > bestDamage) {
                    bestDamage = targetDamage
                    bestCrystal = entity
                }
            }
        }

        if (bestCrystal is EndCrystal) {
            attackCrystal(bestCrystal)
        }
    }

    private fun updateEyePosition() {
        if (!isWorldUnavailable) {
            eyePosition = ClientAccess.collectiveMinecraft.player?.eyePosition
        }
    }

    val isFacePlaceEnabled: Boolean
        @JvmName("isFacePlaceEnabledProp") get() = facePlaceSetting.value && (!auraHasAttackTarget || !damageSyncSetting.value)

    fun isFacePlaceEnabled(): Boolean = isFacePlaceEnabled

    private fun canRetryBasePlacement(): Boolean {
        val forcedBase = lastForcedBase
        val currentTarget = target
        if (forcedBase != null && currentTarget != null) {
            if (!BlockInteractionUtils.canPlaceCrystal(forcedBase, requireUpperAirSetting.value, true, false, true, lowerHitBoxSetting.value, true)) {
                return true
            }

            val samplingMode = if (multipointSetting.value) BlockSampleMode.X8 else BlockSampleMode.FAST
            @Suppress("UNCHECKED_CAST")
            val samplePoints = samplingMode.sampleBlock(forcedBase) as List<Vec3>
            var visible = true

            if (placeWallRangeSetting.value != placeRangeSetting.value) {
                val eye = eyePosition
                if (eye != null) {
                    visible = WorldUtils.canSeeAny(eye, samplePoints)
                }
            }

            val player = ClientAccess.collectiveMinecraft.player ?: return true
            val distance = player.eyePosition.distanceTo(forcedBase.center)
            if (distance > placeRangeSetting.value) {
                return true
            }

            if (!visible && distance > placeWallRangeSetting.value) {
                return true
            }

            val explosionPosition = Vec3.upFromBottomCenterOf(forcedBase, 1.0)
            val targetBounds = getTargetBounds(currentTarget)
            val targetDamage = DamageUtils.getCrystalDamage(explosionPosition, currentTarget, targetBounds)
            if (targetDamage >= CrystalDamagePolicy.PLACE.getMinimumDamage(currentTarget)) {
                return false
            }
        }

        val retryDelayMillis = Math.max(getTickDurationMillis(), basePlaceDelaySetting.value.toLong())
        return basePlaceTimer.hasElapsed(retryDelayMillis)
    }

    private fun clearInvalidTarget() {
        if (isInvalidTarget(target)) {
            target = null
        }
    }

    private fun selectPlacementDirection(base: BlockPos): Direction? {
        val strictDirections = BlockInteractionUtils.getStrictDirections(base)
        val directions = if (strictDirections.isEmpty()) Direction.values().toList() else strictDirections

        val eye = eyePosition ?: return null
        return directions.stream()
            .filter { candidate -> isPlacementFaceVisible(base, candidate) }
            .min(Comparator.comparingDouble { faceDirection ->
                val faceCenter = base.center.relative(faceDirection, MathConstants.ONE_HALF)
                eye.distanceTo(faceCenter)
            })
            .orElse(null)
    }

    private fun runForcedPlacement(base: BlockPos) {
        if (!ClientAccess.collectiveMinecraft.isSameThread || !canRetryBasePlacement()) {
            return
        }

        var crystalSlot = InventoryUtils.findHotbarSlot(Items.END_CRYSTAL)
        if (autoSwapSetting.value == ItemSwapMode.SILENT && silentSetting.value == AutoCrystalSilentChoice.ALTERNATIVE) {
            crystalSlot = InventoryUtils.findMenuSlot(Items.END_CRYSTAL)
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (!player.offhandItem.`is`(Items.END_CRYSTAL) && crystalSlot == -1) {
            return
        }

        val direction = BlockInteractionUtils.findSupportDirection(
            base, BlockInteractionUtils.isStrictDirectionEnabled(), placeWallRangeSetting.isAtMinimum
        )
        var hitPoint = base.center
        if (direction != null) {
            val samplingMode = if (multipointSetting.value) BlockSampleMode.X8 else BlockSampleMode.FAST
            @Suppress("UNCHECKED_CAST")
            val sampleFacePoints = samplingMode.sampleFace(base, direction) as List<Vec3>
            val visibleHit = sampleFacePoints.stream()
                .filter { isPlacementHitVisible(it, base, direction) }
                .min(Comparator.comparingDouble { player.eyePosition.distanceTo(it) })
                .orElse(null)

            hitPoint = visibleHit ?: hitPoint.relative(direction, 0.5)
        }

        val originalSlot = player.inventory.selectedSlot
        var baseSlot = InventoryUtils.findHotbarSlot(Items.OBSIDIAN)
        var alternativeSwap = isSilentSwap() && silentSetting.value == AutoCrystalSilentChoice.ALTERNATIVE

        if (alternativeSwap && baseSlot == -1) {
            baseSlot = InventoryUtils.findMenuSlot(Items.OBSIDIAN)
        }

        if (baseSlot != -1) {
            if (direction == null && !basePlaceAirSetting.value) {
                return
            }

            if (BlockInteractionUtils.canPlaceAt(base, false)) {
                if (baseSlot != originalSlot) {
                    if (alternativeSwap) {
                        InventoryUtils.swapIntoSelectedSlot(baseSlot)
                    } else {
                        speedMine?.crystalAttackPending = true
                        InventoryUtils.selectHotbarSlotForPlacement(baseSlot)
                    }
                }

                BlockInteractionUtils.placeBlockIgnoringCrystals(
                    base, hitPoint, direction, basePlaceAirSetting.value, InteractionHand.MAIN_HAND
                )

                basePlaceTimer.reset()
                if (rotatePlaceSetting.value && rotateSetting.value) {
                    setRotationTarget(hitPoint)
                }

                lastForcedBase = base
                if (originalSlot != -1 && baseSlot != originalSlot) {
                    if (alternativeSwap) {
                        InventoryUtils.swapIntoSelectedSlot(baseSlot)
                    } else {
                        InventoryUtils.selectHotbarSlotForPlacement(originalSlot)
                    }
                }
            }
        }
    }

    private fun isSwapDelayActive(): Boolean {
        if (autoSwapSetting.value != ItemSwapMode.NORMAL) {
            return false
        }
        // SwapDelay задана в тиках: длительность тика масштабируется под реальный TPS
        // (фиксированные 50 мс путали время и тики на плавающем TPS).
        val delay = getTickDurationMillis() * swapDelaySetting.value.toLong()
        return !swapDelayTimer.hasElapsed(delay)
    }

    private fun throwUnboundArmor() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.containerMenu !== player.inventoryMenu) {
            return
        }
        for (armorSlot in ArmorSlot.values()) {
            val armorStack = player.inventory.getItem(armorSlot.inventoryIndex)
            if (!armorStack.isEmpty && !EnchantmentUtils.hasEnchantment(Enchantments.BINDING_CURSE, armorStack)) {
                InventoryUtils.clickSlot(armorSlot.containerSlot, 1, ClickType.THROW)
                break
            }
        }
    }

    private fun getTickDurationMillis(): Long {
        // Длительность одного тика = 50 мс (20 TPS), масштабируется под реальный TPS сервера.
        return (50.0f / Math.max(0.1f, ClientContext.serverStateManager.tickRateScale)).toLong()
    }

    fun ignoreNextSlotChange() {
        ignoredSlotChanges++
    }

    private fun isQuiverThreatPresent(): Boolean {
        if (quiverThreatActive) {
            return false
        }

        val currentTarget = target
        if (antiQuiverSetting.value && currentTarget is Player) {
            if (!currentTarget.isAlive) {
                return false
            }

            val level = ClientAccess.collectiveMinecraft.level ?: return false
            for (entity in level.entitiesForRendering()) {
                if (entity is Arrow && entity.y >= currentTarget.eyeY) {
                    val withinHorizontal = Vec3Utils.horizontalDistance(entity.position(), currentTarget.eyePosition) < 0.5
                    val withinVertical = abs(currentTarget.eyeY - entity.eyeY) < 4.0
                    if (withinHorizontal && withinVertical) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun shouldRestoreSlot(): Boolean {
        return autoSwapSetting.value == ItemSwapMode.NORMAL && swapBackSetting.value
    }

    private fun isSwapPenaltyActive(): Boolean {
        // SwapPenalty — в тиках, см. isSwapDelayActive: масштабируем под реальный TPS.
        val penalty = getTickDurationMillis() * swapPenaltySetting.value.toLong()
        return !swapPenaltyTimer.hasElapsed(penalty)
    }

    private fun updateIgnoredBlocks() {
        synchronized(ignoredBlocks) {
            ignoredBlocks.clear()

            if (mineIgnoreSetting.value) {
                val speedMineRef = speedMine
                if (shouldIgnoreMiningBlock() && speedMineRef != null) {
                    val miningPosition = speedMineRef.miningPos
                    if (miningPosition != null) {
                        ignoredBlocks.add(miningPosition)
                    }

                    val extraTarget = speedMineRef.extraBreakTarget
                    if (extraTarget != null) {
                        ignoredBlocks.add(extraTarget.position)
                    }
                }

                if (antiCheat?.assumeBlockBreakingSetting?.value == true) {
                    val player = ClientAccess.collectiveMinecraft.player
                    val playerPos = player?.blockPosition()
                    for (trackedBreak in ClientContext.blockBreakTracker.entries) {
                        if (trackedBreak.progress >= 0.8) {
                            if (playerPos == null || trackedBreak.position.distSqr(playerPos) <= 144.0) {
                                ignoredBlocks.add(trackedBreak.position)
                            }
                        }
                    }
                }
            }
        }
        // Содержимое ignoredBlocks — вход ключа кэша урона: мутация множества
        // поднимает epoch, чтобы запись, посчитанная на смеси старого/нового
        // множества, была отброшена.
        ExplosionDamageCache.bump()
    }

    fun getTargetBounds(target: LivingEntity): AABB {
        return if (predictionSetting.value && target is Player) {
            val predictionTicks = BoundingBoxUtils.getPredictionTicks(predictionTicksSetting)
            ClientContext.movementPredictionManager.getPredictedBoundingBox(target, predictionTicks)
        } else {
            BoundingBoxUtils.getInterpolationBounds(target)
        }
    }

    private fun prepareCalculation(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val holdingShulker = player.isHolding { ItemUtils.isShulkerBox(it.item) }

        if (!holdingShulker && crystalHoldingTimer.hasElapsed(50L)) {
            return false
        }

        if (holdingShulker) {
            crystalHoldingTimer.reset()
        }

        val future = calculationFuture
        if (future != null && !future.isDone) {
            completeCalculation()
        }

        return true
    }

    private fun restoreSelectedSlot() {
        if (!isWorldUnavailable && shouldRestoreSlot() && restoreSlot != -1) {
            InventoryUtils.selectHotbarSlotForPlacement(restoreSlot)
            restoreSlot = -1
        }
    }

    fun isAwaitEnabled(): Boolean = awaitSetting.value

    private fun isSilentSwap(): Boolean = autoSwapSetting.value == ItemSwapMode.SILENT

    private fun onCrystalSpawn(packet: ClientboundAddEntityPacket) {
        if (packet.type != EntityType.END_CRYSTAL || target == null) {
            return
        }

        val spawnPosition = Vec3(packet.x, packet.y, packet.z)
        val lastBase = lastPlacedBase
        if (lastBase != null && spawnPosition.distanceToSqr(lastBase.center) <= 9.0) {
            val spawnTime = System.currentTimeMillis()
            ClientAccess.collectiveMinecraft.execute {
                placementTimes.add(spawnTime)
            }
        }

        if (instantSetting.value == AutoCrystalInstantChoice.NONE || !breakSetting.value) {
            return
        }

        if (isPlayerInvulnerable(target)) {
            return
        }

        if (shouldIgnoreMiningBlock() && isAwaitEnabled()) {
            return
        }

        val eye = eyePosition ?: return
        var visible = true
        if (breakRangeSetting.value != breakWallRangeSetting.value) {
            visible = WorldUtils.canSee(eye, spawnPosition)
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val distance = player.eyePosition.distanceTo(spawnPosition)
        val selectedRange = if (visible) breakRangeSetting.value else breakWallRangeSetting.value
        if (distance > selectedRange) {
            return
        }

        val currentTarget = target ?: return
        val targetDamage = DamageUtils.getCrystalDamage(spawnPosition, currentTarget).toDouble()
        val selfDamage = DamageUtils.getCrystalDamage(spawnPosition, player).toDouble()

        val lethality = CrystalLethality.classifyLethality(currentTarget, targetDamage)
        if (targetDamage <= selfDamage && !forceSuicideSetting.value && !lethality.lethalDamage) {
            return
        }

        if (targetDamage < CrystalDamagePolicy.HIT.getMinimumDamage(currentTarget)) {
            return
        }

        if (CrystalDamagePolicy.HIT.shouldRejectDamage(currentTarget, selfDamage, targetDamage, lethality)) {
            return
        }

        val crystalPosition = BlockPos.containing(spawnPosition)
        if (instantSetting.value == AutoCrystalInstantChoice.STRICT && previousPlacement == crystalPosition) {
            return
        }

        if (rotateSetting.value && rotateBreakSetting.value) {
            setRotationTarget(spawnPosition)
        }

        if (ClientContext.crystalInteractionManager.attackEntity(packet.id, true)) {
            lastAttackedBase = crystalPosition
            previousPlacement = crystalPosition
            lastAttackedCrystalId = packet.id
            attackRetries = 0
            basePlaceTimer.reset()
        }

        var followupAllowed = player.isHolding(Items.END_CRYSTAL)
        if (!followupAllowed) {
            followupAllowed = EntityUtils.getPlayerHealthWithAbsorption() >= 8.0f
        }

        if (antiCheat?.is2b2tEnabled != true) {
            followupAllowed = true
        }

        if (sequentialSetting.value.isEnabled && !spawnFollowupQueued && followupAllowed) {
            sequentialPlacementTimer.reset()
            LockUtils.runWithSharedLock { runPlacementCycle() }
            if (sequentialSetting.value == AutoCrystalSequentialChoice.STRICT) {
                spawnFollowupQueued = true
            }
        }

        sendFactorAttacks(packet.id)
    }

    private fun placeCrystal(base: BlockPos?, offhand: Boolean, damage: Double) {
        if (base == null) return
        val direction = selectPlacementDirection(base) ?: return

        val lastAttacked = lastAttackedBase
        if (lastAttacked != null && base != lastAttacked && !sequentialSetting.value.isEnabled) {
            return
        }

        var hitPoint = base.center.relative(direction, MathConstants.ONE_HALF)
        val eye = eyePosition
        if (eye != null) {
            @Suppress("UNCHECKED_CAST")
            val cornerResultCandidates = MathUtils.faceCorners(base, direction).stream().filter { candidatePoint ->
                isPlacementHitVisible(candidatePoint, base, direction)
            }
            val visibleHit = cornerResultCandidates.min(Comparator.comparingDouble { eye.distanceTo(it) }).orElse(null)
            if (visibleHit != null) {
                hitPoint = visibleHit
            }
        }

        if (placeRangeSetting.value == placeWallRangeSetting.value) {
            hitPoint = if (direction.axis == Direction.Axis.Y) {
                base.center.relative(direction, MathConstants.ONE_HALF)
            } else {
                base.center.relative(direction, MathConstants.ONE_HALF).add(0.0, 0.5, 0.0)
            }
        }

        if (rotatePlaceSetting.value && rotateSetting.value && !breakRotationRequested) {
            setRotationTarget(hitPoint)
        }

        val hand = if (offhand) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
        val hit = BlockHitResult(hitPoint, direction, base, false)
        PacketSender.sendUseItemOn(hand, hit)
        ClientContext.swingManager.swing(hand, SwingAction.INTERACT)
        lastPlacedBase = base

        if (renderSetting.value) {
            ClientContext.featureBoxRenderer.addBox(this, base)
            damageRenderer.showDamage(base, damage)
        }

        placeTimer.reset()
    }

    private fun attackCrystal(crystal: EndCrystal) {
        if (crystal.id == lastAttackedCrystalId && attackRetryTimer.hasElapsed(500L) && crystal.isAlive && !crystal.isRemoved) {
            attackRetries++
        } else {
            attackRetries = 0
        }

        lastAttackedCrystalId = crystal.id
        if (attackRetries >= 5) {
            attackRetryTimer.reset()
        }

        if (ClientContext.crystalInteractionManager.attackEntity(crystal.id)) {
            (crystal as EndCrystalState).setClientAttacked(true)
            lastAttackedBase = crystal.blockPosition().below()
            sendFactorAttacks(crystal.id)

            if (rotateBreakSetting.value && rotateSetting.value) {
                val rotationPoint = BoundingBoxUtils.getLowerQuarterCenter(crystal.boundingBox)
                setRotationTarget(rotationPoint)
                breakRotationRequested = true
            }

            breakTimer.reset()
            basePlaceTimer.reset()
        }
    }

    private fun isPlacementHitVisible(point: Vec3, base: BlockPos, direction: Direction): Boolean {
        if (placeRangeSetting.value == placeWallRangeSetting.value) {
            return true
        }

        val eye = eyePosition ?: return true
        val distance = eye.distanceTo(point)
        if (distance >= placeWallRangeSetting.value) {
            return WorldUtils.canSee(eye, point, base, direction)
        }
        return true
    }

    fun calculateExplosionDamage(base: BlockPos, target: LivingEntity?, bounds: AABB, ignoreBaseBlock: Boolean): Double {
        if (target == null) return 0.0

        val speedMineRef = speedMine
        val trackedBlock = ClientContext.worldStateManager.unconfirmedBreak
        var miningMatchFlag = false

        if (trackedBlock != null && speedMineRef != null) {
            val miningPos = speedMineRef.miningOrNearlyBrokenPos
            if (miningPos != null && trackedBlock == miningPos) {
                miningMatchFlag = true
            }
        }

        var useIgnoredBlocks = ignoreBaseBlock || miningMatchFlag
        if (trackedBlock != null && speedMineRef != null) {
            useIgnoredBlocks = useIgnoredBlocks || trackedBlock == speedMineRef.miningOrNearlyBrokenPos
        }

        val explosionPosition = Vec3.upFromBottomCenterOf(base, 1.0)
        val damageIgnoredBlocks = if (useIgnoredBlocks) synchronized(ignoredBlocks) {
            if (ignoredBlocks.isEmpty()) emptyList() else ArrayList(ignoredBlocks)
        } else emptyList()
        val ignoredBase = if (ignoreBaseBlock) base.immutable() else null

        return DamageUtils.getExplosionDamage(explosionPosition, target, bounds, 6.0, true, damageIgnoredBlocks, ignoredBase).toDouble()
    }

    @EventHandler
    private fun onHeldSlotChange(event: SelectedSlotChangeEvent) {
        if (ignoredSlotChanges > 0) {
            ignoredSlotChanges--
        } else {
            swapPenaltyTimer.reset()
            swapDelayTimer.reset()
        }
    }

    @EventHandler
    private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
        forceSuicideSetting.value = false
    }

    fun isInvalidTarget(candidate: LivingEntity?): Boolean {
        if (candidate == null) return true

        val localPlayer = ClientAccess.collectiveMinecraft.player
        if (forceSuicideSetting.value) {
            return candidate !== localPlayer
        }

        val friends = ClientContext.socialRegistry
        if (candidate is Player) {
            if (friends.isFriendPlayer(candidate) || candidate.isCreative) {
                return true
            }
        }

        if (!candidate.isAlive || candidate.isDeadOrDying) {
            return true
        }

        val distance = if (localPlayer != null) candidate.distanceTo(localPlayer) else Float.MAX_VALUE
        if (distance > targetRangeSetting.value ||
            (candidate as ServerSideDeathState).isServerSideDead() ||
            localPlayer === candidate
        ) {
            return true
        }

        return candidate.isSpectator
    }

    fun canAttackCrystal(crystal: EndCrystal): Boolean {
        if (crystal.tickCount < ticksExistedSetting.value && !shouldIgnoreMiningBlock()) {
            return false
        }

        if (!crystal.isRemoved && target != null) {
            val player = ClientAccess.collectiveMinecraft.player ?: return false
            val distance = player.eyePosition.distanceTo(crystal.position())
            val breakRange = breakRangeSetting.value
            val wallRange = breakWallRangeSetting.value
            // Raycast видимости — только для спорного диапазона: при равных
            // настройках Range/WallRange он не нужен вообще.
            if (distance <= Math.min(breakRange, wallRange)) {
                return true
            }
            if (distance > Math.max(breakRange, wallRange)) {
                return false
            }
            val visible = player.hasLineOfSight(crystal)
            return distance <= (if (visible) breakRange else wallRange)
        }
        return false
    }

    @EventHandler
    private fun onRenderTargetPath(event: WorldRenderEvent.Main) {
        LineMeshRenderer.begin(event, 1.5f)

        if (drawPredictionSetting.value && predictionSetting.value && target is Player) {
            val pathTarget = target as Player
            val ticks = BoundingBoxUtils.getPredictionTicks(predictionTicksSetting)
            var predictionTick = 1

            while (predictionTick < ticks) {
                val previousBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(pathTarget, predictionTick - 1)
                val nextBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(pathTarget, predictionTick)
                LineMeshRenderer.addLine(
                    BoundingBoxUtils.getBottomCenter(previousBounds),
                    BoundingBoxUtils.getBottomCenter(nextBounds),
                    outlineSetting.value
                )
                predictionTick++
            }

            val endBounds = getTargetBounds(pathTarget)
            LineMeshRenderer.addLine(
                BoundingBoxUtils.getBottomCenter(endBounds),
                BoundingBoxUtils.getTopCenter(endBounds),
                outlineSetting.value
            )
        }

        LineMeshRenderer.draw()
    }

    @EventHandler
    private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
        if (isWorldUnavailable || prepareCalculation()) {
            return
        }

        val future = calculationFuture
        if (future != null && future.isDone) {
            try {
                val calculatedPlacement = future.get()
                if (calculationEpoch.get() != scheduledEpoch) {
                    calculationFuture = null
                } else if (calculatedPlacement == null) {
                    placementData.set(null)
                    selectedDamage = 0.0f
                } else {
                    placementData.set(calculatedPlacement)
                    selectedDamage = calculatedPlacement.damage.toFloat()
                    target = calculatedPlacement.target
                }
            } catch (failure: InterruptedException) {
                failure.printStackTrace()
            } catch (failure: ExecutionException) {
                failure.printStackTrace()
            }
        }

        val currentFuture = calculationFuture
        if (currentFuture == null || currentFuture.isDone || currentFuture.isCancelled) {
            clearInvalidTarget()
            val targets = targeting.snapshot()
            scheduledEpoch = calculationEpoch.incrementAndGet()
            val scheduledCalculation = CompletableFuture.supplyAsync({ calculatePlacement(targets) }, calculationExecutor)
            calculationFuture = scheduledCalculation.completeOnTimeout(null, 750L, TimeUnit.MILLISECONDS)
                .exceptionally { null }
        }

        synchronized(predictedAttackIds) {
            val level = ClientAccess.collectiveMinecraft.level
            if (level != null) {
                for (predictedId in predictedAttackIds) {
                    val entity = level.getEntity(predictedId)
                    if (entity is EndCrystal) {
                        (entity as EndCrystalState).setClientAttacked(true)
                    }
                }
                predictedAttackIds.removeIf { level.getEntity(it) != null }
            }
        }
    }

    private fun isAttackRetryWindowActive(retryMillis: Long): Boolean {
        val serverState = ClientContext.serverStateManager
        if (!serverState.setbackTimer.hasElapsed(200L)) {
            return true
        }
        val scaledMillis = (retryMillis.toFloat() / serverState.tickRateScale).toLong()
        return !attackRetryTimer.hasElapsed(scaledMillis)
    }

    @EventHandler
    private fun onPacketReceived(event: PacketEvent.Receive) {
        val packet = event.packet
        if (packet is ClientboundAddEntityPacket) {
            if (!isSwapPenaltyActive()) {
                // onCrystalSpawn запускает цикл установки (инвентарь, свапы, рендер-состояние) —
                // только с клиентского потока: событие приходит из netty (ConnectionMixin.channelRead0).
                ClientAccess.collectiveMinecraft.execute {
                    try {
                        if (!shouldPause()) {
                            onCrystalSpawn(packet)
                        }
                    } catch (ignored: Throwable) {
                    }
                }
            }
        }

        if (packet is ClientboundEntityEventPacket) {
            if (packet.eventId.toInt() == 3) {
                val currentTarget = target
                if (currentTarget != null) {
                    val eventEntityId = (packet as ClientboundEntityEventPacketAccessor).id
                    if (eventEntityId == currentTarget.id) {
                        (currentTarget as ServerSideDeathState).setServerSideDead(true)
                    }
                }
            }
        }

        if (packet is ClientboundPlayerCombatKillPacket) {
            forceSuicideSetting.value = false
        }
    }

    @EventHandler(priority = 200)
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        breakRotationRequested = false
        lastAttackedBase = null

        val level = ClientAccess.collectiveMinecraft.level ?: return
        val lastAttackedEntity = level.getEntity(lastAttackedCrystalId)
        if (lastAttackedEntity == null || !lastAttackedEntity.isAlive) {
            attackRetries = 0
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.tickCount % 4 == 0) {
            val now = System.currentTimeMillis()
            synchronized(placementTimes) {
                while (placementTimes.isNotEmpty()) {
                    val oldest = placementTimes.firstOrNull() ?: break
                    if (now - oldest <= 1000L) {
                        break
                    }
                    placementTimes.remove()
                }
            }
        }

        val killEffects = ClientContext.moduleRegistry.getModule(KillEffectsModule::class.java) as? KillEffectsModule
        if (killEffects != null && killEffects.isToggled) {
            val currentTarget = target
            if (currentTarget is Player && !isInvalidTarget(currentTarget)) {
                killEffects.trackTarget(currentTarget)
            }
        }

        if (!shouldPause()) {
            runBreakCycle()
            if (placeTimer.hasElapsed(placeDelaySetting.value.toLong())) {
                LockUtils.runWithSharedLock { runPlacementCycle() }
            }

            val rotationPoint = rotationTarget.get()
            if (rotateSetting.value && rotationPoint != null) {
                val movementInput = Vec2(
                    readForwardMovementInput(),
                    compareMovementKeys(
                        ClientAccess.collectiveMinecraft.options.keyLeft.isDown,
                        ClientAccess.collectiveMinecraft.options.keyRight.isDown
                    )
                )
                val retryWindowActive = isAttackRetryWindowActive(500L)
                var rotationFollowup = !(RotationManager.isSilentRotationMode() && !retryWindowActive && movementInput.lengthSquared() >= 0.01f)

                if (PlayerStateUtils.isFallFlying()) {
                    rotationFollowup = false
                }

                val rotations = RotationManager.getRotationsToPosition(rotationPoint)
                ClientContext.rotationManager.requestRotation(rotations, 100, rotationFollowup)

                if (RotationManager.isSilentRotationMode() && rotationFollowup && !retryWindowActive) {
                    ClientContext.rotationManager.requestRotation(rotations, 101, false)
                }

                if (rotationTimer.hasElapsed(400L)) {
                    placementData.lazySet(null)
                    rotationTarget.lazySet(null)
                }
            }
        }
    }

    @EventHandler
    private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
        if (event.stage != EventStage.Post) {
            targeting.refresh()
            updateEyePosition()
            updateIgnoredBlocks()

            auraHasAttackTarget = aura?.isTargetWithinAttackRange() == true
            spawnFollowupQueued = false
            quiverThreatActive = isQuiverThreatPresent()

            if (forceSuicideSetting.value && forceSuicideNoArmorSetting.value) {
                throwUnboundArmor()
            }

            if (obsidianDropNearTarget) {
                obsidianDropNearTarget = false
            } else {
                obsidianDropNearTarget = false
                val proximityTarget = targeting.findProximityTarget()
                if (proximityTarget != null && targeting.hasObsidianDropNear(proximityTarget)) {
                    obsidianDropNearTarget = true
                }
            }
        }
    }

    fun completeCalculation() {
        calculationEpoch.incrementAndGet()
        val pending = calculationFuture
        if (pending != null && !pending.isDone && !pending.isCancelled) {
            pending.complete(null)
        }
    }

    companion object {
        private val calculationExecutor = Executors.newFixedThreadPool(4)
        private var speedMine: SpeedMineModule? = null
        private var antiCheat: AntiCheatModule? = null
        private var anchorAura: AnchorAuraModule? = null
        private var aura: AuraModule? = null
        private var autoExp: AutoExpModule? = null

        init {
            speedMine = ClientContext.getModule(SpeedMineModule::class.java) as? SpeedMineModule
            antiCheat = ClientContext.getModule(AntiCheatModule::class.java) as? AntiCheatModule
            anchorAura = ClientContext.getModule(AnchorAuraModule::class.java) as? AnchorAuraModule
            aura = ClientContext.getModule(AuraModule::class.java) as? AuraModule
            autoExp = ClientContext.getModule(AutoExpModule::class.java) as? AutoExpModule
        }

        @JvmStatic
        fun bindReferencedModules(modules: ModuleRegistry) {
            speedMine = modules.getModule(SpeedMineModule::class.java) as? SpeedMineModule
            antiCheat = modules.getModule(AntiCheatModule::class.java) as? AntiCheatModule
            anchorAura = modules.getModule(AnchorAuraModule::class.java) as? AnchorAuraModule
            aura = modules.getModule(AuraModule::class.java) as? AuraModule
            autoExp = modules.getModule(AutoExpModule::class.java) as? AutoExpModule
        }

        private fun compareMovementKeys(positivePressed: Boolean, negativePressed: Boolean): Float {
            return if (positivePressed == negativePressed) 0.0f else if (positivePressed) 1f else -1f
        }

        private fun readForwardMovementInput(): Float {
            val forwardKey = ClientAccess.collectiveMinecraft.options.keyUp
            val forward = forwardKey.isDown
            val backKey = ClientAccess.collectiveMinecraft.options.keyDown
            return compareMovementKeys(forward, backKey.isDown)
        }
    }
}
