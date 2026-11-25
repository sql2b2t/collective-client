package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.access.MobEffectDurationAccess
import me.collective.client.events.CancellableEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeReleaseUsingItemEvent
import me.collective.client.events.MultiPlayerGameModeUseItemEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.PotionUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.network.PacketSender
import me.collective.mixin.MinecraftAccessor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ProjectileWeaponItem
import net.minecraft.world.item.alchemy.Potion
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.atomic.AtomicBoolean

class ArrowsModule : Module("Arrows", "Will swap between effect arrows in your inventory.", ModuleCategory.COMBAT, "projectiles") {
    val autoShootSetting: Setting<Boolean>
    private val speedSetting: Setting<Int>
    val beforePullSetting: Setting<Boolean>
    private val fovSetting: Setting<Float>
    private val assumeDurationSetting: Setting<Int>
    private val recentEffectTimes: MutableMap<MobEffect, Long> = HashMap()
    private val pendingEffects: MutableList<MobEffect> = ArrayList()
    private val arrowSwapTimer = ElapsedTimer()
    private var selectedArrowSlot = -1
    private var selectedArrowPotion: Potion? = null
    private var allowNextProjectileUse = false

    init {
        this.autoShootSetting = this.add(BooleanSetting("AutoShoot", false).markVisibilityDependency())
        this.speedSetting = this.add(NumberSetting("Speed", 3, 3, 8).dependsOn(this.autoShootSetting))
        this.beforePullSetting = this.add(BooleanSetting("BeforePull", false))
        this.fovSetting = this.add(NumberSetting("Fov", 20.0f, 10.0f, 45.0f) { !this.autoShootSetting.value })
        this.assumeDurationSetting = this.add(NumberSetting("AssumeDuration", 50, 0, 100).withUnit("%"))
    }

    override fun onEnable() {
        if (autoShotInProgress.get()) {
            this.disable()
        }
    }

