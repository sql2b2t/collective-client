package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.network.ServerSideDeathState
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player

@JvmRecord
data class AnchorTargetDamage(
    val target: Player,
    val damage: Double
) : ClientAccess {

    companion object {
        @JvmStatic
        fun calculateForBase(module: AnchorAuraModule, position: BlockPos): AnchorTargetDamage? {
            val ignoreMiningBlock = module.shouldIgnoreMiningBlock()
            var bestDamage: AnchorTargetDamage? = null

            val level = ClientAccess.collectiveMinecraft.level ?: return null
            val players = level.players()

            for (candidate in players) {
                val validationRange = module.targetRangeSetting.value.toDouble()
                if (!isInvalidTarget(candidate, validationRange)) {
                    val explosionPositionSnapshot = position.getCenter()
                    val boundsSnapshot = candidate.boundingBox

                    val ignoredBaseSnapshot = if (!module.isAnchorBlock(position) && !ignoreMiningBlock) null else position
                    val damage: Double = DamageUtils.getExplosionDamage(
                        explosionPositionSnapshot, candidate, boundsSnapshot, 5.0, true, ignoredBaseSnapshot, null
                    ).toDouble()

                    if (module.minDamageSetting.value <= damage && damage >= MathConstants.ONE_HALF) {
                        if (bestDamage != null && damage <= bestDamage.damage) {
                            continue
                        }
                        bestDamage = AnchorTargetDamage(candidate, damage)
                    }
                }
            }

            return bestDamage
        }

        @JvmStatic
        fun isInvalidTarget(target: Player?, range: Double): Boolean {
            val localPlayer = ClientAccess.collectiveMinecraft.player ?: return true
            if (target == null || target == localPlayer || !target.isAlive) {
                return true
            }

            if (ClientContext.socialRegistry.isFriendPlayer(target) || (target as ServerSideDeathState).isServerSideDead()) {
                return true
            }
            if (target.isSpectator || target.abilities.instabuild) {
                return true
            }

            return localPlayer.distanceTo(target) > range
        }
    }
}
