package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.ModuleRegistry
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.movement.FlightModule
import me.collective.client.settings.options.AntiCheatRotationsChoice
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

class AuraAttackReadiness(private val aura: AuraModule) : ClientAccess {

    private fun shouldWaitForCriticalMovement(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val onGround = player.onGround()

        if (!PlayerStateUtils.isInCobweb(player)) {
            if (!player.isPassenger) {
                val rotations = antiCheat.rotationsSetting
                var motionSynchronized = false
                if (rotations.value == AntiCheatRotationsChoice.MOTION) {
                    val movementSync = antiCheat.movementSyncSetting
                    motionSynchronized = movementSync.value
                }

                if (motionSynchronized) {
                    return shouldWaitForMotionCritical(onGround)
                }

                return shouldWaitForAirborneCritical(onGround)
            }
        }

        return false
    }

    private fun shouldWaitForCritical(target: Entity): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false

        if (flight.isToggled() || player.abilities.flying || player.isFallFlying || player.onClimbable() || PlayerStateUtils.isInLiquid(player)) {
            return false
        }

        if (criticals.isToggled()) {
            if (criticals.mode == CriticalsModule.ModeChoice.GRIM && !aura.isGrimSafe) {
                return false
            }
        }
        if (player.hasEffect(MobEffects.LEVITATION)) {
            return false
        }
        return target is LivingEntity && shouldWaitForCriticalMovement()
    }

    private fun shouldWaitForAirborneCritical(onGround: Boolean): Boolean {
        if (PlayerStateUtils.getTrackedFallDistance() <= 0.0f && !onGround) {
            return true
        }

        if (onGround) {
            if (ClientContext.playerStateTracker.getGroundedTicks() <= 1) {
                return true
            }
        }

        return false
    }

    private fun shouldWaitForMotionCritical(onGround: Boolean): Boolean {
        val currentY = ClientContext.playerStateTracker.getCurrentTickY()
        val previousY = ClientContext.playerStateTracker.getPreviousTickY()
        val verticalMovement = (currentY - previousY).toFloat()

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        var collisionBounds: AABB = player.boundingBox
        if (verticalMovement > 0.0f) {
            val top = collisionBounds.maxY
            val upperY = verticalMovement.toDouble()
            collisionBounds = collisionBounds.setMinY(top).setMaxY(top + upperY)
        } else {
            val bottom = collisionBounds.minY
            val lowerY = (-verticalMovement).toDouble()
            collisionBounds = collisionBounds.setMaxY(bottom).setMinY(bottom - lowerY)
        }

        if (!level.noBlockCollision(player, collisionBounds) && verticalMovement < 0.0f) {
            return true
        }

        if (!level.noBlockCollision(player, collisionBounds) && verticalMovement > 0.0f) {
            return false
        }

        return PlayerStateUtils.getTrackedFallDistance() <= 0.0f && !onGround
    }

    fun canAttack(target: Entity, attackStrength: Float): Boolean {
        if (target is EndCrystal) {
            return true
        }

        if (shouldWaitForCritical(target)) {
            return false
        }

        if (target !is Player && target is LivingEntity) {
            if (!antiCheat.is2b2tEnabled()) {
                val health = EntityUtils.getHealthWithAbsorption(target)
                if (health <= 1.0) {
                    val maximumHealth = target.maxHealth
                    if (maximumHealth <= 4.0f) {
                        return attackStrength >= 0.2
                    }
                }
            }
        }

        // Readiness по реальным кулдаунам оружия.
        return attackStrength >= aura.attackDelay
    }

    companion object {
        lateinit var antiCheat: AntiCheatModule
        lateinit var flight: FlightModule
        lateinit var criticals: CriticalsModule

        @JvmStatic
        fun bindModuleRegistry(modules: ModuleRegistry) {
            antiCheat = modules.getModule(AntiCheatModule::class.java) as AntiCheatModule
            flight = modules.getModule(FlightModule::class.java) as FlightModule
            criticals = modules.getModule(CriticalsModule::class.java) as CriticalsModule
        }
    }
}
