package me.collective.client.utils.network

import me.collective.client.settings.options.PresetCategory

class AutoPresetServerEntry(val server: String) {
   val presets: MutableMap<PresetCategory, String> = HashMap()
   var kitName: String? = null

   fun hasKit(): Boolean {
      return this.kitName != null
   }
}
