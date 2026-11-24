package me.collective.client.modules.combat

import me.collective.client.ClientContext
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerState
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.Arrays

class FishTrapModule : AutoTrapModule("FishTrap", "Traps your enemies in fish (crawling) position.", ModuleCategory.COMBAT) {
    private val modeSetting: Setting<CrawlTrapMode>
    private val extrapolationSetting: Setting<Int>

    init {
        val ticksEditor = NumberSetting("Extrapolation", 0, 0, 6)
        val extrapolationEditor = this.add(ticksEditor)
        extrapolationEditor.setBoundaryLabel("None", SettingLabelCondition.MIN)
        this.extrapolationSetting = extrapolationEditor

        val modeEditor = EnumSetting("Mode", CrawlTrapMode.HEAD)
        this.modeSetting = this.add(modeEditor)

        this.setDisableOnRespawn(false)
        this.unregister(this.targetsSetting)
        this.unregister(this.headSetting)
        this.unregister(this.faceSetting)
        this.unregister(this.legsSetting)
        this.unregister(this.belowSetting)
        this.unregister(this.logoutsSetting)
        this.unregister(this.ignoreCrawlSetting)
        this.unregister(this.onlyPhasedSetting)

        this.headSetting.value = true
        this.faceSetting.value = false
        this.legsSetting.value = false
    }

    override fun getTargetPosition(player: Player): Vec3 {
        if (!this.extrapolationSetting.isAtMinimum) {
            val predictionManagerSnapshot = ClientContext.movementPredictionManager
            val predictionTicks = this.extrapolationSetting.value
            val predictedBounds = predictionManagerSnapshot.getPredictedBoundingBox(player, predictionTicks)
            return BoundingBoxUtils.getBottomCenter(predictedBounds)
        } else {
            return super.getTargetPosition(player)
        }
    }

    override fun isValidTarget(player: Player): Boolean {
        if (player is PlayerState && !player.`collective$wasCrawling`()) {
            return false
        }
        return !player.isFallFlying && super.isValidTarget(player)
    }

    override fun includeLowerSideOffsets(): Boolean {
        return false
    }

    override fun isHeadOnly(): Boolean {
        return true
    }

    override fun collectTargetOffsets(targetPos: BlockPos, baseYOffset: Int, targetHeight: Float): List<Vec3> {
        return this.modeSetting.value.offsets.toList()
    }

    enum class CrawlTrapMode(
        private val displayName: String,
        vararg val offsets: Vec3
    ) : NamedEntry {
        HEAD("Head", Vec3(0.0, 1.0, 0.0)),
        BELOW("Below", Vec3(0.0, -1.0, 0.0)),
        BOTH("Both", Vec3(0.0, 1.0, 0.0), Vec3(0.0, -1.0, 0.0));

        override fun getName(): String {
            return this.displayName
        }

        fun getOffsets(): List<Vec3> {
            return Arrays.asList(*this.offsets)
        }
    }
}
