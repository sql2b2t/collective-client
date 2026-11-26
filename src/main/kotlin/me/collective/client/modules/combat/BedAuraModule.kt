package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.ColorSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BedItem
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.awt.Color

class BedAuraModule : Module("BedAura", createDescription(), ModuleCategory.COMBAT) {
    private val placeSetting: Setting<Boolean> = add(BooleanSetting("Place", true).markVisibilityDependency())
    private val placeDelaySetting: Setting<Int> = add(NumberSetting("Delay", 1, 1, 20).dependsOn(placeSetting))

    @JvmField
    internal val placeRangeSetting: Setting<Float> = add(NumberSetting("Range", 4.0f, 0.1f, 6.0f).dependsOn(placeSetting))

    @JvmField
    internal val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", true).dependsOn(placeSetting))

    @JvmField
    internal val liquidPlaceSetting: Setting<Boolean> = add(BooleanSetting("LiquidPlace", false).dependsOn(placeSetting))

    @JvmField
    internal val airPlaceSetting: Setting<Boolean> = add(BooleanSetting("AirPlace", false).dependsOn(placeSetting))

    @JvmField
    internal val breakSetting: Setting<Boolean> = add(BooleanSetting("Break", true).markVisibilityDependency())
    private val breakDelaySetting: Setting<Int> = add(NumberSetting("Delay", 1, 1, 20).dependsOn(breakSetting))

    @JvmField
    internal val breakRangeSetting: Setting<Float> = add(NumberSetting("Range", 4.0f, 0.1f, 6.0f).dependsOn(breakSetting))

    @JvmField
    internal val tpsSyncSetting: Setting<Boolean> = add(BooleanSetting("TPSSync", false).dependsOn(breakSetting))

    @JvmField
    internal val instantSetting: Setting<Boolean> = add(BooleanSetting("Instant", true).dependsOn(breakSetting))

    private val extrapolationSetting: Setting<Boolean> = add(BooleanSetting("Extrapolation", false).markVisibilityDependency())
    private val ticksSetting: Setting<Int> = add(NumberSetting("Ticks", 3, 1, 8).dependsOn(extrapolationSetting))
    private val refillSetting: Setting<Boolean> = add(BooleanSetting("Refill", false).markVisibilityDependency())
    private val slotSetting: Setting<Int> = add(NumberSetting("Slot", 1, 1, 9).dependsOn(refillSetting))
    private val renderSetting: Setting<Boolean> = add(BooleanSetting("Render", true).markVisibilityDependency())
    private val fillSetting: Setting<Color> = add(ColorSetting("Fill", Color(1136499199, true)).dependsOn(renderSetting))
    private val outlineSetting: Setting<Color> = add(ColorSetting("Outline", Color(-910321153, true)).dependsOn(renderSetting))
    private val fadeSetting: Setting<Boolean> = add(BooleanSetting("Fade", true).markVisibilityDependency().dependsOn(renderSetting))
    private val fadeTimeSetting: Setting<Float> = add(NumberSetting("FadeTime", 0.5f, 0.1f, 1.0f).withUnit("s").dependsOn(renderSetting, fadeSetting))
    private val targetingSetting: Setting<Boolean> = add(BooleanSetting("Targeting", false).markAsGroup().markVisibilityDependency())

    @JvmField
    internal val minDamageSetting: Setting<Float> = add(NumberSetting("MinDamage", 6.0f, 0.0f, 12.0f).dependsOn(targetingSetting))

    @JvmField
    internal val maxSelfDamageSetting: Setting<Float> = add(NumberSetting("MaxSelfDamage", 10.0f, 0.0f, 20.0f).dependsOn(targetingSetting))

    @JvmField
    internal val antiSuicideSetting: Setting<Boolean> = add(BooleanSetting("AntiSucide", true).dependsOn(targetingSetting))

    private val placementScanner = BedPlacementScanner(this)
    private val breakScanner = BedBreakScanner(this)
    private val placeTimer = ElapsedTimer()
    private val breakTimer = ElapsedTimer()
    private var target: Player? = null
    private var placement: BedPlacementCandidate? = null
    private var breakPosition: BlockPos? = null

    init {
        val renderer = ClientContext.featureBoxRenderer
        val renderConfiguration = FeatureBoxRenderer.RenderConfiguration(
            this, fillSetting, outlineSetting,
            { 1.0f },
            fadeTimeSetting, { false }, fadeSetting,
            1000
        )
        renderer.register(renderConfiguration)

        placeRangeSetting.setConfigName("PlaceRange")
        breakRangeSetting.setConfigName("BreakRange")
        placeDelaySetting.setConfigName("PlaceDelay")
        breakDelaySetting.setConfigName("BreakDelay")
    }

    private fun refillBeds() {
        if (!refillSetting.value) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val refillSlot = slotSetting.value - 1
        val currentStack = player.inventory.getItem(refillSlot)

        if (currentStack.item !is BedItem) {
            val bedSlot = InventoryUtils.findInventorySlot({ candidateStack -> candidateStack.item is BedItem }, true)
            if (bedSlot != -1) {
                InventoryUtils.swapWithHotbarSlot(bedSlot, refillSlot)
            }
        }
    }

