package me.collective.client.modules.hud

import me.collective.client.ClientContext
import me.collective.client.managers.ServerStateManager

import java.awt.Color

class LagNotification : HudNotification("", 3600000L, Color(128, 128, 128, 255)) {
   private var finished: Boolean = false
   private var lagSeconds: Float = 0.0f

   override fun update() {
      val nowMillis: Long = System.currentTimeMillis()
      val networkActivity: ServerStateManager = ClientContext.serverStateManager
      val idleMillis: Long = nowMillis - networkActivity.getLastPacketTime()
      if (idleMillis < 1000L && !this.finished) {
         val completionTimeMillis: Long = System.currentTimeMillis()
         this.endTimeMillis = completionTimeMillis + 500L
         this.finished = true
      }

      if (!this.finished) {
         val idleMillisAsFloat: Float = idleMillis.toFloat()
         this.lagSeconds = idleMillisAsFloat / 1000.0f
      }

      this.text = lagFormat.format(this.lagSeconds)
   }

   fun finish() {
      if (!this.finished) {
         val nowMillis: Long = System.currentTimeMillis()
         this.endTimeMillis = nowMillis + 500L
         this.finished = true
      }
   }

   companion object {
      private const val lagFormat: String = "The server is not responding for %.1fs"
   }
}