    private fun interceptProjectileUse(event: CancellableEvent, hand: InteractionHand) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.getItemInHand(hand).item is ProjectileWeaponItem) {
            if (this.allowNextProjectileUse) {
                this.allowNextProjectileUse = false
            } else {
                event.cancel()
                this.prepareArrow()
                val scheduler = ClientContext.taskScheduler
                scheduler.scheduleRunTickTask { this.clearItemUseCooldown() }
                this.allowNextProjectileUse = true
            }
        }
    }

    @EventHandler
    private fun onMultiPlayerGameModeUseItemEvent(event: MultiPlayerGameModeUseItemEvent) {
        if (this.beforePullSetting.value) {
            this.interceptProjectileUse(event, event.hand)
        }
    }

    @EventHandler
    private fun onMultiPlayerGameModeReleaseUsingItemEvent(event: MultiPlayerGameModeReleaseUsingItemEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val hand = player.usedItemHand
        val target = this.findEffectTarget()

        if (player.getItemInHand(hand).item is ProjectileWeaponItem) {
            if (target == null || (this.selectedArrowSlot != -1 && this.scoreArrow(player.inventory.getItem(this.selectedArrowSlot), target) > 0)) {
                this.rememberReleasedEffects()
                if (!this.beforePullSetting.value) {
                    this.prepareArrow()
                }
                return
            }

            val previousHotbarSlot = player.inventory.selected
            var temporarySlotIndex = (previousHotbarSlot + 1) % 9

            InventoryUtils.selectHotbarSlotForPlacement(temporarySlotIndex)
            val releaseAction = ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM
            PacketSender.sendPlayerAction(releaseAction, BlockPos.ZERO, Direction.DOWN)

            InventoryUtils.selectHotbarSlotForPlacement(previousHotbarSlot)
            event.cancel()
        }
    }

    private fun scoreArrow(stack: ItemStack, player: Player?): Int {
        if (stack.item !is ArrowItem || player == null) {
            return -1
        }

        val localPlayer = ClientAccess.collectiveMinecraft.player
        val potion = PotionUtils.getPotion(stack)
        val beneficialTarget = player == localPlayer || ClientContext.socialRegistry.isFriendPlayer(player)
        var score = 0
        if (potion.effects.isEmpty()) {
            score = if (beneficialTarget) -1 else 1
        }

        for (effect in potion.effects) {
            val effectType = effect.effect
            var alreadyCovered = player.hasEffect(effectType)
            if (alreadyCovered) {
                val existingEffect = player.getEffect(effectType)
                if (existingEffect is MobEffectDurationAccess) {
                    val remainingDuration = existingEffect.`collective$getDurationRatio`()
                    val scaledDuration = remainingDuration * 100.0f
                    if (scaledDuration < this.assumeDurationSetting.value) {
                        alreadyCovered = false
                    }
                }
            }

            if (!alreadyCovered) {
                val recentEffect = effectType.value()
                if (!this.recentEffectTimes.containsKey(recentEffect)) {
                    val effectValueSnapshot = effectType.value()
                    val amplifier = if (!effectValueSnapshot.isInstantenous) effect.amplifier else 0
                    var effectScore = amplifier + 1
                    if (this.isBeneficialArrowEffect(effectType) != beneficialTarget) {
                        effectScore *= -1
                    }
                    score += effectScore
                }
            }
        }

        return score
    }

    @EventHandler(priority = 200)
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        var target: Player? = this.findEffectTarget()
        if (target == null) {
            target = player
        }

        val now = System.currentTimeMillis()
        this.recentEffectTimes.entries.removeIf { entry -> entry.value + 750L < now }

        var bestScore = -1
        var bestSlot = -1
        for (slot in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(slot)
            val score = this.scoreArrow(stack, target)
            if (score >= 0 && score > bestScore) {
                bestSlot = slot
                bestScore = score
            }
        }

        this.selectedArrowSlot = bestSlot
        if (this.selectedArrowSlot != -1) {
            val selectedStackSnapshot = player.inventory.getItem(this.selectedArrowSlot)
            this.selectedArrowPotion = PotionUtils.getPotion(selectedStackSnapshot)
        }
    }

    @EventHandler
    private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
        if (this.beforePullSetting.value) {
            this.interceptProjectileUse(event, event.hand)
        }
    }

    private fun isBeneficialArrowEffect(effectType: Holder<MobEffect>): Boolean {
        if (effectType == MobEffects.SLOW_FALLING) {
            return false
        }
        return effectType.value().isBeneficial
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        if (!this.autoShootSetting.value) {
            return
        }
        if (this.selectedArrowSlot == -1) {
            this.disable()
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val arrowStackSnapshot = player.inventory.getItem(this.selectedArrowSlot)
        if (this.scoreArrow(arrowStackSnapshot, player) <= 0) {
            this.disable()
            return
        }

        this.startAutoShot()
    }

    fun isAutoShooting(): Boolean {
        return this.isToggled && this.autoShootSetting.value
    }

    fun shouldBypassUsingItemCheck(): Boolean {
        if (this.isToggled && this.autoShootSetting.value) {
            val bowSlot = InventoryUtils.findHotbarSlot(Items.BOW)
            val player = ClientAccess.collectiveMinecraft.player
            if (bowSlot != -1 && player != null) {
                if (!player.isUsingItem) {
                    return true
                }
                if (autoShotInProgress.get()) {
                    return true
                }
            }
        }
        return false
    }

    private fun finishAutoShot() {
        autoShotInProgress.set(false)
        this.disable()
    }

    private fun rememberReleasedEffects() {
        val releaseTime = System.currentTimeMillis()
        for (effect in this.pendingEffects) {
            this.recentEffectTimes[effect] = releaseTime
        }
        this.pendingEffects.clear()
    }

    private fun clearItemUseCooldown() {
        (ClientAccess.collectiveMinecraft as MinecraftAccessor).itemUseCooldown = 0
    }

    private fun startAutoShot() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val previousHotbarSlot = player.inventory.selected
        val bowSlot = InventoryUtils.findHotbarSlot(Items.BOW)

        val rotationArray = floatArrayOf(player.yRot, -MathConstants.NINETY_DEGREES.toFloat())
        ClientContext.rotationManager.requestRotation(rotationArray, 999)

        if (bowSlot != -1 && !player.isUsingItem) {
            val previousPitch = player.xRot
            InventoryUtils.selectHotbarSlot(bowSlot)
            player.xRot = -MathConstants.NINETY_DEGREES.toFloat()

            val gameMode = ClientAccess.collectiveMinecraft.gameMode
            var useStarted = false
            try {
                gameMode?.useItem(player, InteractionHand.MAIN_HAND)
                useStarted = true
            } finally {
                player.xRot = previousPitch
                if (!useStarted) {
                    InventoryUtils.selectHotbarSlot(previousHotbarSlot)
                }
            }

            if (!autoShotInProgress.get()) {
                autoShotInProgress.set(true)
                val scheduler = ClientContext.taskScheduler
                val releaseShot = Runnable {
                    val p = ClientAccess.collectiveMinecraft.player
                    if (p != null) {
                        ClientAccess.collectiveMinecraft.gameMode?.releaseUsingItem(p)
                    }
                    InventoryUtils.selectHotbarSlot(previousHotbarSlot)
                    ClientContext.taskScheduler.scheduleRotationTask({ this.finishAutoShot() }, 1)
                }

                val releaseDelay = this.speedSetting.value
                scheduler.scheduleRotationTask(releaseShot, releaseDelay)
            }
        }
    }

    private fun findEffectTarget(): Player? {
        if (this.autoShootSetting.value) {
            return ClientAccess.collectiveMinecraft.player
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return null
        if (player.xRot < -85.0f) {
            return player
        }

        var bestPlayer: Player? = null
        var bestAngleDistance = Double.MAX_VALUE
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        for (candidate in level.players()) {
            if (candidate != player && !ClientContext.socialRegistry.isFriendPlayer(candidate)) {
                val rotations = RotationManager.getRotationsToEntity(candidate)

                val targetYaw = rotations[0]
                var angleValue = Mth.degreesDifferenceAbs(player.yRot, targetYaw)
                val yawDimensions = candidate.getDimensions(candidate.pose)
                val targetWidth = yawDimensions.width()
                val yawDistance = angleValue - targetWidth * 0.5f

                val targetPitch = rotations[1]
                angleValue = Mth.degreesDifferenceAbs(player.xRot, targetPitch)
                val pitchDimensions = candidate.getDimensions(candidate.pose)
                val targetHeight = pitchDimensions.height()
                val pitchDistance = angleValue - targetHeight * 0.5f

                val yawFov = this.fovSetting.value
                val pitchFov = this.fovSetting.value
                if (yawDistance <= yawFov && pitchDistance <= pitchFov) {
                    val angleDistance = Math.hypot(yawDistance.toDouble(), pitchDistance.toDouble())
                    if (angleDistance < bestAngleDistance) {
                        bestPlayer = candidate
                        bestAngleDistance = angleDistance
                    }
                }
            }
        }

        return bestPlayer
    }

    private fun prepareArrow() {
        if (!this.arrowSwapTimer.hasElapsed(50L)) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        for (slot in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(slot)
            if (stack.item !is ArrowItem) {
                continue
            }

            if (slot != this.selectedArrowSlot) {
                val firstArrowSlot = slot
                this.arrowSwapTimer.reset()

                ClientContext.inventoryStateManager.setClickBatching(true)
                ClientContext.rotationManager.executeAction {
                    val sourceMenuSlot = InventoryUtils.toMenuSlot(firstArrowSlot)
                    val destinationMenuSlot = InventoryUtils.toMenuSlot(this.selectedArrowSlot)
                    InventoryUtils.swapSlotsWithPickup(sourceMenuSlot, destinationMenuSlot)
                }

                ClientContext.inventoryStateManager.setClickBatching(false)
                this.selectedArrowSlot = -1
            }

            val potion = this.selectedArrowPotion
            if (potion != null) {
                for (effect in potion.effects) {
                    this.pendingEffects.add(effect.effect.value())
                }
            }

            return
        }
    }

    companion object {
        private val autoShotInProgress = AtomicBoolean()
    }
}
