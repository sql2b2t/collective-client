package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.InventoryStateManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.options.BlockSampleMode
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.piston.PistonBaseBlock
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.function.Predicate

class PusherModule : Module("Pusher", "Pushes your enemies out of their holes.", ModuleCategory.COMBAT) {
    private val rangeSetting: Setting<Float>
    private val alternativeSetting: Setting<Boolean>
    private val delaySetting: Setting<Int>
    private val rotateSetting: Setting<Boolean>
    private val raytraceSetting: Setting<Boolean>
    private val autoDisableSetting: Setting<Boolean>
    private val targetingSetting: Setting<Boolean>
    val ignoreNakedSetting: Setting<Boolean>
    val onlySafeSetting: Setting<Boolean>
    val onlyHoleSetting: Setting<Boolean>
    val renderSetting: Setting<Boolean>
    private val fillSetting: Setting<Color>
    private val outlineSetting: Setting<Color>
    protected val lineWidthSetting: Setting<Float>
    private val fadeSetting: Setting<Boolean>
    private val fadeTimeSetting: Setting<Float>
    private val actionTimer: ElapsedTimer
    private var target: Player? = null
    private var powerPlacementFound: Boolean = false
    private val facingResolver: PusherFacingResolver
    private val targetSelector: PusherTargetSelector

    init {
        this.alternativeSetting = this.add(BooleanSetting("Alternative", false))
        this.rangeSetting = this.add(NumberSetting("Range", 5.0f, 1.0f, 6.0f).withUnit("m"))
        this.delaySetting = this.add(NumberSetting("Delay", 200, 0, 500).withUnit("ms"))
        this.rotateSetting = this.add(BooleanSetting("Rotate", false))
        this.raytraceSetting = this.add(BooleanSetting("Raytrace", false))
        this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", false))
        this.targetingSetting = this.add(BooleanSetting("Targeting", false).markAsGroup().markVisibilityDependency())
        this.ignoreNakedSetting = this.add(BooleanSetting("IgnoreNaked", false).dependsOn(this.targetingSetting))
        this.onlySafeSetting = this.add(BooleanSetting("OnlySafe", false).dependsOn(this.targetingSetting))
        this.onlyHoleSetting = this.add(BooleanSetting("OnlyHole", false).dependsOn(this.targetingSetting))
        this.renderSetting = this.add(BooleanSetting("Render", true).markVisibilityDependency())

        val fillPredicate = Predicate<Any?> { this.renderSetting.isExpanded }
        this.fillSetting = this.add(ColorSetting("Fill", Color(1136499199, true), fillPredicate))

        val outlinePredicate = Predicate<Any?> { this.renderSetting.isExpanded }
        this.outlineSetting = this.add(ColorSetting("Outline", Color(-910321153, true), outlinePredicate))

        this.lineWidthSetting = this.add(NumberSetting("LineWidth", 1.0f, 0.1f, 3.0f).dependsOn(this.renderSetting))
        this.fadeSetting = this.add(BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(this.renderSetting))
        this.fadeTimeSetting = this.add(NumberSetting("FadeTime", 1.0f, 0.1f, 1.0f).withUnit("s").dependsOn(this.renderSetting, this.fadeSetting))
        this.actionTimer = ElapsedTimer()
        this.facingResolver = PusherFacingResolver()
        this.targetSelector = PusherTargetSelector(this)

        val renderConfiguration = FeatureBoxRenderer.RenderConfiguration(
            this, this.fillSetting, this.outlineSetting, this.lineWidthSetting, this.fadeTimeSetting, { true }, this.fadeSetting, 450
        )
        ClientContext.featureBoxRenderer.register(renderConfiguration)
    }

