package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.managers.RotationManager
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.RegistrySetSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.SettingLabelCondition
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.BoundingBoxUtils
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.projectile.ThrownPotion
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.Comparator
import java.util.stream.Stream

class AutoPotModule : Module("AutoPot", "Throws a potion in certain conditions.", ModuleCategory.COMBAT) {
    private val potionsSetting: Setting<Set<Potion>>
    private val delaySetting: Setting<Int>
    private val frequencySetting: Setting<Int>
    private val healthSetting: Setting<Int>
    private val totemsSetting: Setting<Int>
    private val overrideSetting: Setting<Boolean>
    private val autoDisableSetting: Setting<Boolean>
    private val throwTimer: ElapsedTimer

    init {
        this.potionsSetting = this.add(RegistrySetSetting<Potion>("Potions", BuiltInRegistries.POTION))
        this.delaySetting = this.add(NumberSetting("Delay", 75, 75, 500).withUnit("ms"))
        this.frequencySetting = this.add(NumberSetting("Frequency", 1, 1, 3))
        this.healthSetting = this.add(NumberSetting("Health", 8, 1, 36))
        this.totemsSetting = this.add(NumberSetting("Totems", 1, 0, 10))
        this.overrideSetting = this.add(BooleanSetting("Override", false))
        this.autoDisableSetting = this.add(BooleanSetting("AutoDisable", false))
        this.throwTimer = ElapsedTimer()

        this.healthSetting.setBoundaryLabel("None", SettingLabelCondition.MAX)
        this.totemsSetting.setBoundaryLabel("None", SettingLabelCondition.MAX)

        val defaultPotions = listOf(Potions.HEALING.value(), Potions.STRONG_HEALING.value())
        (this.potionsSetting.defaultValue as MutableSet<Potion>).addAll(defaultPotions)
        (this.potionsSetting.value as MutableSet<Potion>).addAll(defaultPotions)
    }

    private fun calculateThrowRotations(): FloatArray {
        val player = ClientAccess.collectiveMinecraft.player ?: return floatArrayOf(0.0f, 0.0f)
        val eyePosition = player.eyePosition
        val playerBlockPosition = BlockPos.containing(player.position())
        val throwBounds = ClientContext.movementPredictionManager.getPredictedBoundingBox(player, 4)
        val rotations = RotationManager.getRotationsToPosition(BoundingBoxUtils.getBottomCenter(throwBounds))
        val movementSpeed = ClientContext.movementSpeedTracker.currentSpeed

        val rayEndSnapshot = if (movementSpeed > 15.0) {
            val pitchSnapshot = rotations[1]
            val yaw = rotations[0]
            val directionSnapshot = Vec3.directionFromRotation(pitchSnapshot, yaw)
            val scaledDirectionSnapshot = directionSnapshot.scale(4.0)
            scaledDirectionSnapshot.add(eyePosition)
        } else {
            player.position().add(0.0, -0.009999999776482582, 0.0)
        }

        val raycastBuilder = BlockRaycastContext.Builder(eyePosition, rayEndSnapshot)
        if (RaycastUtils.raycast(raycastBuilder.build()).type != HitResult.Type.MISS) {
            return floatArrayOf(rotations[0], rotations[1])
        } else {
            val playerBounds = player.boundingBox
            val minimumY = playerBounds.minY
            val bottomFaceSnapshot = playerBounds.setMaxY(minimumY)
            val belowBounds = bottomFaceSnapshot.expandTowards(0.0, -1.0, 0.0)

            val maximumY = playerBounds.maxY
            val topFaceSnapshot = playerBounds.setMinY(maximumY)
            val aboveBounds = topFaceSnapshot.expandTowards(0.0, 1.0, 0.0)

            val playerPosition = player.position()
            val belowStream = BlockPos.betweenClosedStream(belowBounds)
            val aboveStream = BlockPos.betweenClosedStream(aboveBounds)
            val nearestBlockOptional = Stream.concat(belowStream, aboveStream)
                .map { it.immutable() }
                .filter { WorldUtils.isPlacementOccupied(it) }
                .min(Comparator.comparingDouble { block ->
                    block.distToCenterSqr(playerPosition.x, playerPosition.y, playerPosition.z)
                })

            val nearestBlock = nearestBlockOptional.orElse(playerBlockPosition)
            val nearestCenter = nearestBlock.center
            return RotationManager.getRotationsToPosition(nearestCenter)
        }
    }

