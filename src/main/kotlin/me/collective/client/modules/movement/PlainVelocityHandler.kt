package me.collective.client.modules.movement

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientPacketListenerHandleExplosionEvent
import me.collective.client.events.ClientPacketListenerHandleSetEntityMotionEvent
import me.collective.client.utils.movement.VelocityModeHandler
import me.collective.client.managers.RotationManager

import net.minecraft.world.phys.Vec3

class PlainVelocityHandler(velocityModule: VelocityModule) : VelocityModeHandler(velocityModule) {
   override fun onExplosion(event: ClientPacketListenerHandleExplosionEvent) {
      if (!velocityModule.explosionsSetting.value) {
         return
      }
      val horizontalPercent = velocityModule.horizontalSetting.value
      val verticalPercent = velocityModule.verticalSetting.value
      val sign = if (velocityModule.inverseSetting.value) -1 else 1

      event.velocityX = (event.velocityX * (horizontalPercent * 0.01F) * sign)
      event.velocityY = (event.velocityY * (verticalPercent * 0.01F))
      event.velocityZ = (event.velocityZ * (horizontalPercent * 0.01F) * sign)
   }

   override fun onEntityMotion(event: ClientPacketListenerHandleSetEntityMotionEvent) {
      if (event.entityId != ClientAccess.collectiveMinecraft.player!!.id) {
         return
      }
      val horizontalPercent = velocityModule.horizontalSetting.value
      val verticalPercent = velocityModule.verticalSetting.value
      val sign = if (velocityModule.inverseSetting.value) -1 else 1
      val velocity: Vec3 = event.velocity
      if (shouldLeaveMotionUnchanged(velocity.length().toFloat())) {
         return
      }
      if (RotationManager.isMovementSyncEnabled() && velocity.x == 0.0 && velocity.z == 0.0 && Math.abs(velocity.y) < 0.0788) {
         return
      }
      event.setVelocity(
         velocity.multiply(
            horizontalPercent * 0.01 * sign,
            (verticalPercent * 0.01F).toDouble(),
            horizontalPercent * 0.01 * sign
         )
      )
      if (horizontalPercent == 0 && verticalPercent == 0) {
         event.cancel()
      }
   }

   private fun shouldLeaveMotionUnchanged(motionLength: Float): Boolean {
      if (velocityModule.lungeMotionPending && motionLength > 0.458F) {
         velocityModule.lungeMotionPending = false
         return true
      }
      val reductionWindow = if (motionLength <= 0.3) 200L else 100L
      return RotationManager.isMovementSyncEnabled() &&
         velocityModule.reduceSetting.value &&
         !ClientContext.serverStateManager.hasElapsedSinceSetback(reductionWindow)
   }
}
