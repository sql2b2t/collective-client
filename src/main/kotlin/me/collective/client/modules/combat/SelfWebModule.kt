package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.RotationRequestEvent
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.math.MathConstants
import me.collective.client.utils.world.BlockInteractionUtils
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import java.util.Comparator
import java.util.stream.StreamSupport

class SelfWebModule : Module("SelfWeb", "Places a cobweb at your feet.", ModuleCategory.COMBAT) {
    private val rotateSetting: Setting<Boolean> = add(BooleanSetting("Rotate", true))
    private val autoDisableSetting: Setting<Boolean> = add(BooleanSetting("AutoDisable", true))
    private val onlyHoleSetting: Setting<Boolean> = add(BooleanSetting("OnlyHole", false))
    internal val smartSetting: Setting<Boolean> = add(BooleanSetting("Smart", false).markVisibilityDependency())
    protected val enemyRangeSetting: Setting<Float> = add(NumberSetting("EnemyRange", 3.0f, 1.0f, 6.0f) { smartSetting.isExpanded }.withUnit("m"))
    protected val aggressiveSetting: Setting<Boolean> = add(BooleanSetting("Aggressive", false).dependsOn(smartSetting))

    private fun findNearestEnemy(rangeSetting: Setting<Float>): Entity? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val level = ClientAccess.collectiveMinecraft.level ?: return null

        val enemyStream = StreamSupport.stream(level.entitiesForRendering().spliterator(), false).filter { candidate ->
            if (candidate === player) {
                return@filter false
            }

            val distanceToEnemy = player.distanceTo(candidate)
            if (distanceToEnemy > rangeSetting.value) {
                return@filter false
            }
            if (!meetsHeightPolicy(candidate) || candidate !is Player) {
                return@filter false
            }

            val candidateName = candidate.name.string
            !ClientContext.socialRegistry.isFriendName(candidateName) && candidate.isAlive
        }

        return enemyStream.min(Comparator.comparingDouble { player.distanceToSqr(it) }).orElse(null)
    }

    @EventHandler
    private fun onPlayerUpdate(event: RotationRequestEvent) {
        val nearestEnemy = findNearestEnemy(enemyRangeSetting)

        if (!smartSetting.value || nearestEnemy != null) {
            placeWeb(event)
        }
    }

    private fun meetsHeightPolicy(candidate: Entity): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        return if (aggressiveSetting.value) {
            candidate.y >= player.y
        } else {
            candidate.y > player.y
        }
    }

    private fun placeWeb(event: RotationRequestEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val level = ClientAccess.collectiveMinecraft.level ?: return

        if (player.isPassenger) {
            return
        }

        if (!ClientContext.holeManager.isInHole && onlyHoleSetting.value) {
            return
        }

        val feetPosition = BlockPos.containing(player.position())
        val feetState = level.getBlockState(feetPosition)

        if (!feetState.canBeReplaced() || feetState.liquid() || !player.onGround()) {
            return
        }

        val webSlot = InventoryUtils.findHotbarSlot(Items.COBWEB)
        val previousSlot = player.inventory.selectedSlot
        if (webSlot == -1) {
            val outOfBlocksMessage = Component.literal(name).append(" is out of blocks!")
            ChatUtils.printChat(outOfBlocksMessage, ChatUtils.createSignature(-2), ChatUtils.MessagePriority.HIGH)
            disable()
            return
        }

        val placementDirection = BlockInteractionUtils.findSupportDirection(feetPosition) ?: return

        InventoryUtils.selectHotbarSlotForPlacement(webSlot)

        val previousBounds = player.boundingBox
        player.boundingBox = AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        try {
            BlockInteractionUtils.placeBlockIgnoringCrystals(
                feetPosition, placementDirection, false, InteractionHand.MAIN_HAND
            )
        } finally {
            player.boundingBox = previousBounds
            InventoryUtils.selectHotbarSlotForPlacement(previousSlot)
        }

        if (rotateSetting.value) {
            val rotations = floatArrayOf(event.playerYaw, MathConstants.NINETY_DEGREES.toFloat())
            ClientContext.rotationManager.requestRotation(rotations, 5)
        }

        if (autoDisableSetting.value) {
            disable()
        }
    }
}
