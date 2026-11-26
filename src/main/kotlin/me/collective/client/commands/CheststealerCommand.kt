package me.collective.client.commands

import net.minecraft.world.phys.Vec3
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.managers.InventoryStateManager
import me.collective.client.managers.RotationManager
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.entity.EnderChestBlockEntity
import net.minecraft.world.phys.BlockHitResult
import java.util.Comparator

class CheststealerCommand : Command("cheststealer") {

    @Suppress("UNCHECKED_CAST")
    override fun exec(builder: LiteralArgumentBuilder<*>) {
        val root = builder as LiteralArgumentBuilder<Any?>
        val openBuilder = literal<Any?>("open")
        val openCommand = { _: com.mojang.brigadier.context.CommandContext<Any?> ->
            val scheduler = ClientContext.taskScheduler
            val openTask = Runnable {
                openEnderChest(true)
            }
            scheduler.scheduleRotationTask(openTask, 0)
            1
        }
        val openBranch = openBuilder.executes(openCommand)
        root.then(openBranch)
    }

    private fun findOpenableEnderChest(): BlockPos? {
        val worldState = ClientContext.worldStateManager
        for (blockEntity in worldState.blockEntities) {
            if (blockEntity is EnderChestBlockEntity && hasReachAndClearance(blockEntity.blockPos)) {
                return blockEntity.blockPos
            }
        }
        return null
    }

    private fun findPlacementPosition(): BlockPos? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val eyePosition = player.eyePosition
        val positions = WorldUtils.streamSphereBlocks(eyePosition, 4.0f)
        val distanceComparator = Comparator.comparingDouble<BlockPos> { pos ->
            val p = ClientAccess.collectiveMinecraft.player ?: return@comparingDouble Double.MAX_VALUE
            pos.distToCenterSqr(p.eyePosition)
        }
        return positions
            .filter { WorldUtils.isReplaceable(it) && hasReachAndClearance(it) }
            .filter { canPlaceEnderChest(it) }
            .min(distanceComparator)
            .orElse(null)
    }

    private fun openEnderChest(allowPlacement: Boolean) {
        val existingChest = findOpenableEnderChest()
        if (existingChest != null) {
            val interactionDirections = BlockInteractionUtils.getStrictDirectionsOrFallback(existingChest, Direction.UP)
            val interactionDirection = interactionDirections.first()
            val chestCenter = Vec3.atCenterOf(existingChest)
            val interactionPoint = chestCenter.relative(interactionDirection, 0.5)
            val hitResult = BlockHitResult(interactionPoint, interactionDirection, existingChest, false)
            PacketSender.sendUseItemOn(InteractionHand.MAIN_HAND, hitResult)
            PacketSender.sendSwing(InteractionHand.MAIN_HAND)
            val openRotations = RotationManager.getRotationsToPosition(interactionPoint)
            ClientContext.rotationManager.requestRotation(openRotations, 5000)
        } else if (allowPlacement) {
            val originalSlot = InventoryUtils.getSelectedSlot()
            val includeInventory = !InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 1)
            val enderChestSlot = InventoryUtils.findSwapSlot(Items.ENDER_CHEST, includeInventory)
            val placementPosition = findPlacementPosition()
            if (placementPosition != null && enderChestSlot.isFound()) {
                val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
                val supportDirection = BlockInteractionUtils.findSupportDirection(placementPosition, strictDirection)
                if (supportDirection != null) {
                    val supportPosition = placementPosition.relative(supportDirection)
                    val supportCenter = Vec3.atCenterOf(supportPosition)
                    val faceForHitPoint = supportDirection.opposite
                    val placementHitPoint = supportCenter.relative(faceForHitPoint, 0.5)
                    InventoryUtils.swapTo(enderChestSlot)
                    BlockInteractionUtils.placeBlock(placementPosition, null, supportDirection, false, InteractionHand.MAIN_HAND)
                    InventoryUtils.swapBack(originalSlot, enderChestSlot)
                    val placementRotations = RotationManager.getRotationsToPosition(placementHitPoint)
                    ClientContext.rotationManager.requestRotation(placementRotations, 5000)
                    val scheduler = ClientContext.taskScheduler
                    val reopenTask = Runnable {
                        openEnderChest(false)
                    }
                    scheduler.scheduleRotationTask(reopenTask, 0)
                }
            }
        }
    }

    private fun hasReachAndClearance(position: BlockPos): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val distance = player.eyePosition.distanceTo(Vec3.atCenterOf(position))
        if (distance > 4.0) {
            return false
        }
        val abovePosition = position.above()
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val aboveBlockState = level.getBlockState(abovePosition)
        val aboveCollision = aboveBlockState.getCollisionShape(level, abovePosition)
        return aboveCollision.isEmpty
    }

    private fun canPlaceEnderChest(position: BlockPos): Boolean {
        val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
        if (BlockInteractionUtils.findSupportDirection(position, strictDirection) == null) {
            return false
        }
        return BlockInteractionUtils.canPlaceAt(position, true, false)
    }
}
