package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.Vec3

class BedBreakScanner(private val module: BedAuraModule) : ClientAccess {
    private var eyePosition: Vec3 = Vec3.ZERO
    private var range: Float = 0.0f
    private var minimumDamage: Float = 0.0f
    private var maximumSelfDamage: Float = 0.0f

    private fun refreshSettings() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        this.eyePosition = player.eyePosition
        this.range = module.breakRangeSetting.value
        this.minimumDamage = module.minDamageSetting.value
        this.maximumSelfDamage = module.maxSelfDamageSetting.value
    }

    fun scanBreakPosition(target: LivingEntity): BlockPos? {
        refreshSettings()
        var bestDamage = 0.0f
        var bestPosition: BlockPos? = null

        val player = ClientAccess.collectiveMinecraft.player ?: return null

        for (candidate in ClientContext.worldStateManager.blockEntities) {
            if (candidate.blockState.block is BedBlock) {
                val state = candidate.blockState
                if (state.getValue(BedBlock.PART) != BedPart.FOOT) {
                    val candidatePos = candidate.blockPos
                    val rangeCenter = Vec3.atCenterOf(candidatePos)

                    if (rangeCenter.distanceTo(eyePosition) <= range.toDouble()) {
                        val targetCenter = Vec3.atCenterOf(candidatePos)
                        val targetDamage = DamageUtils.getBedDamage(targetCenter, target)

                        val selfCenter = Vec3.atCenterOf(candidatePos)
                        val selfDamage = DamageUtils.getBedDamage(selfCenter, player)

                        if (selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
                            if (module.antiSuicideSetting.value) {
                                continue
                            }
                        }

                        if (selfDamage <= maximumSelfDamage &&
                            targetDamage >= minimumDamage &&
                            (bestPosition == null || targetDamage > bestDamage)
                        ) {
                            bestPosition = candidate.blockPos
                            bestDamage = targetDamage
                        }
                    }
                }
            }
        }

        return bestPosition
    }
}
