package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.events.PacketEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.settings.options.WarpRechargeChoice
import java.util.LinkedList
import java.util.Queue
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

class WarpRechargeController(warp: WarpModule) : ClientAccess {
   @JvmField
   val pendingPongs: Queue<ServerboundPongPacket> = LinkedList()

   private val warp: WarpModule

   init {
      ClientAccess.eventBus.subscribe(this)
      this.warp = warp
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (this.warp.hasDisplacement() || this.warp.modeSetting.value != WarpModule.ModeChoice.PLAIN) {
         return
      }
      val maximumCharge = this.warp.chargeSetting.value
      val increasedCharge = this.warp.plainCharge + this.warp.chargeSpeedSetting.value
      this.warp.plainCharge = Math.min(maximumCharge, increasedCharge)
   }

   @EventHandler
   private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
      if (this.isWorldUnavailable()) {
         return
      }

      if (this.warp.isToggled() &&
         this.warp.modeSetting.value == WarpModule.ModeChoice.ALTERNATIVE &&
         event.packet is ServerboundPongPacket
      ) {
         val pong = event.packet as ServerboundPongPacket
         val pendingCount = this.pendingPongs.size
         val chargeLimit: Int = this.warp.chargeSetting.value
         val moving = this.warp.hasMovementInputOrDisplacement()
         // Откладываем pong только когда реально сохраняем его в очередь: раньше
         // event.cancel() срабатывал и без add() (полная очередь / движение при
         // непустой очереди) и сервер не получал Pong — кик по таймауту.
         if (pendingCount < chargeLimit && (!moving || !this.pendingPongs.isEmpty())) {
            event.cancel()
            this.pendingPongs.add(pong)
         }
      }

      if (this.warp.modeSetting.value != WarpModule.ModeChoice.PLAIN ||
         event.packet !is ServerboundMovePlayerPacket
      ) {
         return
      }

      if (!this.warp.hasDisplacement() || this.warp.plainCharge == 0) {
         return
      }
      if (!this.warp.isToggled() && this.warp.rechargeSetting.value == WarpRechargeChoice.HOLD) {
         return
      }

      val decreasedCharge = this.warp.plainCharge - 1
      this.warp.plainCharge = Math.max(0, decreasedCharge)
   }
}
