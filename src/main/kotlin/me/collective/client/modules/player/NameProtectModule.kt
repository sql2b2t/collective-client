package me.collective.client.modules.player

import me.collective.client.ClientAccess
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer

class NameProtectModule : Module("NameProtect", "Hides your nickname from the curious people.", ModuleCategory.PLAYER) {

   companion object {

      @JvmField
      var replacementBypassed: Boolean = false
   }

   @JvmField
   val nameSetting: Setting<String>

   @JvmField
   val othersSetting: Setting<Boolean>

   @JvmField
   val skinSetting: Setting<Boolean>

   @JvmField
   val slimSetting: Setting<Boolean>

   init {
      this.nameSetting = this.add(StringSetting("Name", "Me"))
      this.othersSetting = this.add(BooleanSetting("Others", false))
      this.skinSetting = this.add(BooleanSetting("Skin", true))
      this.slimSetting = this.add(BooleanSetting("Slim", false).dependsOn(this.skinSetting))
   }

   fun protectNames(text: String?): String? {
      if (replacementBypassed || text == null || text.isEmpty()) {
         return text
      }
      var nameToReplace: String = ClientAccess.collectiveMinecraft.user.name

      val protectedName: String = this.getProtectedName()
      var protectedText: String = this.replaceWholeName(text, nameToReplace, protectedName)
      if ((this.othersSetting.value!!) && ClientAccess.collectiveMinecraft.level != null) {
         val level: ClientLevel = ClientAccess.collectiveMinecraft.level!!
         val players: Iterator<out AbstractClientPlayer> = level.players().iterator()

         while (players.hasNext()) {
            nameToReplace = players.next().gameProfile.name

            protectedText = this.replaceWholeName(protectedText, nameToReplace, "redacted")
         }
      }

      return protectedText
   }

   private fun replaceWholeName(text: String, name: String?, replacement: String): String {
      if (name == null || name.isEmpty()) {
         return text
      }

      val result = StringBuilder(text.length)
      var cursor: Int = 0
      val nameLength: Int = name.length
      while (cursor < text.length) {
         val matchStart: Int = text.indexOf(name, cursor)
         if (matchStart == -1) {
            result.append(text, cursor, text.length)
            break
         }

         val matchEnd: Int = matchStart + nameLength
         val leftBoundary: Boolean = matchStart == 0 || !Character.isLetterOrDigit(text[matchStart - 1])
         val rightBoundary: Boolean = matchEnd == text.length || !Character.isLetterOrDigit(text[matchEnd])
         if (leftBoundary && rightBoundary) {
            result.append(text, cursor, matchStart)
            result.append(replacement)
            cursor = matchEnd
         } else {
            result.append(text, cursor, matchStart + 1)
            cursor = matchStart + 1
         }
      }
      return result.toString()
   }

   fun getProtectedName(): String {
      val name: String = this.nameSetting.value!!
      return if (name.length > 16) name.substring(0, 16) else name
   }

}
