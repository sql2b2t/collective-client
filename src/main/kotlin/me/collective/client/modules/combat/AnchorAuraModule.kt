package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.settings.options.DimensionType
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateTracker
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.BlockPos.MutableBlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RespawnAnchorBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.Comparator
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import java.util.function.Supplier

class AnchorAuraModule : Module("AnchorAura", createDescription(), ModuleCategory.COMBAT) {
    private val delaySetting: Setting<Int> = add(NumberSetting("Delay", 50, 0, 1000).withUnit("ms"))
    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 5.0f, 0.0f, 6.0f).withUnit("m"))
    private val wallRangeSetting: Setting<Float> = add(NumberSetting("WallRange", 5.0f, 0.0f, 6.0f).withUnit("m"))
    val targetRangeSetting: Setting<Float> = add(NumberSetting("TargetRange", 6.0f, 1.0f, 16.0f).withUnit("m"))
    val minDamageSetting: Setting<Float> = add(NumberSetting("MinDamage", 6.0f, 0.1f, 20.0f).withUnit("hp"))
    private val maxSelfDamageSetting: Setting<Float> = add(NumberSetting("MaxSelfDamage", 12.0f, 0.0f, 20.0f).withUnit("hp"))
    private val antiSuicideSetting: Setting<Boolean> = add(BooleanSetting("AntiSuicide", true))
    private val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", false))
    private val airPlaceSetting: Setting<Boolean> = add(BooleanSetting("AirPlace", false))
    private val liquidPlaceSetting: Setting<Boolean> = add(BooleanSetting("LiquidPlace", false))
    private val inhibitSetting: Setting<Boolean> = add(BooleanSetting("Inhibit", true))
    private val sequentialSetting: Setting<Boolean> = add(BooleanSetting("Sequential", true))
    private val mineIgnoreSetting: Setting<Boolean> = add(BooleanSetting("MineIgnore", false))
    val renderSetting: Setting<Boolean> = add(BooleanSetting("Render", true).markVisibilityDependency())
    private val fillSetting: Setting<Color> = add(ColorSetting("Fill", Color(1136499199, true), Predicate { renderSetting.isExpanded }))
    private val outlineSetting: Setting<Color> = add(ColorSetting("Outline", Color(-910321153, true), Predicate { renderSetting.isExpanded }))
    private val fadeSetting: Setting<Boolean> = add(BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(renderSetting))
    private val fadeTimeSetting: Setting<Float> = add(NumberSetting("FadeTime", 1.0f, 0.1f, 1.0f).withUnit("s").dependsOn(renderSetting, fadeSetting))
    private val pauseSetting: Setting<Boolean> = add(BooleanSetting("Pause", false).markAsGroup())
    private val miningSetting: Setting<Boolean> = add(BooleanSetting("Mining", false).dependsOn(pauseSetting))
    private val eatingSetting: Setting<Boolean> = add(BooleanSetting("Eating", false).dependsOn(pauseSetting))
    private val healthSetting: Setting<Float> = add(NumberSetting("Health", 4.0f, 0.0f, 36.0f).withUnit("hp").dependsOn(pauseSetting))

    private var calculationFuture: CompletableFuture<AnchorPlacementResult?>? = null
    private val actionTimer: ElapsedTimer = ElapsedTimer()

    @JvmField
    val placementData: AtomicReference<AnchorPlacementResult?> = AtomicReference()

    @JvmField
    var targetDamage: AnchorTargetDamage? = null

    init {
        val renderer = ClientContext.featureBoxRenderer
        val renderConfiguration = FeatureBoxRenderer.RenderConfiguration(
            this, fillSetting, outlineSetting,
            { 1.0f },
            fadeTimeSetting, { false }, fadeSetting,
            450
        )
        renderer.register(renderConfiguration)
    }

    private fun selectAnchorHand(): InteractionHand? {
        var offhandFlag = false
        if (offhand.isToggled) {
            offhandFlag = offhand.itemSetting.value.getItem(false) == Items.RESPAWN_ANCHOR
        }

        val heldAnchorHand = InventoryUtils.getHandForItem(Items.RESPAWN_ANCHOR)
        if (offhandFlag && heldAnchorHand == null) {
            return null
        }
        return if (offhandFlag) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
    }

    override fun getInfo(): String? {
        val damage = targetDamage
        return if (damage != null) {
            val targetName = damage.target.name.string
            String.format("%s, %.1f", targetName, damage.damage)
        } else {
            null
        }
    }

    override fun onEnable() {
        targetDamage = null
        if (calculationFuture != null) {
            try {
                calculationFuture?.complete(null)
            } catch (ignored: CancellationException) {
            }
        }
    }

    override fun onToggle() {
        placementData.set(null)
    }

    private fun shouldPause(): Boolean {
        if (isWorldUnavailable) {
            return true
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return true
        if (player.isSleeping) {
            return true
        }

        if (player.isUsingItem && eatingSetting.value) {
            return true
        }

        val gameMode = ClientAccess.collectiveMinecraft.gameMode
        if (gameMode != null && gameMode.isDestroying && miningSetting.value) {
            return true
        }

        if (autoCrystal.isToggled) {
            val crystalPlacement = autoCrystal.placementData.get()
            val currentDamage = targetDamage
            if (crystalPlacement != null && currentDamage != null) {
                if (crystalPlacement.damage > currentDamage.damage) {
                    return true
                }
            }
        }

        val selfHealth = EntityUtils.getPlayerHealthWithAbsorption()
        return selfHealth < healthSetting.value
    }

    fun shouldIgnoreMiningBlock(): Boolean {
        var miningThresholdReached = false
        val miningDamage = speedMine.miningProgress
        val projectedDamage = miningDamage + (speedMine.tickBreakProgress * 2.0).toFloat()
        val threshold = speedMine.damageSetting.value
        if (projectedDamage >= threshold) {
            val miningPosition = speedMine.miningOrNearlyBrokenPos
            if (miningPosition != null && WorldUtils.canMineBlock(miningPosition)) {
                miningThresholdReached = true
            }
        }

        if (mineIgnoreSetting.value) {
            if (miningThresholdReached) {
                return true
            }
            if (!speedMine.hasCrystalDelayElapsed(100L)) {
                return true
            }
        }
        return false
    }

    private fun interactWithAnchor(
        position: BlockPos, direction: Direction, hitPoint: Vec3, hand: InteractionHand
    ) {
        val interactionScheduler: PlayerStateTracker = ClientContext.playerStateTracker
        val interactionCallback = Runnable {
            val hit = BlockHitResult(hitPoint, direction, position, false)
            PacketSender.sendUseItemOn(hand, hit)
            ClientContext.swingManager.swingPacket(hand, SwingAction.INTERACT)
        }
        interactionScheduler.runWithoutSneaking(interactionCallback)
    }

    private fun chargeAnchor(position: BlockPos, direction: Direction, originalSlot: Int) {
        val center = position.center
        val hitPoint = center.relative(direction, MathConstants.ONE_HALF)
        val glowstoneHand = InventoryUtils.getHandForItem(Items.GLOWSTONE)

        if (rotateSetting.value) {
            ClientContext.rotationManager.requestRotation(RotationManager.getRotationsToPosition(hitPoint), 100)
        }

        if (glowstoneHand != null) {
            // Взаимодействуем рукой, в которой лежит светокамень: раньше всегда
            // передавался MAIN_HAND и зарядка не работала при светокамне в offhand.
            interactWithAnchor(position, direction, hitPoint, glowstoneHand)
        } else {
            val glowstoneSlot = InventoryUtils.findHotbarSlot(Items.GLOWSTONE)
            if (glowstoneSlot != -1) {
                InventoryUtils.selectHotbarSlotForPlacement(glowstoneSlot)
                interactWithAnchor(position, direction, hitPoint, InteractionHand.MAIN_HAND)
                InventoryUtils.selectHotbarSlotForPlacement(originalSlot)
            }
        }

        actionTimer.reset()
    }

    private fun shouldRejectDamage(position: BlockPos, targetDamage: AnchorTargetDamage?): Boolean {
        if (targetDamage == null) {
            return true
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val miningBlock = if (shouldIgnoreMiningBlock()) speedMine.miningPos else null
        val existingAnchor = isAnchorBlock(position)
        val selfCenter = Vec3.atCenterOf(position)
        val selfBounds = player.boundingBox
        val selfIgnored = if (existingAnchor) position else miningBlock

        val selfDamage = DamageUtils.getExplosionDamage(
            selfCenter, player, selfBounds, 5.0, true, selfIgnored, null
        ).toDouble()

        val storedDamage = targetDamage.damage
        val lethalTargetPlayer = targetDamage.target
        val lethalTarget = storedDamage >= EntityUtils.getHealthWithAbsorption(lethalTargetPlayer)

        var selfRejectionFlag = false
        if (!lethalTarget) {
            selfRejectionFlag = selfDamage >= maxSelfDamageSetting.value
        }

        if (!selfRejectionFlag && selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
            selfRejectionFlag = antiSuicideSetting.value
        }

        if (existingAnchor && !selfRejectionFlag) {
            return false
        }

        // Пересчёт урона по цели — только когда он влияет на решение:
        // для существующего якоря с безопасным self-уроном ответ уже известен.
        val targetCenter = Vec3.atCenterOf(position)
        val damageTarget = targetDamage.target
        val targetBounds = damageTarget.boundingBox
        val targetIgnored = if (existingAnchor) position else miningBlock

        val recomputedTargetDamage = DamageUtils.getExplosionDamage(
            targetCenter, damageTarget, targetBounds, 5.0, true, targetIgnored, null
        ).toDouble()

        return minDamageSetting.value > recomputedTargetDamage
            || recomputedTargetDamage < MathConstants.ONE_HALF
            || selfRejectionFlag
    }

    fun isManagedAnchor(position: BlockPos): Boolean {
        if (isToggled && !shouldPause()) {
            val dimension: DimensionType = ClientContext.worldStateManager.dimension
            if (!dimension.isNether) {
                if (placementData.get() == null) {
                    return false
                }
                return isAnchorBlock(position)
            }
        }
        return false
    }

    fun isAnchorBlock(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(position)
        return state.`is`(Blocks.RESPAWN_ANCHOR)
    }

    private fun selectSupportDirection(position: BlockPos): Direction? {
        val directions = BlockInteractionUtils.getStrictDirections(position)
        var direction: Direction? = if (BlockInteractionUtils.isStrictDirectionEnabled()) null else Direction.DOWN
        if (directions.isNotEmpty()) {
            direction = directions[0]
        }
        return direction
    }

    private fun selectHitPoint(position: BlockPos): Vec3 {
        val player = ClientAccess.collectiveMinecraft.player
        val fallbackCenter = position.center
        if (player == null) return fallbackCenter

        val samples = BlockSampleMode.X8.sampleBlock(position)
        val eyePos = player.eyePosition
        return samples.stream()
            .filter { sample ->
                if (!WorldUtils.canSeeFromEyes(sample)) {
                    val sampleDistance = eyePos.distanceTo(sample)
                    sampleDistance <= this.wallRangeSetting.value
                } else {
                    true
                }
            }
            .min(Comparator.comparingDouble { sample -> eyePos.distanceTo(sample) })
            .orElse(fallbackCenter)
    }

    private fun isInvalidPlacement(position: BlockPos): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val level = ClientAccess.collectiveMinecraft.level ?: return true
        val samples = BlockSampleMode.X8.sampleBlock(position)
        val placementRange = if (!WorldUtils.canSeeAny(samples)) {
            wallRangeSetting.value.toDouble()
        } else {
            rangeSetting.value.toDouble()
        }

        val damage = AnchorTargetDamage.calculateForBase(this, position)
        if (shouldRejectDamage(position, damage)) {
            return true
        }

        val eyeSnapshot = player.eyePosition
        val center = position.center
        if (eyeSnapshot.distanceTo(center) > placementRange) {
            return true
        }

        val state = level.getBlockState(position)
        if (state.isAir) {
            var hasSupport = false
            if (selectSupportDirection(position) != null) {
                val directionMode = BlockInteractionUtils.isStrictDirectionEnabled()
                val zeroWallRange = wallRangeSetting.value <= 0.0f
                hasSupport = BlockInteractionUtils.findSupportDirection(position, directionMode, zeroWallRange) != null
            }

            if (!hasSupport && !airPlaceSetting.value) {
                return true
            }
        }

        return !BlockInteractionUtils.canPlaceBlock(position, Blocks.RESPAWN_ANCHOR, true, false) && !isAnchorBlock(position)
    }

    private fun placeAnchor(
        position: BlockPos, direction: Direction, hand: InteractionHand
    ) {
        if (!isAnchorBlock(position)) {
            val player = ClientAccess.collectiveMinecraft.player ?: return
            val originalSlot = player.inventory.selected
            val anchorSlot = InventoryUtils.findHotbarSlot(Items.RESPAWN_ANCHOR)
            val offhandSwap = BlockInteractionUtils.findSupportDirection(position) == null
                && antiCheat.is2b2tEnabled && airPlaceSetting.value

            if (anchorSlot != -1 || hand != InteractionHand.MAIN_HAND) {
                if (rotateSetting.value) {
                    val center = position.center
                    ClientContext.rotationManager.requestRotation(
                        RotationManager.getRotationsToFace(center, direction), 100
                    )
                }

                val mainStack = player.mainHandItem
                val selectedSwap = !mainStack.`is`(Items.RESPAWN_ANCHOR)
                if (selectedSwap) {
                    InventoryUtils.selectHotbarSlotForPlacement(anchorSlot)
                }

                var placementHand = hand
                if (offhandSwap) {
                    placementHand = PlayerStateUtils.oppositeHand(placementHand)
                    PacketSender.sendSwapOffhand()
                }

                val relativeHitPosition = position.relative(direction)
                val hitPoint = selectHitPoint(relativeHitPosition)

                val airPlacement = airPlaceSetting.value
                if (BlockInteractionUtils.sendPlacement(
                        position, hitPoint, direction, airPlacement, placementHand
                    )
                ) {
                    if (renderSetting.value) {
                        ClientContext.featureBoxRenderer.addBox(this, position)
                    }
                }

                if (offhandSwap) {
                    PacketSender.sendSwapOffhand()
                }

                if (selectedSwap) {
                    InventoryUtils.selectHotbarSlotForPlacement(originalSlot)
                }
            }
        }
    }

    private fun calculatePlacement(): AnchorPlacementResult? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val scanPosition = MutableBlockPos()
        var bestPlacement: AnchorPlacementResult? = null
        val range = rangeSetting.value
        val floorRange = -Math.floor(range.toDouble())
        val ceilRange = Math.ceil(range.toDouble())

        // Снимок позиции/глаз на весь скан: player.eyePosition аллоцировался
        // на каждую из ≈1000 позиций прохода.
        val playerX = player.x
        val playerEyeY = player.eyeY
        val playerZ = player.z
        val eyePosition = player.eyePosition

        var offsetX = floorRange
        while (offsetX < ceilRange) {
            var offsetY = floorRange
            while (offsetY < ceilRange) {
                var offsetZ = floorRange
                while (offsetZ < ceilRange) {
                    val scanX = playerX + offsetX
                    val scanY = playerEyeY + offsetY
                    val scanZ = playerZ + offsetZ
                    scanPosition.set(scanX, scanY, scanZ)

                    if (BlockInteractionUtils.isOutsidePlacementBounds(scanPosition)) {
                        offsetZ += 1.0
                        continue
                    }

                    var miningBlockIgnored = false
                    if (shouldIgnoreMiningBlock()) {
                        if (scanPosition == speedMine.miningPos) {
                            miningBlockIgnored = true
                        }
                    }

                    if (WorldUtils.isPlacementOccupied(scanPosition) && !miningBlockIgnored) {
                        offsetZ += 1.0
                        continue
                    }

                    val bounds = AABB(scanPosition)
                    if (BlockInteractionUtils.hasBlockingEntity(bounds, true, true)) {
                        if (!isAnchorBlock(scanPosition)) {
                            offsetZ += 1.0
                            continue
                        }
                    }

                    val samples = BlockSampleMode.X8.sampleBlock(scanPosition)
                    val visible = WorldUtils.canSeeAny(samples)

                    val center = scanPosition.center
                    val distance = eyePosition.distanceTo(center)
                    if (!visible) {
                        val visibilityWallRange = wallRangeSetting.value
                        if (distance > visibilityWallRange) {
                            offsetZ += 1.0
                            continue
                        }
                    }

                    val placementRange = rangeSetting.value
                    if (distance > placementRange) {
                        offsetZ += 1.0
                        continue
                    }

                    val directionMode = BlockInteractionUtils.isStrictDirectionEnabled()
                    val zeroWallRange = wallRangeSetting.value <= 0.0f
                    var direction = BlockInteractionUtils.findSupportDirection(
                        scanPosition, directionMode, zeroWallRange
                    )

                    val damage = AnchorTargetDamage.calculateForBase(this, scanPosition)
                    if (direction == null && airPlaceSetting.value) {
                        direction = Direction.DOWN
                    } else if (isAnchorBlock(scanPosition)) {
                        direction = Direction.DOWN
                    }

                    if (direction == null) {
                        val fluid = level.getFluidState(scanPosition)
                        if (!fluid.isEmpty && liquidPlaceSetting.value) {
                            direction = Direction.DOWN
                        }
                    }

                    if (direction == null || damage == null) {
                        offsetZ += 1.0
                        continue
                    }

                    if (shouldRejectDamage(scanPosition, damage)) {
                        offsetZ += 1.0
                        continue
                    }

                    val candidatePlacement = AnchorPlacementResult(scanPosition.immutable(), direction, damage)
                    if (isAnchorBlock(scanPosition)) {
                        return candidatePlacement
                    }

                    bestPlacement = candidatePlacement.selectHigherDamage(bestPlacement)

                    offsetZ += 1.0
                }
                offsetY += 1.0
            }
            offsetX += 1.0
        }
        return bestPlacement
    }

    private fun updateCalculation() {
        val currentPlacement = placementData.get()
        if (currentPlacement != null) {
            if (isInvalidPlacement(currentPlacement.base)) {
                placementData.set(null)
            }
        }

        val future = calculationFuture
        if (future != null && future.isDone && placementData.get() == null) {
            try {
                val completedPlacement = future.get()
                if (completedPlacement == null) {
                    placementData.set(null)
                } else {
                    placementData.set(completedPlacement)
                    targetDamage = completedPlacement.targetDamage
                }
            } catch (failure: InterruptedException) {
                failure.printStackTrace()
            } catch (failure: ExecutionException) {
                failure.printStackTrace()
            }
        }

        if (future != null && !future.isDone && !future.isCancelled) {
            return
        }

        val currentTargetDamage = targetDamage
        if (currentTargetDamage != null) {
            val currentTarget = currentTargetDamage.target
            val validationRange = targetRangeSetting.value.toDouble()
            if (AnchorTargetDamage.isInvalidTarget(currentTarget, validationRange)) {
                targetDamage = null
            }
        }

        val calculation = Supplier { calculatePlacement() }
        val calculationExecutor = ClientAccess.executorService
        val newFuture = CompletableFuture.supplyAsync(calculation, calculationExecutor)
        calculationFuture = newFuture.exceptionally { null }
    }

    @EventHandler
    private fun onPacketReceived(event: PacketEvent.Receive) {
        if (sequentialSetting.value) {
            val placement = placementData.get()
            if (placement != null) {
                val packet = event.packet
                if (packet is ClientboundBlockUpdatePacket && targetDamage != null) {
                    val state = packet.blockState
                    if (state.isAir) {
                        val updatedPosition = packet.pos
                        if (updatedPosition == placement.base) {
                            val placeHand = selectAnchorHand()
                            if (placeHand != null) {
                                // placeAnchor (свапы слотов, отправка пакетов) — только с клиентского
                                // потока: событие приходит из netty-потока.
                                ClientAccess.collectiveMinecraft.execute {
                                    placeAnchor(placement.base, placement.direction, placeHand)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        val dimension = WorldUtils.getDimension()
        if (dimension.isNether) {
            val warningComponent = Component.nullToEmpty("Doesn't work in the nether!")
            val warningSignature = ChatUtils.createSignature(-1)
            val warningPriority = ChatUtils.MessagePriority.HIGH
            ChatUtils.printChat(warningComponent, warningSignature, warningPriority)
            disable()
        } else {
            updateCalculation()

            if (!shouldPause()) {
                val placement = placementData.get()
                if (placement != null) {
                    val placeHand = selectAnchorHand()
                    if (placeHand != null) {
                        val position = placement.base
                        val level = ClientAccess.collectiveMinecraft.level ?: return
                        val player = ClientAccess.collectiveMinecraft.player ?: return
                        val state = level.getBlockState(position)
                        val originalSlot = player.inventory.selected

                        if (state.`is`(Blocks.RESPAWN_ANCHOR)) {
                            val direction = selectSupportDirection(position) ?: return

                            val configuredDelay = delaySetting.value
                            val delayMillis = (configuredDelay + ClientContext.serverStateManager.latencyMillis).toLong()
                            if (!actionTimer.hasElapsed(delayMillis)) {
                                return
                            }

                            val charges = state.getValue(RespawnAnchorBlock.CHARGE)
                            if (charges == 0) {
                                chargeAnchor(position, direction, originalSlot)
                            }

                            if (rotateSetting.value) {
                                ClientContext.rotationManager.requestRotation(
                                    RotationManager.getRotationsToPosition(position.center), 100
                                )
                            }

                            val detonationCenter = Vec3.atCenterOf(position)
                            interactWithAnchor(position, direction, detonationCenter, InteractionHand.MAIN_HAND)

                            if (inhibitSetting.value) {
                                level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState())
                            }
                        }

                        val directionSnapshot = placement.direction
                        placeAnchor(position, directionSnapshot, placeHand)
                    }
                }
            }
        }
    }

    companion object {
        private lateinit var antiCheat: AntiCheatModule
        private lateinit var offhand: OffhandModule
        private lateinit var autoCrystal: AutoCrystalModule
        private lateinit var speedMine: SpeedMineModule

        @JvmStatic
        fun bindReferencedModules(registry: ModuleRegistry) {
            antiCheat = registry.getModule(AntiCheatModule::class.java)!!
            offhand = registry.getModule(OffhandModule::class.java)!!
            autoCrystal = registry.getModule(AutoCrystalModule::class.java)!!
            speedMine = registry.getModule(SpeedMineModule::class.java)!!
        }

        private fun createDescription(): String {
            val warningColor = ChatFormatting.RED.toString()
            return "Explodes respawn anchors to kill players. \n${warningColor}Doesn't work in the nether."
        }
    }
}
