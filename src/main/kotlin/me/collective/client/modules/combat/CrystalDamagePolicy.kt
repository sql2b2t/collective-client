package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks

enum class CrystalDamagePolicy(private val displayName: String) : ClientAccess, NamedEntry {
    PLACE("Place"),
    HIT("Hit");

    fun getMinimumDamage(target: LivingEntity): Double {
        if (!autoCrystal.obsidianDropNearTarget && !autoCrystal.quiverThreatActive) {
            if (autoCrystal.isFacePlaceEnabled) {
                val health = EntityUtils.getHealthWithAbsorption(target)

                val healthLimit = autoCrystal.facePlaceHealthSetting.value
                if (health <= healthLimit) {
                    return 0.6
                }

                if (target is Player) {
                    val armor = InventoryUtils.getLowestArmorDurabilityPercent(target)

                    val armorLimit = autoCrystal.facePlaceArmorThresholdSetting.value
                    if (armor <= armorLimit.toInt()) {
                        return 0.6
                    }
                }

                if (target.isUsingItem) {
                    val activeStack = target.useItem
                    if (activeStack.`is`(Items.SHIELD)) {
                        if (autoCrystal.facePlaceShieldBreakerSetting.value) {
                            return 0.6
                        }
                    }
                }
            }

            val level = ClientAccess.collectiveMinecraft.level
            if (level != null) {
                val stateSnapshot = level.getBlockState(target.blockPosition())
                if (stateSnapshot.`is`(Blocks.NETHER_PORTAL)) {
                    return 0.6
                }
            }

            var placeMinimum = autoCrystal.minPlaceDamageSetting.value.toDouble()
            var hitMinimum = autoCrystal.minBreakDamageSetting.value.toDouble()

            if (autoCrystal.minPlaceDamageSetting.shouldDisplayBoundaryLabel()) {
                placeMinimum = EntityUtils.getHealthWithAbsorption(target).toDouble()
            }

            if (autoCrystal.shouldIgnoreMiningBlock()) {
                val miningMinimum = if (autoCrystal.alwaysIgnoreMiningSetting.value) 0.6 else 5.0
                placeMinimum = Math.min(placeMinimum, miningMinimum)
                hitMinimum = 3.0
            }

            val selectedMinimum = if (this == HIT) hitMinimum else placeMinimum
            val targetHealth = EntityUtils.getHealthWithAbsorption(target).toDouble()
            return Math.min(selectedMinimum, targetHealth)
        } else {
            return 0.1
        }
    }

    fun getMaximumSelfDamage(): Double {
        if (autoCrystal.isSelfInvulnerable) {
            return 999.0
        }

        val selectedMaximum = if (this == HIT) {
            autoCrystal.maxBreakSelfDamageSetting.value
        } else {
            autoCrystal.maxPlaceSelfDamageSetting.value
        }

        return selectedMaximum.toDouble()
    }

    fun shouldRejectDamage(
        target: LivingEntity,
        selfDamageInput: Double,
        targetDamage: Double,
        lethality: CrystalLethality
    ): Boolean {
        var selfDamage = selfDamageInput
        val player = ClientAccess.collectiveMinecraft.player
        val holdingTotem = player?.isHolding(Items.TOTEM_OF_UNDYING) ?: false

        if (autoCrystal.isSelfInvulnerable) {
            selfDamage = 0.0
        }

        if (autoCrystal.forceSuicideSetting.value) {
            return false
        }

        if (selfDamage >= EntityUtils.getPlayerHealthWithAbsorption()) {
            if (autoCrystal.antiSuicideSetting.value) {
                if (!lethality.isKill || !holdingTotem) {
                    return true
                }
            }
        }

        if (this == HIT) {
            if (lethality.isIntersection) {
                return false
            }
        }

        if (lethality.isKill) {
            return false
        }

        if (this == PLACE && selfDamage > 0.0) {
            val damageRatio = targetDamage / selfDamage

            val minimumRatio = autoCrystal.placeDamageRatioSetting.value
            if (damageRatio < minimumRatio.toDouble()) {
                return true
            }
        }

        if (targetDamage < EntityUtils.getHealthWithAbsorption(target)) {
            if (targetDamage < this.getMinimumDamage(target)) {
                return true
            }

            if (selfDamage > this.getMaximumSelfDamage()) {
                return true
            }
        }

        return false
    }

    override fun getName(): String {
        return this.displayName
    }

    companion object {
        private val autoCrystal: AutoCrystalModule
            get() = ClientContext.getModule(AutoCrystalModule::class.java) as AutoCrystalModule
    }
}
