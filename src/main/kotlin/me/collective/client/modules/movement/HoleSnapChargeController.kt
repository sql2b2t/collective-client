package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import net.minecraft.client.player.LocalPlayer

class HoleSnapChargeController(
   private val module: HoleSnapModule
) : ClientAccess {
   private var chargeTicks: Int = 0
   private var drainingCharge: Boolean = false

   init {
      ClientAccess.eventBus.subscribe(this)
   }

   @EventHandler
   private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
      if (ClientContext.playerTickController.multiplier > 1.0F) {
         drainingCharge = true
      }
      val moving = isPlayerMoving()
      if (!moving) {
         drainingCharge = false
      }
      if (moving && drainingCharge) {
         chargeTicks = Math.max(0, chargeTicks - 1)
      } else {
         val maximumCharge = module.chargeSetting.value
         chargeTicks = Math.min(maximumCharge, chargeTicks + 1)
      }
   }

   fun requestShift() {
      if (chargeTicks <= 0 || !isPlayerMoving() ||
         !ClientAccess.collectiveMinecraft.player!!.onGround() ||
         ClientContext.playerStateTracker.getGroundedTicks() <= 1) {
         return
      }
      val factor = module.factorSetting.value.toFloat()
      ClientContext.playerTickController.requestMultiplier(module, factor)
   }

   private fun isPlayerMoving(): Boolean {
      val player: LocalPlayer = ClientAccess.collectiveMinecraft.player!!
      return player.position().distanceToSqr(player.xo, player.yo, player.zo) > 0.0
   }
}
