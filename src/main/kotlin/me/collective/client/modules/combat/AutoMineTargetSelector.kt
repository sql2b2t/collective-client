package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.RotationManager
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.PlayerStateUtils
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

class AutoMineTargetSelector internal constructor(private val module: AutoMineModule) : ClientAccess {

    fun getTargetFootprintBounds(target: Player): AABB {
        var boundsSnapshot = target.boundingBox
        val shrink = -BoundingBoxUtils.blockCornerInset
        boundsSnapshot = boundsSnapshot.inflate(shrink, 0.0, shrink)
        val maximumY = target.getY()
        return boundsSnapshot.setMaxY(maximumY)
    }

    fun selectTarget(): Player? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        val filteredCandidates = level.players().filter { candidate ->
            candidate.isAlive &&
                candidate !== player &&
                player.distanceTo(candidate) <= module.rangeSetting.value + 2.0f &&
                !ClientContext.socialRegistry.isFriendPlayer(candidate) &&
                (!module.ignoreNakedSetting.value || PlayerStateUtils.hasArmor(candidate))
        }

        return filteredCandidates.minByOrNull { candidate ->
            val yawSnapshot = player.yRot
            val candidateYaw = RotationManager.getRotationsToEntity(candidate)[0]
            Mth.degreesDifferenceAbs(yawSnapshot, candidateYaw)
        }
    }
}
