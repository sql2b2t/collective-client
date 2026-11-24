package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.world.BlockInteractionUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

class SelfTrapModule : AutoTrapModule("SelfTrap", "Covers you with blocks.", ModuleCategory.COMBAT) {
    private val onlyHoleSetting: Setting<Boolean>
    private val onlyStandingSetting: Setting<Boolean>
    private val smartSetting: Setting<Boolean>
    protected val enemyRangeSetting: Setting<Float>

    init {
        this.setDisableOnRespawn(true)

        val onlyHoleEditor = BooleanSetting("OnlyHole", false)
        this.onlyHoleSetting = this.add(onlyHoleEditor)

        val onlyStandingEditor = BooleanSetting("OnlyStanding", false)
        this.onlyStandingSetting = this.add(onlyStandingEditor)

        val smartEditor = BooleanSetting("Smart", false)
        val smartDependency = smartEditor.markVisibilityDependency()
        this.smartSetting = this.add(smartDependency)

        val enemyRange = NumberSetting(
            "EnemyRange",
            3.0f,
            1.0f,
            6.0f
        ) { this.smartSetting.isExpanded }
        val enemyRangeWithUnit = enemyRange.withUnit("m")
        this.enemyRangeSetting = this.add(enemyRangeWithUnit)

        this.unregister(this.rangeSetting)
        this.unregister(this.ignoreNakedsSetting)
        this.unregister(this.ignoreCrawlSetting)
        this.unregister(this.onlyPhasedSetting)
        this.unregister(this.logoutsSetting)
        this.unregister(this.belowSetting)
    }

    override fun getInfo(): String? {
        return null
    }

    override fun shouldPlaceBelow(): Boolean {
        return false
    }

    private fun findNearestEnemy(): Entity? {
        val level = ClientAccess.collectiveMinecraft.level ?: return null
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val friends = ClientContext.socialRegistry

        return level.entitiesForRendering()
            .asSequence()
            .filter { candidate ->
                if (candidate == player) {
                    return@filter false
                }
                val candidateDistance = player.distanceTo(candidate)
                if (candidateDistance > this.enemyRangeSetting.value || candidate !is Player) {
                    return@filter false
                }
                val name = candidate.name.string
                !friends.isFriendName(name) && candidate.isAlive
            }
            .minByOrNull { player.distanceToSqr(it) }
    }

    private fun shouldSkipUnsupportedPlacement(blockPos: BlockPos): Boolean {
        val attackCheckFlag = true
        if (!BlockInteractionUtils.canPlaceAt(blockPos, true, attackCheckFlag)) {
            return true
        }

        val airPlaceSettingRef = this.placementAirPlaceSetting
        if (airPlaceSettingRef.value) {
            return false
        }

        val strictDirectionSnapshot = BlockInteractionUtils.isStrictDirectionEnabled()
        val raytraceSettingRef = this.placementRaytraceSetting
        val raytrace = raytraceSettingRef.value
        return BlockInteractionUtils.findSupportDirection(blockPos, strictDirectionSnapshot, raytrace) == null
    }

    override fun selectTarget(): Player? {
        return ClientAccess.collectiveMinecraft.player
    }

    @Suppress("UNCHECKED_CAST")
    override fun getPlacementPositions(): MutableList<BlockPos> {
        val placements = (super.getPlacementPositions() as MutableList<BlockPos>)
        placements.removeIf { this.shouldSkipUnsupportedPlacement(it) }

        if (this.onlyStandingSetting.value && PlayerStateUtils.isLocalHeightBelowOne()) {
            return mutableListOf()
        }
        if (this.smartSetting.value) {
            if (this.findNearestEnemy() == null) {
                return mutableListOf()
            }
        }
        if (this.onlyHoleSetting.value) {
            if (!ClientContext.holeManager.isInHole) {
                return mutableListOf()
            }
        }
        return placements
    }
}
