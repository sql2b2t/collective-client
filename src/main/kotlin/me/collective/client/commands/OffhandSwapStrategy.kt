package me.collective.client.commands

import me.collective.client.ClientAccess
import me.collective.client.utils.inventory.InventoryUtils
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.item.Item

class OffhandSwapStrategy : ActionSwapStrategy {
   override fun findSlots(items: Array<Item>): SwapSlots {
      return SwapSlots(InventoryUtils.toMenuSlot(InventoryUtils.findInventorySlot(*items)), 40)
   }

   override fun apply(slots: SwapSlots) {
      val gameMode = ClientAccess.collectiveMinecraft.gameMode
      val player = ClientAccess.collectiveMinecraft.player!!
      val containerId = player.containerMenu.containerId
      gameMode?.handleContainerInput(containerId, slots.sourceSlot, 40, ContainerInput.SWAP, player)
   }

   override fun undo(slots: SwapSlots) {
      val gameMode = ClientAccess.collectiveMinecraft.gameMode
      val player = ClientAccess.collectiveMinecraft.player!!
      val containerId = player.containerMenu.containerId
      gameMode?.handleContainerInput(containerId, slots.sourceSlot, 40, ContainerInput.SWAP, player)
   }
}
