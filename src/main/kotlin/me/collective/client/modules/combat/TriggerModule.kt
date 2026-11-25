package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.math.MathUtils
import me.collective.mixin.MinecraftAccessor
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.phys.EntityHitResult
import java.util.concurrent.TimeUnit

class TriggerModule : Module("Trigger", "Attacks entities under your crosshair.", ModuleCategory.COMBAT) {
    private val rangeSetting: Setting<Float> = add(NumberSetting("Range", 4.5f, 0.0f, 5.0f).withUnit("m"))
    private val onlyWeaponSetting: Setting<Boolean> = add(BooleanSetting("OnlyWeapon", true))
    private val delaySetting: Setting<Double> = add(NumberSetting("Delay", 1.0, 0.0, 5.0).withUnit("s"))
    private val missChanceSetting: Setting<Int> = add(NumberSetting("MissChance", 10, 0, 50).withUnit("%"))
    internal val targetsSetting: Setting<Boolean> = add(BooleanSetting("Targets", true).markAsGroup().markVisibilityDependency())
    internal val playersSetting: Setting<Boolean> = add(BooleanSetting("Players", true, TriggerPlayersVisibilityPredicate(this)).markVisibilityDependency())
    private val ignoreNakedsSetting: Setting<Boolean> = add(BooleanSetting("IgnoreNakeds", false, TriggerIgnoreNakedsVisibilityPredicate(this)))
    private val animalsSetting: Setting<Boolean> = add(BooleanSetting("Animals", false, TriggerAnimalsVisibilityPredicate(this)))
    private val hostilesSetting: Setting<Boolean> = add(BooleanSetting("Hostiles", true, TriggerHostilesVisibilityPredicate(this)))
    private val crystalsSetting: Setting<Boolean> = add(BooleanSetting("Crystals", false, TriggerCrystalsVisibilityPredicate(this)))
    private val attackTimer = ElapsedTimer()

    @EventHandler
    private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
        if (event.stage == EventStage.Post) {
            return
        }
        if (isWorldUnavailable) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val mainHandItem = player.mainHandItem
        if (mainHandItem.item !is AxeItem) {
            if (mainHandItem.item !is net.minecraft.world.item.SwordItem) {
                if (onlyWeaponSetting.value) {
                    return
                }
            }
        }

        val hitResult = ClientAccess.collectiveMinecraft.hitResult
        if (hitResult !is EntityHitResult) {
            return
        }

        val target = hitResult.entity
        var eligiblePlayer = false
        if (target is Player) {
            val playerName = target.name.string
            if (!ClientContext.socialRegistry.isFriendName(playerName)) {
                if (playersSetting.value) {
                    eligiblePlayer = true
                }
            }
        }

        if (eligiblePlayer && ignoreNakedsSetting.value) {
            if (!PlayerStateUtils.hasArmor(target as Player)) {
                eligiblePlayer = false
            }
        }

        if (!target.isAlive) {
            return
        }

        if (!eligiblePlayer) {
            var isEligibleOther = false
            if (target is AgeableMob && animalsSetting.value) {
                isEligibleOther = true
            } else if (target is Enemy && hostilesSetting.value) {
                isEligibleOther = true
            } else if (target is EndCrystal && crystalsSetting.value) {
                isEligibleOther = true
            }
            if (!isEligibleOther) {
                return
            }
        }

        val attackStrength = player.getAttackStrengthScale(0.5f)
        if (attackStrength < 1.0f) {
            return
        }

        val delaySeconds = delaySetting.value
        val randomizedDelay = delaySeconds * Math.random()
        if (!attackTimer.hasElapsed(randomizedDelay, TimeUnit.SECONDS)) {
            return
        }

        val targetDistance = player.distanceTo(target)
        if (targetDistance <= rangeSetting.value) {
            val missThreshold = missChanceSetting.value
            val miss = MathUtils.passesRandomThreshold(missThreshold)
            attackOrMiss(miss)
            attackTimer.reset()
        }
    }

    private fun attackOrMiss(miss: Boolean) {
        val player = ClientAccess.collectiveMinecraft.player
        if (miss) {
            player?.swing(InteractionHand.MAIN_HAND)
            player?.resetAttackStrengthTicker()
        } else {
            (ClientAccess.collectiveMinecraft as MinecraftAccessor).attack()
        }
    }
}