    private fun breakBed(position: BlockPos?) {
        if (position == null) {
            return
        }

        val direction = selectInteractionDirection(position, Direction.UP)
        val hit = BlockHitResult(Vec3.atCenterOf(position), direction, position, false)
        val interactionScheduler = ClientContext.playerStateTracker
        interactionScheduler.runWithoutSneaking {
            val gameMode = ClientAccess.collectiveMinecraft.gameMode
            val player = ClientAccess.collectiveMinecraft.player
            if (player != null && gameMode != null) {
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
                player.swing(InteractionHand.MAIN_HAND)
            }
        }

        val level = ClientAccess.collectiveMinecraft.level
        if (level != null) {
            for (adjacentDirection in Direction.Plane.HORIZONTAL) {
                val adjacentPosition = position.relative(adjacentDirection)
                val state = level.getBlockState(adjacentPosition)
                if (state.block is BedBlock) {
                    ClientContext.worldStateManager.destroyBlockPredicted(adjacentPosition)
                    break
                }
            }
        }

        ClientContext.worldStateManager.destroyBlockPredicted(position)
        breakTimer.reset()
    }

    override fun getInfo(): String? {
        return target?.gameProfile?.name
    }

    private fun hasActionDelayElapsed(timer: ElapsedTimer, delaySettingSnapshot: Setting<Int>): Boolean {
        // TPSSync: задержки растягиваются на медленном сервере — делим на tickRateScale (см. SpeedMineModule).
        val factor = if (tpsSyncSetting.value) Math.max(0.05f, ClientContext.serverStateManager.tickRateScale) else 1.0f
        val delayTicks = delaySettingSnapshot.value
        val requiredMillis = (50.0 * delayTicks / factor).toLong()
        return timer.hasElapsed(requiredMillis)
    }

    private fun placeWithBedSlot(placement: BedPlacementCandidate) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val bedSlot = InventoryUtils.findHotbarSlot { candidateStack -> candidateStack.item is BedItem }
        val originalSlot = player.inventory.selectedSlot

        if (bedSlot != -1) {
            InventoryUtils.selectHotbarSlot(bedSlot)
            placeBed(placement)
            InventoryUtils.selectHotbarSlot(originalSlot)
        }
    }

    internal fun isBedPart(position: BlockPos, part: BedPart): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(position)
        if (state.block is BedBlock) {
            return state.getValue(BedBlock.PART) == part
        }
        return false
    }

    private fun selectTarget(): Player? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        var closestTarget: Player? = null
        var closestDistance = 99999.0f

        val eyePos = player.eyePosition

        for (candidate in level.players()) {
            if (candidate !== player && !ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                val dist = eyePos.distanceTo(candidate.position()).toFloat()
                if (dist < closestDistance) {
                    closestTarget = candidate
                    closestDistance = dist
                }
            }
        }

        return closestTarget
    }

    fun getDamageBounds(entity: LivingEntity): AABB {
        if (entity is Player && extrapolationSetting.value) {
            val extrapolationTicks = ticksSetting.value
            return ClientContext.movementPredictionManager.getPredictedBoundingBox(entity, extrapolationTicks)
        }
        return entity.boundingBox
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        val dimension = WorldUtils.getDimension()
        if (dimension.isOverworld) {
            return
        }

        target = selectTarget()
        val currentTarget = target
        if (currentTarget == null) {
            placement = null
        } else {
            placement = placementScanner.scanPlacement(currentTarget)
            breakPosition = breakScanner.scanBreakPosition(currentTarget)
        }

        val currentPlacement = placement
        if (currentPlacement != null) {
            refillBeds()

            if (breakSetting.value) {
                if (hasActionDelayElapsed(breakTimer, breakDelaySetting)) {
                    breakBed(breakPosition)
                }
            }

            if (placeSetting.value) {
                if (hasActionDelayElapsed(placeTimer, placeDelaySetting)) {
                    placeWithBedSlot(currentPlacement)
                }
            }
        }
    }

    private fun placeBed(placement: BedPlacementCandidate?) {
        if (placement == null) return

        val footSnapshot = placement.foot
        if (BlockInteractionUtils.canPlaceAt(footSnapshot, true)) {
            val placementHit = placement.hitResult
            val hitPoint = placementHit.location

            val angles = if (rotateSetting.value && hitPoint != null) {
                RotationManager.getRotationsToPosition(hitPoint)
            } else {
                RotationManager.calculateRotations(Vec3.atCenterOf(placement.foot), Vec3.atCenterOf(placement.head))
            }

            ClientContext.rotationManager.requestRotation(angles, 250)
            val hit = placement.hitResult
            val placementScheduler = ClientContext.playerStateTracker
            placementScheduler.runSneaking {
                PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hit)
            }

            val player = ClientAccess.collectiveMinecraft.player
            player?.swing(InteractionHand.MAIN_HAND)

            var renderBounds = AABB.encapsulatingFullBlocks(placement.foot, placement.head)
            renderBounds = renderBounds.setMaxY(renderBounds.minY + 0.5625)
            renderBounds = renderBounds.inflate(0.0020000000949949026)

            if (renderSetting.value) {
                ClientContext.featureBoxRenderer.addBox(this, renderBounds)
            }

            if (breakSetting.value && instantSetting.value) {
                val breakScheduler = ClientContext.playerStateTracker
                breakScheduler.runWithoutSneaking {
                    val instantDirection = selectInteractionDirection(placement.foot, Direction.UP)
                    val instantHit = BlockHitResult(Vec3.atCenterOf(placement.foot), instantDirection, placement.foot, false)
                    PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, instantHit)
                    ClientAccess.collectiveMinecraft.player?.swing(InteractionHand.MAIN_HAND)
                }
            }

            placeTimer.reset()
        }
    }

    companion object {
        private fun selectInteractionDirection(position: BlockPos, fallbackDirection: Direction): Direction {
            BlockInteractionUtils.getStrictDirections(position)
            return fallbackDirection
        }

        private fun createDescription(): String {
            val warningColor = ChatFormatting.RED.toString()
            return "Blows your enemies up using beds. \n${warningColor}Doesn't work in the overworld."
        }
    }
}