    private fun calculateThrowCount(): Int {
        if (this.potionsSetting.value.none { this.isHealingPotion(it) }) {
            return 1
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return 1
        var healingStacks = 0
        for (slot in 0 until 9) {
            val stack = player.inventory.getItem(slot)
            val potion = getSplashPotion(stack)
            if (potion != null && this.isHealingPotion(potion)) {
                healingStacks++
            }
        }
        val frequency = this.frequencySetting.value
        return Mth.clamp(healingStacks, 1, frequency)
    }

    fun hasNearbySplashPotion(): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val searchBounds = player.boundingBox.inflate(3.0)

        for (entity in level.entitiesForRendering()) {
            if (entity is ThrownPotion && searchBounds.contains(entity.position())) {
                return true
            }
        }
        return false
    }

    private fun applyLocalItemUse(stack: ItemStack, hand: InteractionHand) {
        val level = ClientAccess.collectiveMinecraft.level ?: return
        val usePlayer = ClientAccess.collectiveMinecraft.player ?: return

        val useResult = stack.use(level, usePlayer, hand)
        val resultStack = if (useResult is InteractionResult.Success) {
            useResult.heldItemTransformedTo() ?: usePlayer.getItemInHand(hand)
        } else {
            usePlayer.getItemInHand(hand)
        }

        if (resultStack != stack) {
            usePlayer.setItemInHand(hand, resultStack)
        }
    }

    private fun isEligiblePotion(stack: ItemStack, allowedPotions: Collection<Potion>): Boolean {
        val potion = getSplashPotion(stack) ?: return false
        if (potion.effects.isEmpty()) {
            return false
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return false
        var hasApplicableEffect = false
        for (effect in potion.effects) {
            if (player.hasEffect(effect.effect)) {
                if (!this.overrideSetting.value) {
                    continue
                }
            }
            hasApplicableEffect = true
        }

        return allowedPotions.contains(potion) && hasApplicableEffect
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        if (!this.healthSetting.isAtMaximum) {
            val health = EntityUtils.getPlayerHealthWithAbsorption()
            val healthLimit = this.healthSetting.value
            if (health > healthLimit) {
                return
            }
        }

        if (!this.totemsSetting.isAtMaximum) {
            val totemCount = InventoryUtils.countItems { stack -> stack.`is`(Items.TOTEM_OF_UNDYING) }
            val totemsLimit = this.totemsSetting.value
            if (totemCount > totemsLimit) {
                return
            }
        }

        val delayMillisSnapshot = this.delaySetting.value.toLong()
        if (!this.throwTimer.hasElapsedAndReset(delayMillisSnapshot)) {
            return
        }

        if (this.hasNearbySplashPotion()) {
            return
        }

        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        val supportBounds = player.boundingBox.expandTowards(0.0, -2.0, 0.0)
        if (player.isFallFlying) {
            return
        }

        if (level.noBlockCollision(player, supportBounds)) {
            return
        }

        val throwCount = this.calculateThrowCount()
        for (throwIndex in 0 until throwCount) {
            val allowedPotions = this.potionsSetting.value
            if (!this.throwPotion(allowedPotions)) {
                break
            }
        }

        if (this.autoDisableSetting.value) {
            this.toggle()
        }
    }

    private fun isHealingPotion(potion: Potion): Boolean {
        if (potion == Potions.HEALING.value()) {
            return true
        }
        return potion == Potions.STRONG_HEALING.value()
    }

    private fun sendPotionUse(hand: InteractionHand, yaw: Float, pitch: Float) {
        val packet = ServerboundUseItemPacket(
            hand,
            PacketSender.nextPredictionSequence(),
            yaw,
            pitch
        )
        PacketSender.markImportant(packet)
        PacketSender.send(packet)
    }

    fun throwPotion(allowedPotions: Collection<Potion>): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val previousSlot = player.inventory.selected
        val selectedItem = InventoryUtils.findSwapSlot({ this.isEligiblePotion(it, allowedPotions) }, true)
        if (!selectedItem.isFound) {
            return false
        }

        val rotations = this.calculateThrowRotations()
        InventoryUtils.swapTo(selectedItem)
        this.sendPotionUse(selectedItem.hand, rotations[0], rotations[1])
        ClientContext.swingManager.swingPacket(InteractionHand.MAIN_HAND, SwingAction.INTERACT)

        this.applyLocalItemUse(selectedItem.stack, selectedItem.hand)
        InventoryUtils.swapBack(previousSlot, selectedItem)

        ClientContext.rotationManager.requestRotation(rotations, 5000)
        return true
    }

    companion object {
        @JvmStatic
        fun getSplashPotion(stack: ItemStack): Potion? {
            if (!stack.`is`(Items.SPLASH_POTION)) {
                return null
            }

            val contents = stack.get(DataComponents.POTION_CONTENTS) ?: return null
            val potionHolder = contents.potion().orElse(null) ?: return null
            return potionHolder.value()
        }
    }
}