    private fun isAlternativeAllowed(): Boolean {
        return this.alternativeSetting.value && !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2)
    }

    private fun placePower() {
        val currentTarget = this.target ?: return
        var powerSlot = InventoryUtils.findSwapSlot(Items.REDSTONE_BLOCK, this.isAlternativeAllowed())
        var useTorch = false
        if (!powerSlot.isFound) {
            powerSlot = InventoryUtils.findSwapSlot(Items.REDSTONE_TORCH, this.isAlternativeAllowed())
            useTorch = true
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val originalSlot = player.inventory.selectedSlot
        if (powerSlot.isFound) {
            var placement: PowerPlacement? = null
            for (targetDirection in Direction.Plane.HORIZONTAL) {
                val headSnapshot = currentTarget.blockPosition().above()
                val pistonPosition = headSnapshot.relative(targetDirection)

                if (WorldUtils.getBlock(pistonPosition) is PistonBaseBlock) {
                    if (!this.isInvalidPistonPosition(currentTarget, pistonPosition, targetDirection)) {
                        placement = this.evaluatePowerPlacement(useTorch, pistonPosition)
                    }
                }
            }

            if (placement != null) {
                this.powerPlacementFound = true

                if (this.rotateSetting.value) {
                    val angleCenterSnapshot = Vec3.atCenterOf(placement.position)
                    ClientContext.rotationManager.requestRotation(
                        RotationManager.getRotationsToFace(angleCenterSnapshot, placement.direction), 500
                    )
                }

                if (!powerSlot.hotbar && powerSlot.hand == InteractionHand.MAIN_HAND && player.containerMenu != player.inventoryMenu) {
                    return
                }

                InventoryUtils.swapTo(powerSlot)
                try {
                    BlockInteractionUtils.placeBlockIgnoringCrystals(placement.position, placement.direction, false, InteractionHand.MAIN_HAND)
                    PacketSender.sendSwing(InteractionHand.MAIN_HAND)
                } finally {
                    InventoryUtils.swapBack(originalSlot, powerSlot)
                }

                if (this.renderSetting.value) {
                    ClientContext.featureBoxRenderer.addBox(this, placement.position)
                }
            }
        }
    }

    fun getRange(): Float {
        return this.rangeSetting.value
    }

    private fun placePiston() {
        if (!this.powerPlacementFound) {
            val currentTarget = this.target ?: return
            val pistonSlot = InventoryUtils.findSwapSlot(
                { stack -> stack.`is`(Items.PISTON) || stack.`is`(Items.STICKY_PISTON) },
                this.isAlternativeAllowed()
            )

            val player = ClientAccess.collectiveMinecraft.player ?: return
            val originalSlot = player.inventory.selectedSlot
            if (pistonSlot.isFound) {
                var placement: PistonPlacement? = null
                for (targetDirection in Direction.Plane.HORIZONTAL) {
                    val headSnapshot = currentTarget.blockPosition().above()
                    val pistonPosition = headSnapshot.relative(targetDirection)

                    if (!this.isInvalidPistonPosition(currentTarget, pistonPosition, targetDirection)) {
                        if (this.getPistonFacing(pistonPosition) == targetDirection.opposite) {
                            placement = null
                            break
                        }

                        if (BlockInteractionUtils.canPlaceAt(pistonPosition, true)) {
                            val candidate = this.evaluatePistonPlacement(pistonPosition, targetDirection.opposite)
                            if (candidate != null) {
                                placement = candidate
                            }
                        }
                    }
                }

                if (placement != null) {
                    ClientContext.rotationManager.requestRotation(
                        RotationManager.getRotationsToPosition(placement.hitPoint), 500
                    )

                    if (!pistonSlot.hotbar && pistonSlot.hand == InteractionHand.MAIN_HAND && player.containerMenu != player.inventoryMenu) {
                        return
                    }

                    InventoryUtils.swapTo(pistonSlot)
                    try {
                        BlockInteractionUtils.placeBlockIgnoringCrystals(placement.position, placement.direction, false, InteractionHand.MAIN_HAND)
                        PacketSender.sendSwing(InteractionHand.MAIN_HAND)
                    } finally {
                        InventoryUtils.swapBack(originalSlot, pistonSlot)
                    }

                    if (this.renderSetting.value) {
                        ClientContext.featureBoxRenderer.addBox(this, placement.position)
                    }
                }
            }
        }
    }

    override fun getInfo(): String? {
        return this.target?.gameProfile?.name ?: super.getInfo()
    }

    private fun selectPowerDirection(useTorch: Boolean, position: BlockPos, excludedDirection: Direction): Direction? {
        if (!useTorch) {
            val placementMode = BlockInteractionUtils.isStrictDirectionEnabled()
            return BlockInteractionUtils.findSupportDirection(position, placementMode)
        }

        for (direction in Direction.entries) {
            val adjacentPosition = position.relative(direction)
            if (excludedDirection != direction) {
                if (ClientContext.worldStateManager.isPlacementOccupied(adjacentPosition)) {
                    if (!BlockInteractionUtils.isStrictDirectionEnabled()) {
                        return direction
                    }

                    val supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition)
                    if (supportDirections.contains(direction.opposite)) {
                        return direction
                    }
                }
            }
        }

        return null
    }

    private fun isInvalidPistonPosition(target: LivingEntity, position: BlockPos, direction: Direction): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val distance = player.eyePosition.distanceTo(Vec3.atCenterOf(position))
        if (distance > this.getRange()) {
            return true
        }

        val destination = position.relative(direction.opposite, 2)
        var replaceableCount = 0

        for (occupiedPosition in PlayerStateUtils.getSurroundPositions(target)) {
            if (ClientContext.worldStateManager.isPlacementOccupied(occupiedPosition)) {
                replaceableCount++
            }
        }

        val level = ClientAccess.collectiveMinecraft.level ?: return true
        val belowPosition = destination.below()
        val belowState = level.getBlockState(belowPosition)
        val replaceableBelow = belowState.canBeReplaced()

        if (replaceableCount > 2 && replaceableBelow) {
            return true
        }

        val destinationState = level.getBlockState(destination)
        return !destinationState.canBeReplaced()
    }

    private fun evaluatePistonPlacement(position: BlockPos, facing: Direction): PistonPlacement? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        for (direction in Direction.entries) {
            val adjacentPosition = position.relative(direction)

            if (ClientContext.worldStateManager.isPlacementOccupied(adjacentPosition)) {
                if (BlockInteractionUtils.isStrictDirectionEnabled()) {
                    val supportDirections = BlockInteractionUtils.getStrictDirections(adjacentPosition)
                    if (!supportDirections.contains(direction.opposite)) {
                        continue
                    }
                }

                if (this.raytraceSetting.value) {
                    if (!WorldUtils.canSeeAny(BlockSampleMode.X8.sampleBlock(adjacentPosition))) {
                        continue
                    }
                }

                if (!this.rotateSetting.value) {
                    val fallbackPoint = player.eyePosition.relative(facing.opposite, 1.0)
                    return PistonPlacement(direction, position, fallbackPoint)
                }

                for (sample in BlockSampleMode.X8.sampleFace(adjacentPosition, direction.opposite)) {
                    if (this.facingResolver.resolveFacing(RotationManager.getRotationsToPosition(sample)) == facing.opposite) {
                        return PistonPlacement(direction, position, sample)
                    }
                }
            }
        }

        return null
    }

    private fun evaluatePowerPlacement(useTorch: Boolean, pistonPosition: BlockPos): PowerPlacement? {
        var placement: PowerPlacement? = null
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        for (direction in Direction.entries) {
            val position = pistonPosition.relative(direction)
            val stateSnapshot = level.getBlockState(position)

            if (stateSnapshot.`is`(Blocks.REDSTONE_BLOCK)) {
                return null
            }

            if (BlockInteractionUtils.canPlaceAt(position, true)) {
                val excludedDirection = direction.opposite
                val supportDirection = this.selectPowerDirection(useTorch, position, excludedDirection)
                if (supportDirection != null) {
                    placement = PowerPlacement(supportDirection, position)
                }
            }
        }

        return placement
    }

    private fun getPistonFacing(position: BlockPos): Direction? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val state = level.getBlockState(position)
        return if (state.block is PistonBaseBlock) {
            state.getValue(DirectionalBlock.FACING)
        } else {
            null
        }
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        if (!multiTask.shouldPauseModule(this)) {
            this.target = this.targetSelector.selectTarget()
            val currentTarget = this.target
            if (currentTarget != null) {
                if (this.autoDisableSetting.value) {
                    val level = ClientAccess.collectiveMinecraft.level
                    val pistonOverlap = BlockPos.betweenClosedStream(currentTarget.boundingBox).anyMatch { position ->
                        if (level != null) {
                            val state = level.getBlockState(position)
                            state.`is`(Blocks.PISTON_HEAD) || state.`is`(Blocks.PISTON)
                        } else {
                            false
                        }
                    }
                    if (pistonOverlap) {
                        this.disable()
                        return
                    }
                }

                val delayMillis = this.delaySetting.value.toLong()
                if (this.actionTimer.hasElapsed(delayMillis)) {
                    this.powerPlacementFound = false
                    this.actionTimer.reset()
                    this.placePower()
                    this.placePiston()
                }
            }
        }
    }

    private data class PistonPlacement(val direction: Direction, val position: BlockPos, val hitPoint: Vec3)
    private data class PowerPlacement(val direction: Direction, val position: BlockPos)

    companion object {
        @JvmStatic
        lateinit var multiTask: MultiTaskModule

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            multiTask = modules.getModule(MultiTaskModule::class.java) as MultiTaskModule
        }
    }
}
