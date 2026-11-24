package me.collective.client.modules.hud

import me.collective.client.settings.Setting
import me.collective.client.settings.options.WelcomerMode

import java.awt.Color
import net.minecraft.network.chat.Component

class GreetingNotification(welcomer: WelcomerModule?) : HudNotification("", 604800000L, null) {
   private val welcomer: WelcomerModule? = welcomer

   override fun getColor(): Color {
      val colorOwner: WelcomerModule = this.welcomer!!
      val positionOwner: WelcomerModule = this.welcomer!!

      return colorOwner.getHudColor(
         positionOwner.getRenderer()
            .getY()
      )
   }

   override fun update() {
      if (this.welcomer != null) {
         val modeSetting: Setting<WelcomerMode> = this.welcomer.modeSetting
         val modeValue: WelcomerMode = modeSetting.value
         val textOwner: WelcomerModule = this.welcomer
         val greeting: Component = modeValue.createText(textOwner)!!
         this.text = greeting.string
      }
   }
}
