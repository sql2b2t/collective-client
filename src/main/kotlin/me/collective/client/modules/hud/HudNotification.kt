package me.collective.client.modules.hud

import java.awt.Color

open class HudNotification(text: String, durationMillis: Long, color: Color?) {
   @JvmField
   protected var text: String = text

   @JvmField
   protected val color: Color? = color

   @JvmField
   protected var startTimeMillis: Long = System.currentTimeMillis()

   @JvmField
   protected var endTimeMillis: Long = this.startTimeMillis + durationMillis

   fun getStartTimeMillis(): Long {
      return this.startTimeMillis
   }

   fun getText(): String {
      return this.text
   }

   fun getEndTimeMillis(): Long {
      return this.endTimeMillis
   }

   open fun getColor(): Color? {
      return this.color
   }

   open fun update() {
   }
}
