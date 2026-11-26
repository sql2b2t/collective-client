package me.collective.client.utils.inventory

import me.collective.client.events.InventoryActionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.movement.NoSlowModule

open class NoSlowInventoryMode(
   @JvmField protected val noSlow: NoSlowModule
) {
   open fun onPacketSend(
      event: PacketEvent.Send
   ) {
   }

   open fun onTickStart(event: LocalPlayerPreTickEvent) {
   }

   open fun onInventoryAction(event: InventoryActionEvent) {
   }

   open fun beforeMenuClick(
      event: MultiPlayerGameModeHandleInventoryMouseClickEvent.BeforeContainerClick
   ) {
   }

   open fun onInventoryClickIntercept(
      event: MultiPlayerGameModeHandleInventoryMouseClickEvent.AfterStackHash
   ) {
   }

   open fun onPlayerAiStep() {
   }

   open fun onTickBeforePassengerCheck() {
   }
}
