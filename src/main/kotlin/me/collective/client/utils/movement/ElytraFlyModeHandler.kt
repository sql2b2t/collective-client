package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.events.FireworkRocketEntityTickEvent
import me.collective.client.events.LivingEntityRotationEvent
import me.collective.client.events.LocalPlayerMoveEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.RotationRequestEvent
import me.collective.client.modules.movement.ElytraFlyModule

abstract class ElytraFlyModeHandler(@JvmField protected val module: ElytraFlyModule) : ClientAccess {
   open fun onEnable() {
   }

   open fun onDisable() {
   }

   open fun handleFireworkTick(event: FireworkRocketEntityTickEvent) {
   }

   open fun handlePrePlayerTick(event: LocalPlayerPreTickEvent) {
   }

   open fun handleOutgoingPacket(
      event: PacketEvent.Send
   ) {
   }

   open fun handlePlayerTick(event: LocalPlayerTickEvent) {
   }

   open fun handleMovement(event: LocalPlayerMoveEvent) {
   }

   open fun handleTravelRotation(event: LivingEntityRotationEvent) {
   }

   open fun handleClientTick(event: MinecraftRunTickEvent) {
   }

   open fun handleRotationManagerEvent(event: RotationRequestEvent) {
   }

   open fun handleIncomingPacket(
      event: PacketEvent.Receive
   ) {
   }

   open fun handleSendPosition(event: LocalPlayerSendPositionEvent) {
   }
}
