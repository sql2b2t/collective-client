package me.collective.client.modules.misc


import java.util.concurrent.ThreadLocalRandom
import java.util.function.Predicate
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.EnumSetting
import me.collective.client.settings.NamedEntry
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import net.minecraft.network.chat.Component

class CustomDeathTextModule : Module("CustomDeathText", "Displays a custom message on your death screen.", ModuleCategory.MISC) {
   val modeSetting: Setting<ModeChoice>
   private val messageSetting: Setting<String>

   init {
      this.modeSetting = this.add(EnumSetting<ModeChoice>("Mode", ModeChoice.CUSTOM))
      val customMode = Predicate<Any> { ignored -> this.modeSetting.value == ModeChoice.CUSTOM }
      this.messageSetting = this.add(StringSetting("Message", "Uh oh.", customMode))
      this.setDrawn(false)
   }

   fun getDeathText(): Component {
      val modeSettingSnapshot = this.modeSetting
      if (modeSettingSnapshot.value == ModeChoice.CUSTOM) {
         val messageSettingSnapshot = this.messageSetting
         val message: String = messageSettingSnapshot.value
         val trimmedMessage = message.trim { it <= ' ' }
         return Component.nullToEmpty(trimmedMessage)
      } else {
         val messagesSnapshot = randomMessages
         val randomSnapshot = ThreadLocalRandom.current()
         val messageCount = randomMessages.size
         val randomMessage = messagesSnapshot[randomSnapshot.nextInt(messageCount)]
         return Component.literal(randomMessage)
      }
   }

   enum class ModeChoice(private val displayName: String) : NamedEntry {
      CUSTOM("Custom"),
      RANDOM("Random");

      override fun getName(): String {
         return this.displayName
      }
   }

   companion object {
      private val randomMessages: Array<String> = arrayOf(
         "YOU USELESS SHITSTAIN",
         "GAME OVER MOTHERFUCKER! YOU SUCK!",

         "HOW THE HELL ARE YOU SO BAD AT THIS GAME?",
         "HOW DO YOU FUCK UP SO BADLY JESUS FUCKING CHRIST?",
         "YOU DIED! CONGRATS FUCKFACE",
         "OH MY GOD YOU SUCK! AUTO-UNINSTALLING",

         "HOLY FUCKING SHIT SERIOUSLY",
         "YOU FUCKING RETARD!",

         "YOU DENSE FUCK"
      )
   }
}
