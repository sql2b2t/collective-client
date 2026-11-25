package me.collective.client.modules.hud

import me.collective.client.ClientAccess
import me.collective.client.modules.HudWidgetModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.Setting
import me.collective.client.settings.StringSetting
import me.collective.client.settings.options.HudAnchor
import me.collective.client.CollectiveClient

import java.time.LocalDate
import java.time.Month
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class WatermarkModule : HudWidgetModule("Watermark") {
   @JvmField
   val versionColorSetting: Setting<Boolean>
   private val textSetting: Setting<String>

   private fun getVersionSuffix(): String {
      return " v" + ClientAccess.getVersion()
   }

   fun getVersionText(): String {
      val version: String = ClientAccess.getVersion()
      return "v" + version
   }

   private fun buildWatermarkText(): Component {
      if (aprilFoolsDay) {
         val aprilFoolsText: String = CollectiveClient.NAME + " v" + CollectiveClient.VERSION
         return Component.literal(aprilFoolsText)
      }

      val versionSuffix: String = this.getVersionSuffix()
      val versionComponent: MutableComponent = Component.literal(versionSuffix)
      val versionColorOption: Setting<Boolean> = this.versionColorSetting
      val useWhiteVersion: Boolean = versionColorOption.value
      if (useWhiteVersion) {
         val versionColor: ChatFormatting = ChatFormatting.WHITE
         versionComponent.withStyle(versionColor)
      }

      val configuredText: String = this.textSetting.value
      val trimmedText: String = configuredText.trim()
      val watermark: MutableComponent = Component.literal(trimmedText)
      return watermark.append(versionComponent)
   }

   init {
      this.versionColorSetting = this.add(BooleanSetting("VersionColor", true))
      this.textSetting = this.add(StringSetting("Text", "Collective"))
      val textRow = HudTextRow({ this.buildWatermarkText() }, { true })
      this.setRenderer(HudTextListRenderer(this, textRow))
      this.getRenderer().setAnchor(HudAnchor.TOP_LEFT)
      this.setToggled(true)
   }

   companion object {
      private var aprilFoolsDay: Boolean = false

      init {
         aprilFoolsDay = false
         val today: LocalDate = LocalDate.now()
         aprilFoolsDay = today.month == Month.APRIL && today.dayOfMonth == 1
      }
   }
}
