package me.collective.client.utils.movement

import me.collective.client.ClientAccess
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.ClientPacketListenerHandleSetEntityMotionEvent
import me.collective.client.events.FishingHookPullEntityEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.PacketEvent
import me.collective.client.modules.movement.VelocityModule

abstract class VelocityModeHandler(@JvmField protected val velocityModule: VelocityModule) : ClientAccess {
   open fun onDisable() {
   }

   open fun onExplosion(
      event: ClientPacketListenerHandleExplosionEvent
   ) {
   }

   open fun onEntityMotion(
      event: ClientPacketListenerHandleSetEntityMotionEvent
   ) {
   }

   open fun onSendPacket(
      event: PacketEvent.Send
   ) {
   }

   open fun onUseItemOn(event: MultiPlayerGameModeUseItemOnEvent) {
   }

   open fun onFishingPull(event: FishingHookPullEntityEvent) {
   }

   open fun onSendPosition(event: LocalPlayerSendPositionEvent) {
   }

   open fun onReceivePacket(
      event: PacketEvent.Receive
   ) {
   }

   open fun onTick() {
   }
}
