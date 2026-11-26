package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.utils.ElapsedTimer
import net.minecraft.core.BlockPos

class AutoMineMiningTracker internal constructor() : ClientAccess {
    private val recentTimer = ElapsedTimer()
    private var internalRecentPosition: BlockPos? = null

    val currentPosition: BlockPos?
        get() {
            val miningData = speedMine.extraBreakTarget ?: return null
            return miningData.position
        }

    val recentPosition: BlockPos?
        get() = if (recentTimer.hasElapsed(200L)) null else internalRecentPosition

    fun hasMiningActivity(): Boolean {
        val recent = recentPosition
        val level = ClientAccess.collectiveMinecraft.level
        if (recent != null && level != null && !level.isEmptyBlock(recent)) {
            return true
        }
        return speedMine.extraBreakTarget != null
    }

    fun rememberPosition(position: BlockPos) {
        this.internalRecentPosition = position
        this.recentTimer.reset()
    }

    fun hasRecentDelayElapsed(delayMillis: Long): Boolean {
        return recentTimer.hasElapsed(delayMillis)
    }

    companion object {
        lateinit var speedMine: SpeedMineModule

        @JvmStatic
        fun bindModules(registry: ModuleRegistry) {
            speedMine = registry.getModule(SpeedMineModule::class.java) as SpeedMineModule
        }
    }
}
