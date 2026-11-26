package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.misc.StashEntry
import me.collective.client.modules.misc.StashFinderModule
import me.collective.client.settings.JsonConfigurable

class StashRegistry : Registry<StashEntry>(), ClientAccess, JsonConfigurable {
   init {
      ClientAccess.eventBus.subscribe(this)
   }

   override fun fromJson(element: JsonElement) {
      if (!element.isJsonObject) {
         return
      }
      val jsonObject = element.asJsonObject
      if (!jsonObject.has("stashes")) {
         return
      }
      val stashes = jsonObject.getAsJsonArray("stashes")
      for (stashElement in stashes) {
         try {
            this.entries.add(ClientAccess.gson.fromJson(stashElement, StashEntry::class.java))
         } catch (_: Exception) {
         }
      }
   }

   override fun toJson(): JsonElement {
      // Сохраняем стэши всегда: настройка logSetting управляет только записью новых
      // стэшей, но не сериализацией — иначе сохранение конфига стирает stashfinder.json.
      val stashes = JsonArray()
      val jsonObject = JsonObject()
      for (stash in ClientContext.stashRegistry.getRegistry()) {
         stashes.add(ClientAccess.gson.toJsonTree(stash))
      }
      jsonObject.add("stashes", stashes)
      return jsonObject
   }

   override fun getConfigName(): String = "stashfinder.json"

   companion object {
      private var stashFinderModule: StashFinderModule? = null

      @Suppress("unused")
      private fun stashFinderModule(): StashFinderModule {
         if (stashFinderModule == null) {
            stashFinderModule = ClientContext.getModule(StashFinderModule::class.java)
         }
         return stashFinderModule!!
      }
   }
}
