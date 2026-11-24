package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.managers.RotationManager
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.managers.WorldStateManager

import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Objects
import java.util.Queue
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.tags.ItemTags
import net.minecraft.tags.TagKey
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.EnderChestBlockEntity
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

class ShulkerReturnController(module: ChestStealerModule) : ChestStealerController(module) {
   private val trackedShulkers: MutableList<TrackedShulker> = ArrayList()
   private val pendingShulkers: Queue<TrackedShulker> = ArrayDeque()
   private var reopenRequested: Boolean = false
   private var handlingReturnClick: Boolean = false
   private var returnedThisTick: Boolean = false
   private var leftEnderChest: Boolean = false

   private fun isPlayerInventorySlot(slotIndex: Int): Boolean {
      return slotIndex >= 0 &&
         slotIndex <= (26)
   }

   fun onContainerSlotUpdate(containerId: Int, stack: ItemStack) {
      val screen: Screen? = ClientAccess.collectiveMinecraft.screen
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         if (containerId == ClientAccess.collectiveMinecraft.player!!.inventoryMenu.containerId) {
            for (trackedShulker in this.trackedShulkers) {
               if (trackedShulker.matchesItemAndName(stack)) {
                  this.reopenRequested = true
               }
            }
         }
      }
   }

   fun onInventoryMouseClick(
      event: MultiPlayerGameModeHandleInventoryMouseClickEvent
   ) {
      val screen: Screen? = ClientAccess.collectiveMinecraft.screen
      if (InventoryUtils.isEnderChestScreen(screen) &&
         !this.handlingReturnClick) {
         val menu: AbstractContainerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
         val clickedSlotId: Int = event.slotId
         if (clickedSlotId < 0 || clickedSlotId >= menu.slots.size) {
            // Клик вне окна (slotId == -999: выброс, курсор) — раньше menu.getSlot падал с IndexOutOfBounds.
            return
         }
         val slot: Slot = menu.getSlot(clickedSlotId)
         val trackedShulker = TrackedShulker(
            getTrackedItem(slot), slot.getItem().get(DataComponents.CUSTOM_NAME), event.slotId
         )
         val clickedSlot: Int = event.slotId
         if (this.isPlayerInventorySlot(clickedSlot)) {
            val clickedStack: ItemStack = slot.getItem()
            val shulkerTag: TagKey<Item> = ItemTags.SHULKER_BOXES
            if (clickedStack.`is`(shulkerTag)) {
               if (event.clickType == ClickType.SWAP) {
                  if (event.button < (9)) {
                     this.pendingShulkers.add(trackedShulker)
                  }
               }

               if (event.clickType == ClickType.QUICK_MOVE) {
                  this.pendingShulkers.add(trackedShulker)
               }

               if (event.clickType == ClickType.PICKUP) {
                  // Ручной PICKUP снимает с автовозврата только тот шалкер,
                  // по которому кликнули: poll() сносил голову FIFO и терял
                  // невозвращённый шалкер.
                  this.pendingShulkers.removeIf { pending: TrackedShulker -> pending.matchesItemAndName(clickedStack) }
               }

               return
            }
         }
      }
   }

   private fun findTrackedShulkerSlot(trackedShulker: TrackedShulker): Int {
      var slotIndex = 27
      while (true) {
         if (slotIndex > (62)) {
            return -1
         }

         val stack: ItemStack = ClientAccess.collectiveMinecraft.player!!.containerMenu.getSlot(slotIndex).getItem()
         if (trackedShulker.matchesItemAndName(stack)) {
            return slotIndex
         }

         slotIndex++
      }
   }

   fun hasReturnedThisTick(): Boolean {
      return this.returnedThisTick
   }

   fun reopenRequestedShulker() {
      if (!this.reopenRequested) {
         return
      }
      val position: BlockPos? = this.findReopenPosition()
      if (position != null) {
         val fallbackDirection: Direction = Direction.UP
         val direction: Direction = BlockInteractionUtils.getStrictDirectionsOrFallback(position, fallbackDirection).first()

         val hitOffset = 0.5
         val hitResult = BlockHitResult(position.center.relative(direction, hitOffset), direction, position, false)
         ClientAccess.collectiveMinecraft.player!!.closeContainer()
         val interactionHand: InteractionHand = InteractionHand.MAIN_HAND
         PacketSender.sendUseItemOn(interactionHand, hitResult)
         val swingManager: SwingInteractionManager = ClientContext.swingManager
         val swingHand: InteractionHand = InteractionHand.MAIN_HAND
         val swingType: SwingAction = SwingAction.INTERACT
         swingManager.swing(swingHand, swingType)
         val rotationManager: RotationManager = ClientContext.rotationManager

         val rotations: FloatArray = RotationManager.getRotationsToBlock(position)
         rotationManager.requestRotation(rotations, 250)
      }
      this.reopenRequested = false
   }

   private fun findReopenPosition(): BlockPos? {
      val lastInteracted: BlockPos? = ChestStealerModule.getLastInteractedBlock(this.module)
      if (lastInteracted != null && this.isWithinReopenRange(lastInteracted) &&
         ClientAccess.collectiveMinecraft.level!!.getBlockState(lastInteracted).`is`(Blocks.ENDER_CHEST)) {
         return lastInteracted
      }
      var nearest: BlockPos? = null
      var nearestDistance = Double.MAX_VALUE
      val containerTracker: WorldStateManager = ClientContext.worldStateManager
      for (blockEntity in containerTracker.blockEntities) {
         if (blockEntity !is EnderChestBlockEntity) {
            continue
         }
         val candidate: BlockPos = blockEntity.blockPos
         val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
         val eyePosition: Vec3 = player.eyePosition
         val center: Vec3 = candidate.center
         val distance: Double = eyePosition.distanceTo(center)
         if (distance <= 5.0 && distance < nearestDistance) {
            nearestDistance = distance
            nearest = candidate
         }
      }
      return nearest
   }

   private fun isWithinReopenRange(position: BlockPos): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      val eyePosition: Vec3 = player.eyePosition
      val center: Vec3 = position.center
      return eyePosition.distanceTo(center) <= 5.0
   }

   fun tick() {
      this.returnedThisTick = false
      val screen: Screen? = ClientAccess.collectiveMinecraft.screen
      if (!InventoryUtils.isEnderChestScreen(screen)) {
         this.leftEnderChest = true
         while (!this.pendingShulkers.isEmpty()) {
            this.trackedShulkers.add(this.pendingShulkers.poll())
         }
      } else if (!this.trackedShulkers.isEmpty() && this.leftEnderChest) {
         if (!this.module.hasScreenOpenTimedOut()) {
            return
         }
         val playerContainerMenu = ClientAccess.collectiveMinecraft.player!!.containerMenu
         if (playerContainerMenu is ChestMenu) {
            val menu = playerContainerMenu as ChestMenu
            if (menu.rowCount == (3)) {
               val inventoryCoordinator: InventoryStateManager = ClientContext.inventoryStateManager

               if (!InventoryStateManager.wouldReachClickLimit(inventoryCoordinator, (1))) {
                  this.handlingReturnClick = true
                  val returnedShulkers: MutableList<TrackedShulker> = ArrayList()
                  for (selectedShulker in this.trackedShulkers) {
                     val matchingSlot = this.findTrackedShulkerSlot(selectedShulker)
                     if (matchingSlot != -1) {
                        if (matchingSlot >= (54) &&
         matchingSlot <= (62)) {
                           val originalSlot: Int = selectedShulker.slotIndex
                           val hotbarSlot: Int = matchingSlot - (54)
                           InventoryUtils.swapWithHotbarSlot(originalSlot, hotbarSlot)
                        } else {
                           val originalSlot: Int = selectedShulker.slotIndex
                           InventoryUtils.swapSlotsWithPickup(matchingSlot, originalSlot)
                        }

                        returnedShulkers.add(selectedShulker)
                        this.returnedThisTick = true
                     }
                  }

                  // Убираем только реально возвращённые: невозвращённые
                  // (offhand, курсор, рассинхрон) остаются до следующего
                  // открытия эндер-сундука, а не выбрасываются из отслеживания.
                  this.trackedShulkers.removeAll(returnedShulkers.toSet())
                  this.handlingReturnClick = false
                  this.leftEnderChest = false
               }
            }
         }
      }
   }

   private class TrackedShulker(
      val item: Item,
      val customName: Component?,
      val slotIndex: Int
   ) {
      fun matchesItemAndName(stack: ItemStack): Boolean {
         if (stack.getItem() !== this.item) {
            return false
         }
         val stackName: Component? = stack.get(DataComponents.CUSTOM_NAME)
         return Objects.equals(stackName, this.customName)
      }
   }

   companion object {
      @JvmStatic
      private fun getTrackedItem(slot: Slot): Item {
         return slot.getItem().getItem()
      }
   }
}
