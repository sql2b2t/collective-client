package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.managers.RotationManager
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.world.WorldUtils

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.world.item.Item
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class ShulkerOpenController(module: ChestStealerModule) : ChestStealerController(module) {
   private var pendingOpen: Boolean = false

   private fun isUsableShulker(position: BlockPos): Boolean {
      val ignoreCrystals: Boolean = false
      val playersOnly: Boolean = true
      if (!BlockInteractionUtils.canPlaceAt(position, playersOnly, ignoreCrystals)) {
         return false
      }

      val strictDirection: Boolean = true
      val supportDirection: Direction = BlockInteractionUtils.findSupportDirection(position, strictDirection) ?: return false

      val supportPosition: BlockPos = position.relative(supportDirection)

      if (BlockInteractionUtils.requiresSneakForInteraction(supportPosition)) {
         return false
      }

      val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
      val openDirection: Direction = supportDirection.opposite
      val openPosition: BlockPos = position.relative(openDirection)
      return level.getBlockState(openPosition).isAir
   }

   @EventHandler
   fun openPendingShulker() {
      val screen: Screen? = ClientAccess.collectiveMinecraft.gui.screen()
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val heldStack: ItemStack = player.mainHandItem
         val shulkerTag: TagKey<Item> = ItemTags.SHULKER_BOXES
         if (heldStack.`is`(shulkerTag) && this.pendingOpen) {
            val position: BlockPos = this.findShulkerToOpen() ?: return
            val strictDirection: Boolean = true
            val direction: Direction = BlockInteractionUtils.findSupportDirection(position, strictDirection)!!
            val supportPosition: BlockPos = position.relative(direction)
            val hitDirection: Direction = direction.opposite
            val supportCenter: Vec3 = Vec3.atCenterOf(supportPosition)

            val hitOffset: Double = 0.5
            val hitPosition: Vec3 = supportCenter.relative(hitDirection, hitOffset)
            val gameMode: MultiPlayerGameMode = ClientAccess.collectiveMinecraft.gameMode!!
            val interactionPlayer: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
            val interactionHand: InteractionHand = InteractionHand.MAIN_HAND
            val hitResult = BlockHitResult(hitPosition, hitDirection, supportPosition, false)
            gameMode.useItemOn(interactionPlayer, interactionHand, hitResult)
            val swingManager: SwingInteractionManager = ClientContext.swingManager
            val swingHand: InteractionHand = InteractionHand.MAIN_HAND
            val swingType: SwingAction = SwingAction.INTERACT
            swingManager.swing(swingHand, swingType)
            val rotationManager: RotationManager = ClientContext.rotationManager

            val rotations: FloatArray = RotationManager.getRotationsToBlock(supportPosition)
            rotationManager.requestRotation(rotations, 1000)
            this.pendingOpen = false
         }
      }
   }

   private fun findShulkerToOpen(): BlockPos? {
      val stealerModule: ChestStealerModule = this.module

      val center: BlockPos? = ChestStealerModule.getLastInteractedBlock(stealerModule)
      val searchOrigin: Vec3
      if (center == null) {
         searchOrigin = ClientAccess.collectiveMinecraft.player!!.eyePosition
      } else {
         searchOrigin = net.minecraft.world.phys.Vec3.atBottomCenterOf(center)
      }

      val searchCenter: Vec3 = ClientAccess.collectiveMinecraft.player!!.eyePosition

      val searchRadius: Float = 4.0f
      val usableCandidates: java.util.stream.Stream<BlockPos> = WorldUtils.streamSphereBlocks(searchCenter, searchRadius).filter(this::isUsableShulker)
      val byDistance: Comparator<BlockPos> = Comparator.comparingDouble<BlockPos> { candidate: BlockPos -> searchOrigin.distanceToSqr(Vec3.atCenterOf(candidate)) }
      return usableCandidates.min(byDistance).orElse(null)
   }

   fun requestOpen() {
      this.pendingOpen = true
   }

}
