package me.collective.client.modules.render

import me.collective.client.render.ChamsColorProvider
import me.collective.client.settings.NamedEntry
import java.awt.Color

enum class ChamsWireframeChoice(private val displayName: String) : ChamsColorProvider, NamedEntry {
   BOTH("Both") {
      override fun getColors(chams: ChamsModule): Array<Color> {
         val colors = arrayOfNulls<Color>(2)
         val lineSettingSnapshot = chams.popLineSetting
         colors[0] = lineSettingSnapshot.value
         val fillSettingSnapshot = chams.popFillSetting
         colors[1] = fillSettingSnapshot.value
         @Suppress("UNCHECKED_CAST")
         return colors as Array<Color>
      }
   },
   FILL("Fill") {
      override fun getColors(chams: ChamsModule): Array<Color> {
         val colors = arrayOfNulls<Color>(2)
         colors[0] = ChamsColorProvider.TRANSPARENT_COLOR
         val fillSettingSnapshot = chams.popFillSetting
         colors[1] = fillSettingSnapshot.value
         @Suppress("UNCHECKED_CAST")
         return colors as Array<Color>
      }
   },
   LINE("Line") {
      override fun getColors(chams: ChamsModule): Array<Color> {
         val colors = arrayOfNulls<Color>(2)
         val lineSettingSnapshot = chams.popLineSetting
         colors[0] = lineSettingSnapshot.value
         colors[1] = ChamsColorProvider.TRANSPARENT_COLOR
         @Suppress("UNCHECKED_CAST")
         return colors as Array<Color>
      }
   };

   override fun getName(): String {
      return this.displayName
   }
}
