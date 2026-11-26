package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.managers.TaskScheduler
import me.collective.client.managers.RotationManager
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Item
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class ShulkerPlacementController(module: ChestStealerModule) : ChestStealerController(module) {

   fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val hand: InteractionHand = event.hand
      val heldStack: ItemStack = player.getItemInHand(hand)
      val shulkerTag: TagKey<Item> = ItemTags.SHULKER_BOXES
      if (heldStack.`is`(shulkerTag)) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val clickedPosition: BlockPos = event.hitResult.blockPos
         val clickedBlock: Block = level.getBlockState(clickedPosition).block
         if (BlockInteractionUtils.isInteractiveBlock(clickedBlock)) {
            val sneakingPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            if (!sneakingPlayer.isShiftKeyDown) {
               return
            }
         }

         val scheduler: TaskScheduler = ClientContext.taskScheduler
         val openPlacedShulker = Runnable {
            val placedHit: BlockHitResult = event.hitResult
            val clickedBlockPosition: BlockPos = placedHit.blockPos
            val placementDirection: Direction = placedHit.direction
            val shulkerPosition: BlockPos = clickedBlockPosition.relative(placementDirection)
            val fallbackDirection: Direction = Direction.UP
            val openDirection: Direction = BlockInteractionUtils.getStrictDirectionsOrFallback(shulkerPosition, fallbackDirection).first()

            val hitPosition: Vec3 = Vec3.atCenterOf(shulkerPosition).relative(openDirection, 0.5)
            val openHit = BlockHitResult(hitPosition, openDirection, shulkerPosition, false)
            val openHand: InteractionHand = event.hand
            PacketSender.sendUseItemOn(openHand, openHit)
            val rotationManager: RotationManager = ClientContext.rotationManager

            val rotations: FloatArray = RotationManager.getRotationsToBlock(shulkerPosition)
            rotationManager.requestRotation(rotations, 1000)
         }
         scheduler.scheduleRotationTask(openPlacedShulker, 2)
      }
   }
}
