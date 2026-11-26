package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.DamageUtils
import me.collective.client.utils.entity.EntityUtils
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class LethalDamageTracker(private val policy: LethalCheckPolicy) : ClientAccess {
    private val lethalCrystalTimer = ElapsedTimer()

    fun update() {
        if (hasLethalCrystal()) {
            this.lethalCrystalTimer.reset()
        }
    }

    fun isLethalCheckEnabled(): Boolean {
        return this.policy.isLethalCheckEnabled()
    }

    fun isCrystalLethal(crystalPosition: Vec3): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val squaredDistance = crystalPosition.distanceToSqr(player.position())
        if (squaredDistance > 144.0) {
            return false
        }

        var previousBox: AABB? = null
        for (predictionStep in 4 downTo 0) {
            val predictedPlayerBox: AABB = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, predictionStep)
            val previousBoxSnapshot = previousBox
            // Если предсказания нет, все шаги возвращают один бокс —
            // повторный расчёт урона (лучи exposure) не нужен.
            if (previousBoxSnapshot != null && isSameBox(previousBoxSnapshot, predictedPlayerBox)) {
                continue
            }
            previousBox = predictedPlayerBox
            val predictedDamage = DamageUtils.getCrystalDamage(crystalPosition, player, predictedPlayerBox)
            if (predictedDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
                return true
            }
        }

        return false
    }

    private fun isSameBox(first: AABB, second: AABB): Boolean {
        return first.minX == second.minX && first.minY == second.minY && first.minZ == second.minZ &&
            first.maxX == second.maxX && first.maxY == second.maxY && first.maxZ == second.maxZ
    }

    private fun isNearbyCrystal(entity: Entity): Boolean {
        if (entity is EndCrystal) {
            val player = ClientAccess.collectiveMinecraft.player ?: return false
            val squaredDistance = player.distanceToSqr(entity)
            return squaredDistance <= 144.0
        }
        return false
    }

    fun shouldHoldTotem(): Boolean {
        return isLethalCheckEnabled() && !this.lethalCrystalTimer.hasElapsed(750L)
    }

    fun hasLethalCrystal(): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val entities = level.entitiesForRendering()
        synchronized(entities) {
            for (entity in entities) {
                if (isNearbyCrystal(entity) && isCrystalLethal(entity.position())) {
                    return true
                }
            }
            return false
        }
    }

    fun interface LethalCheckPolicy {
        fun isLethalCheckEnabled(): Boolean
    }
}
