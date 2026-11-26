package me.collective.client.modules.combat

import net.minecraft.world.phys.Vec3
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.options.SpeedMineRebreakChoice
import me.collective.client.utils.world.HoleInfo
import kotlin.math.abs

open class PlainHoleFillPolicy(protected val holeFillModule: HoleFillModule) : ClientAccess {

    open fun shouldFill(hole: HoleInfo): Boolean {
        if (speedMineModule.isToggled()) {
            val rebreakSettingRef = speedMineModule.rebreakSetting
            if (rebreakSettingRef.value == SpeedMineRebreakChoice.INSTANT) {
                val miningPos = speedMineModule.miningPos
                if (miningPos != null) {
                    val miningCenter = Vec3.atCenterOf(miningPos)
                    if (hole.bounds.contains(miningCenter)) {
                        return false
                    }
                }
            }
        }

        val widthBounds = hole.bounds
        if (widthBounds.getXsize() != 1.0 || widthBounds.getZsize() != 1.0) {
            if (!holeFillModule.doublesSetting.value) {
                return false
            }
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val eyeSnapshot = player.eyePosition
        val holeCenter = Vec3.atCenterOf(hole.pos)
        val distance = eyeSnapshot.distanceTo(holeCenter)

        val configuredRange: Float = holeFillModule.rangeSetting.value
        if (distance <= configuredRange.toDouble()) {
            val eyeY = player.eyePosition.y
            val verticalDifference = eyeY - holeCenter.y
            val absoluteVerticalDifference = abs(verticalDifference)
            val configuredVerticalRange: Float = holeFillModule.verticalRangeSetting.value
            return absoluteVerticalDifference <= configuredVerticalRange.toDouble()
        }
        return false
    }

    companion object {
        @JvmStatic
        lateinit var speedMineModule: SpeedMineModule

        @JvmStatic
        fun connectSpeedMineModule(registry: ModuleRegistry) {
            speedMineModule = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule
        }
    }
}
