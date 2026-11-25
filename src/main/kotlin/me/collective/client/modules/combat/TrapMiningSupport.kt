package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.player.SpeedMineModule
import me.collective.client.settings.Setting
import me.collective.client.utils.entity.PlayerStateUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import java.util.ArrayList

class TrapMiningSupport(private val trapModule: AutoTrapModule) {
    private val bodyPartSettings: List<Setting<Boolean>> = listOf(
        trapModule.headSetting,
        trapModule.faceSetting,
        trapModule.legsSetting,
        trapModule.belowSetting
    )

    private fun hasEnemyBelowPosition(blockPos: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val localPlayer = ClientAccess.collectiveMinecraft.player ?: return false
        val expandedBounds = AABB(blockPos).inflate(1.0)

        for (player in level.players()) {
            if (player !== localPlayer && !ClientContext.socialRegistry.isFriendPlayer(player)) {
                if (!player.isDeadOrDying && player.isAlive) {
                    val playerBounds = player.boundingBox
                    if (playerBounds.intersects(expandedBounds)) {
                        val playerPos = PlayerStateUtils.getRoundedBlockPosition(player)
                        if (playerPos.y < blockPos.y) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    fun collectMiningPositions(): MutableList<BlockPos> {
        val speedMine = speedMineModule
        val miningPos = speedMine?.miningPos ?: speedMine?.rebreakPos ?: return ArrayList()

        val player = ClientAccess.collectiveMinecraft.player ?: return ArrayList()
        val eyeSnapshot = player.eyePosition
        val distance = eyeSnapshot.distanceTo(miningPos.center)

        if (distance > trapModule.rangeSetting.value || !hasEnemyBelowPosition(miningPos)) {
            return ArrayList()
        }

        if (trapModule.hasLogoutAbovePosition(miningPos)) {
            return ArrayList()
        }
        val positions = ArrayList<BlockPos>(1)
        positions.add(miningPos)
        return positions
    }

    fun shouldUseMiningSupport(): Boolean {
        if (!trapModule.miningSupportAllowed) {
            return false
        }

        for (setting in bodyPartSettings) {
            if (setting.value) {
                return false
            }
        }

        return true
    }

    companion object {
        private var speedMineModule: SpeedMineModule? = null

        init {
            speedMineModule = ClientContext.getModuleOrNull(SpeedMineModule::class.java)
        }

        @JvmStatic
        fun bindReferencedModules(registry: ModuleRegistry) {
            speedMineModule = registry.getModule(SpeedMineModule::class.java) as? SpeedMineModule
        }
    }
}
