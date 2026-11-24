package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.GameModeUseItemPacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.TargetSelector
import me.collective.client.utils.inventory.PotionUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.item.ArrowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.Items
import net.minecraft.world.item.ProjectileWeaponItem
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.phys.Vec3
import java.util.Comparator

class BowAimModule : Module("BowAim", "Helps aiming with bows.", ModuleCategory.COMBAT) {
    internal val targetsSetting: Setting<Boolean> = add(BooleanSetting("Targets", true).markAsGroup())
    internal val playersSetting: Setting<Boolean> = add(BooleanSetting("Players", true) { targetsSetting.isExpanded }.markVisibilityDependency())
    private val ignoreNakedsSetting: Setting<Boolean> = add(BooleanSetting("IgnoreNakeds", false) { targetsSetting.isExpanded && playersSetting.isExpanded })
    private val animalsSetting: Setting<Boolean> = add(BooleanSetting("Animals", false) { targetsSetting.isExpanded })
    private val hostilesSetting: Setting<Boolean> = add(BooleanSetting("Hostiles", true) { targetsSetting.isExpanded })
    private val neutralsSetting: Setting<Boolean> = add(BooleanSetting("Neutrals", false).dependsOn(targetsSetting))

    private var cachedRotations: FloatArray? = null

    private fun findArrowPotion(): Potion {
        val player = ClientAccess.collectiveMinecraft.player ?: return PotionUtils.MISSING_POTION
        val inventory = player.inventory

        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (stack.item is ArrowItem) {
                return PotionUtils.getPotion(stack)
            }
        }
        return PotionUtils.MISSING_POTION
    }

    @EventHandler
    private fun onUseItemPacket(event: GameModeUseItemPacketEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val packet = event.packet
        val heldStack = player.getItemInHand(packet.hand)

        if (heldStack.`is`(Items.CROSSBOW) && cachedRotations != null) {
            event.setRotations(cachedRotations!!)
        }
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        cachedRotations = null

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val mainHandItem = player.mainHandItem.item
        val projectileWeapon = mainHandItem is ProjectileWeaponItem

        var activeUse = player.isUsingItem
        if (CrossbowItem.isCharged(player.mainHandItem)) {
            activeUse = true
        }

        if (!projectileWeapon || !activeUse) {
            return
        }

        for (effect in findArrowPotion().effects) {
            val effectHolder = effect.effect
            val effectType = effectHolder.value()

            if (effectType.isBeneficial && effectHolder != MobEffects.SLOW_FALLING && effectHolder != MobEffects.JUMP_BOOST) {
                return
            }
        }

        val target = TargetSelector()
            .withPlayers(playersSetting.value)
            .withNeutrals(neutralsSetting.value)
            .withAnimals(animalsSetting.value)
            .withHostiles(hostilesSetting.value)
            .ignoreNakeds(ignoreNakedsSetting.value)
            .sortBy(Comparator.comparingDouble { getAngularDistance(it).toDouble() })
            .visibleRange(128.0f)
            .select()

        if (target == null || target is EnderMan) {
            return
        }

        val targetBounds = target.boundingBox
        val targetCenter = targetBounds.center

        if (!RotationManager.isVisible(targetBounds)) {
            return
        }

        if (!WorldUtils.canSeeFromEyes(targetCenter)) {
            return
        }

        val targetDistance = target.position().distanceTo(player.eyePosition)
        val distanceLeadTicks = (targetDistance / 8.0).toFloat()
        val predictionTicks = 4.0f + distanceLeadTicks

        val rotations = calculateBallisticRotations(target, true, predictionTicks) ?: return
        if (rotations[0].isNaN() || rotations[1].isNaN()) {
            return
        }

        cachedRotations = rotations
        ClientContext.rotationManager.requestRotation(rotations, 5, true)
    }

    companion object {
        private fun calculateBallisticRotations(target: Entity, predictMotion: Boolean, predictionTicks: Float): FloatArray? {
            val player = ClientAccess.collectiveMinecraft.player ?: return null
            var targetPosition = target.position().add(0.0, target.bbHeight / 2.0 + 0.1, 0.0)
            var eyePosition = player.eyePosition
            if (predictMotion) {
                targetPosition = targetPosition.add(MathUtils.getPositionDelta(target).scale(predictionTicks.toDouble()))
                eyePosition = eyePosition.add(MathUtils.getPositionDelta(player).scale(predictionTicks.toDouble()))
            }
            val delta = targetPosition.subtract(eyePosition)
            val horizontalDistance = Math.hypot(delta.x, delta.z)
            val charge = if (player.isHolding(Items.CROSSBOW)) 1.0f else player.ticksUsingItem / 20.0f
            val launchPower = Math.min((charge * charge + 2.0f * charge) / 3.0f, 1.0f)
            val gravity = 0.006
            val powerSquared = (launchPower * launchPower).toDouble()
            val discriminant = powerSquared * powerSquared - gravity * (gravity * (horizontalDistance * horizontalDistance) + 2.0 * delta.y * powerSquared)
            if (discriminant < 0.0) {
                return null
            }
            val yaw = (Math.atan2(delta.z, delta.x) * 180.0 / MathConstants.PI).toFloat() - MathConstants.NINETY_DEGREES
            val pitch = (-Math.toDegrees(Math.atan((powerSquared - Math.sqrt(discriminant)) / (gravity * horizontalDistance)))).toFloat()
            return if (yaw.isFinite() && pitch.isFinite()) floatArrayOf(yaw, pitch) else null
        }

        private fun getAngularDistance(target: Entity): Float {
            val player = ClientAccess.collectiveMinecraft.player ?: return 0.0f
            val eyePosition = player.eyePosition
            val clampedPoint = BoundingBoxUtils.clampToBounds(eyePosition, target.boundingBox)
            return MathUtils.cameraRotationDifference(clampedPoint)
        }
    }
}
