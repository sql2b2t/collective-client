package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.HoleManager
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.MovementPredictionManager
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.exploit.MultiTaskModule
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator

class WebAuraModule : PlacementModule("WebAura", "Traps your enemies with cobwebs.", ModuleCategory.COMBAT, "autoweb") {
    private val alternativeSetting: Setting<Boolean> = add(BooleanSetting("Alternative", false))
    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 5.0f, 0.0f, 6.0f).withUnit("m"))
    private val ignoreNakedsSetting: Setting<Boolean> = add(BooleanSetting("IgnoreNakeds", false))
    private val ignoreHoleSetting: Setting<Boolean> = add(BooleanSetting("IgnoreHole", false))
    private val onlyFishSetting: Setting<Boolean> = add(BooleanSetting("OnlyFish", false))
    private val selfSafetySetting: Setting<Boolean> = add(BooleanSetting("SelfSafety", false))
    private val extrapolationSetting: Setting<Boolean> = add(BooleanSetting("Extrapolation", true).markVisibilityDependency())
    private val ticksSetting: Setting<Int> = add(
        NumberSetting("Ticks", 4, 0, 8)
            .setBoundaryLabel("Auto", SettingLabelCondition.MIN)
            .dependsOn(extrapolationSetting)
    )
    private val positionsSetting: Setting<Boolean> = add(BooleanSetting("Positions", false).markAsGroup())
    private val headSetting: Setting<Boolean> = add(BooleanSetting("Head", false).dependsOn(positionsSetting))
    private val legsSetting: Setting<Boolean> = add(BooleanSetting("Legs", true).dependsOn(positionsSetting))
    private var target: Player? = null

    init {
        placementJumpSetting.value = false
        placementAttackSetting.value = false
        unregister(placementAutoDisableSetting)
        unregister(placementJumpSetting)
        unregister(placementChorusSetting)
        unregister(placementPearlSetting)
        unregister(placementAttackSetting)
    }

    override fun disableForJumpOrSleep(): Boolean = false

    private fun getTargetPoint(target: Player): Vec3 {
        var bounds = target.boundingBox

        if (extrapolationSetting.value) {
            val extrapolationTicks = BoundingBoxUtils.getPredictionTicks(ticksSetting)
            bounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(target, extrapolationTicks)
        }

        return BoundingBoxUtils.getBottomCenter(bounds)
    }

    override fun findPlacementSwapSlot(): InventoryUtils.SwapSlot {
        return InventoryUtils.findSwapSlot(Items.COBWEB, alternativeSetting.value)
    }

    override fun getInfo(): String? {
        return target?.gameProfile?.name
    }

    private fun isSafeForSelf(position: BlockPos): Boolean {
        if (!selfSafetySetting.value) {
            return true
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return true
        val placementBounds = AABB(position)

        var boundsSnapshot = player.boundingBox
        var intersectsSelf = boundsSnapshot.intersects(placementBounds)

        if (extrapolationSetting.value) {
            val ticks = ticksSetting.value
            for (remainingTicks in ticks downTo 0) {
                boundsSnapshot = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, remainingTicks)
                if (boundsSnapshot.intersects(placementBounds)) {
                    intersectsSelf = true
                    break
                }
            }
        }

        return !intersectsSelf
    }

    override fun onPlacementAttempt(position: BlockPos, ignoredResult: Boolean) {
        val speedMineRef = speedMine
        if (speedMineRef != null && position == speedMineRef.rebreakPos) {
            SpeedMineModule.clearRebreakAndReset(speedMineRef)
        }
    }

    protected fun findNearestTarget(): Player? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return null

        return level.players().stream()
            .filter { candidate ->
                if (ignoreHoleSetting.value) {
                    val holePosition = PlayerStateUtils.getRoundedBlockPosition(candidate)
                    if (ClientContext.holeManager.intersectsHole(holePosition)) {
                        return@filter false
                    }
                }

                if (onlyFishSetting.value && !candidate.isVisuallySwimming) {
                    return@filter false
                }

                if (candidate === localPlayer) {
                    return@filter false
                }

                val candidateName = candidate.name.string
                if (candidateName == localPlayer.gameProfile.name) {
                    return@filter false
                }

                if (ClientContext.socialRegistry.isFriendName(candidateName)) {
                    return@filter false
                }

                if (!candidate.isAlive || candidate.isDeadOrDying) {
                    return@filter false
                }

                if (ignoreNakedsSetting.value && !PlayerStateUtils.hasArmor(candidate)) {
                    return@filter false
                }

                val distance = getTargetPoint(candidate).distanceTo(localPlayer.eyePosition)
                distance <= rangeSetting.value
            }
            .min(Comparator.comparingDouble { candidate ->
                candidate.distanceToSqr(localPlayer)
            })
            .orElse(null)
    }

    override fun getPlacementPositions(): MutableList<BlockPos> {
        target = findNearestTarget()
        val currentTarget = target ?: return Collections.emptyList()

        val multiTaskRef = multiTask
        if (multiTaskRef != null && multiTaskRef.shouldPauseModule(this)) {
            return Collections.emptyList()
        }

        val positions = ArrayList<BlockPos>()
        var bounds = currentTarget.boundingBox

        if (extrapolationSetting.value) {
            val extrapolationTicks = BoundingBoxUtils.getPredictionTicks(ticksSetting)
            bounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(currentTarget, extrapolationTicks)
        }

        val basePosition = BlockPos.containing(BoundingBoxUtils.getBottomCenter(bounds))

        if (legsSetting.value) {
            val minimumY = bounds.minY
            if (minimumY - basePosition.y <= MathConstants.ONE_HALF) {
                positions.add(basePosition.below())
                positions.add(basePosition)
            }
        }

        if (headSetting.value) {
            positions.add(basePosition.above())
        }

        val level = ClientAccess.collectiveMinecraft.level ?: return Collections.emptyList()
        positions.removeIf { candidatePosition ->
            if (!WorldUtils.isPlacementOccupied(candidatePosition)) {
                val fluid = level.getFluidState(candidatePosition)
                if (fluid.isEmpty) {
                    if (isSafeForSelf(candidatePosition)) {
                        return@removeIf false
                    }
                }
            }
            true
        }

        return positions
    }

    companion object {
        private var multiTask: MultiTaskModule? = null
        private var speedMine: SpeedMineModule? = null

        init {
            multiTask = ClientContext.getModuleOrNull(MultiTaskModule::class.java)
            speedMine = ClientContext.getModuleOrNull(SpeedMineModule::class.java)
        }

        @JvmStatic
        fun bindModules(modules: ModuleRegistry) {
            multiTask = modules.getModuleOrNull(MultiTaskModule::class.java)
            speedMine = modules.getModuleOrNull(SpeedMineModule::class.java)
        }
    }
}
