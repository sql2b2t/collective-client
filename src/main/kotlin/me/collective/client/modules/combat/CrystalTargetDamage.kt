package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathConstants
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

@JvmRecord
data class CrystalTargetDamage(
    val target: LivingEntity,
    val damage: Double,
    val kind: CrystalPlacementKind,
    val lethality: CrystalLethality
) {
    companion object {
        @JvmStatic
        fun calculateForBase(
            module: AutoCrystalModule,
            base: BlockPos,
            hasCrystalBase: Boolean,
            targets: List<LivingEntity>
        ): CrystalTargetDamage? {
            var bestTargetDamage: CrystalTargetDamage? = null
            for (candidate in targets) {
                if (module.isInvalidTarget(candidate)) {
                    continue
                }

                val baseY = base.getY().toDouble()
                val targetY = candidate.getY()
                if (baseY > targetY + 1.5 && !hasCrystalBase) {
                    continue
                }

                val isFriend = ClientContext.socialRegistry.isFriendEntity(candidate) && candidate !== ClientAccess.collectiveMinecraft.player

                // Чистые проверки без зависимости от damage выполняются ДО
                // дорогого расчёта урона (расчёт — чистая функция, для отброшенных
                // кандидатов его результат и так не использовался). Для друга
                // проверка «голизны» не переносится: в friend-ветке нужен damage
                // для решения return null (kill friend) — она остаётся ниже.
                if (isFriend && !module.antiFriendKillSetting.value) {
                    continue
                }

                if (!isFriend && module.ignoreNakedsSetting.value) {
                    val holdingCrystal = candidate.isHolding(Items.END_CRYSTAL)
                    if (!PlayerStateUtils.hasArmor(candidate) && !holdingCrystal) {
                        continue
                    }
                }

                val targetBounds: AABB = module.getTargetBounds(candidate)
                val boundsCenterSnapshot = BoundingBoxUtils.getBottomCenter(targetBounds)
                val baseCenter = Vec3.atBottomCenterOf(base)
                val distance = boundsCenterSnapshot.distanceTo(baseCenter)

                val crystalRange: Float = module.crystalRangeSetting.value
                val beyondCrystalRange = distance > crystalRange.toDouble() &&
                    !module.obsidianDropNearTarget && !module.quiverThreatActive

                val damage = module.calculateExplosionDamage(base, candidate, targetBounds, true)
                val lethality = CrystalLethality.classifyLethality(candidate, damage)

                if (isFriend) {
                    if (damage > EntityUtils.getHealthWithAbsorption(candidate)) {
                        return null
                    }
                    continue
                }

                if (beyondCrystalRange && !lethality.lethalDamage) {
                    continue
                }

                val minimumDamage = CrystalDamagePolicy.PLACE.getMinimumDamage(candidate)
                if ((minimumDamage > damage || damage < MathConstants.ONE_HALF) && !lethality.lethalDamage) {
                    continue
                }
                if (bestTargetDamage != null && damage <= bestTargetDamage.damage) {
                    continue
                }

                bestTargetDamage = CrystalTargetDamage(candidate, damage, CrystalPlacementKind.NONE, lethality)
            }

            return bestTargetDamage
        }
    }
}
