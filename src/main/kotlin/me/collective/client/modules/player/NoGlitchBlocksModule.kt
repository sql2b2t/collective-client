package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.events.EventHandler
import me.collective.client.events.MultiPlayerGameModeDestroyBlockEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting

import net.minecraft.ChatFormatting
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult

class NoGlitchBlocksModule : Module(
   "NoGlitchBlocks", "Makes sure there are no ghost blocks as you interact with them. \n" +
         ChatFormatting.RED.toString() + "Not recommended on 2b2t/Grim servers.", ModuleCategory.PLAYER
) {
   private val placeSetting: Setting<Boolean>

   @JvmField
   val breakSetting: Setting<Boolean>

   init {
      val defaultPlace: Boolean = true
      val placeOption = BooleanSetting("Place", defaultPlace)
      this.placeSetting = this.add(placeOption)
      val defaultBreak: Boolean = true
      val breakOption = BooleanSetting("Break", defaultBreak)
      this.breakSetting = this.add(breakOption)
   }

   @EventHandler
   private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
      val interactionHand: InteractionHand = event.hand
      val heldItem: Item = ClientAccess.collectiveMinecraft.player!!.getItemInHand(interactionHand).item
      if ((this.placeSetting.value!!) && heldItem is BlockItem) {
         val blockItem: BlockItem = heldItem
         val clickedPos: BlockPos = event.hitResult.blockPos
         val clickedFace: Direction = event.hitResult.direction
         val placementPos: BlockPos = clickedPos.relative(clickedFace)
         val placedBlock: Block = blockItem.block
         if (BlockInteractionUtils.canPlaceBlock(placementPos, placedBlock, false)) {
            val packetHand: InteractionHand = event.hand
            val packetHit: BlockHitResult = event.hitResult
            PacketSender.sendUseItemOn(packetHand, packetHit)
            val playerForSwing: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val swingHand: InteractionHand = event.hand
            playerForSwing.swing(swingHand)
            event.cancel()
         }
      }
   }

   @EventHandler
   private fun onMultiPlayerGameModeDestroyBlockEvent(event: MultiPlayerGameModeDestroyBlockEvent) {
      if (this.breakSetting.value!!) {
         event.cancel()
      }
   }

}
