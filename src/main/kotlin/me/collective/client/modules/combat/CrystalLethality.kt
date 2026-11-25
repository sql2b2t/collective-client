package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.utils.entity.EntityUtils
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items

enum class CrystalLethality(@JvmField val lethalDamage: Boolean) : ClientAccess {
    NONE(false),
    INTERSECTION(false),
    POP(true),
    KILL(true);

    fun isKill(): Boolean = this == KILL

    val isKill: Boolean @JvmName("isKillProp") get() = this == KILL

    fun isIntersection(): Boolean = this == INTERSECTION

    val isIntersection: Boolean @JvmName("isIntersectionProp") get() = this == INTERSECTION

    companion object {
        @JvmStatic
        fun classifyLethality(target: LivingEntity, damage: Double): CrystalLethality {
            val isLethal = damage >= EntityUtils.getHealthWithAbsorption(target)
            val holdingTotem = target.offhandItem.`is`(Items.TOTEM_OF_UNDYING) || target.mainHandItem.`is`(Items.TOTEM_OF_UNDYING)

            return if (isLethal) {
                if (holdingTotem) POP else KILL
            } else {
                NONE
            }
        }
    }
}
