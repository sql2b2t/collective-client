package me.collective.client.utils.movement

import me.collective.client.utils.ElapsedTimer
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.MinecraftRunTickEvent

import java.util.ArrayDeque
import net.minecraft.world.entity.Entity

open class MovementSpeedTracker : ClientAccess {
   private val speedSamples = ArrayDeque<Double>()
   private val displayTimer = ElapsedTimer()
   var averageSpeed: Double = 0.0
      private set
   var displaySpeed: Double = 0.0
      private set
   var currentSpeed: Double = 0.0
      private set

   init {
      ClientAccess.eventBus.subscribe(this)
   }

   fun calculateCurrentSpeed(): Double {
      val player = ClientAccess.collectiveMinecraft.player ?: return 0.0
      val entity: Entity = if (player.isPassenger) player.vehicle ?: player else player
      return Math.hypot(entity.x - entity.xo, entity.z - entity.zo) * 20.0 * 3.6
   }

   @EventHandler
   private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
      averageSpeed = 0.0
      displaySpeed = 0.0
      speedSamples.clear()
      displayTimer.reset()
   }

   @EventHandler
   private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
      if (!event.hasPendingTicks || isWorldUnavailable()) {
         return
      }
      val player = ClientAccess.collectiveMinecraft.player ?: return
      currentSpeed = calculateCurrentSpeed()
      if (currentSpeed > 0.0 || player.tickCount % 4 == 0) {
         speedSamples.add(currentSpeed * ClientContext.tickRateController.multiplier)
      } else {
         speedSamples.pollFirst()
      }
      while (speedSamples.size > 9) {
         speedSamples.pollFirst()
      }
      var total = 0.0
      for (speed in speedSamples) {
         total += speed
      }
      averageSpeed = if (speedSamples.isEmpty()) 0.0 else total / speedSamples.size
      if (averageSpeed < 21.0) {
         displaySpeed = averageSpeed
      } else if (displayTimer.hasElapsed(50L)) {
         displaySpeed = averageSpeed
         displayTimer.reset()
      }
   }

}
