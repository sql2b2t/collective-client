package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Collections
import java.util.Locale
import java.util.Optional
import java.util.stream.Stream
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.modules.misc.ChatFilterModule
import me.collective.client.settings.JsonConfigurable
import me.collective.client.utils.chat.ChatFilterEntry
import net.minecraft.ChatFormatting

open class ChatFilterRegistry : ClientAccess, JsonConfigurable {
   private val filters: MutableList<ChatFilterEntry> = Collections.synchronizedList(ArrayList())

   fun size(): Int {
      synchronized(this.filters) {
         return this.filters.size
      }
   }

   fun streamIds(): Stream<String> {
      synchronized(this.filters) {
         return this.filters.map { it.id() }.stream()
      }
   }

   fun describeFilters(): List<String> {
      synchronized(this.filters) {
         return this.filters.map { entry ->
            "${ChatFormatting.GRAY}${entry.id()}${ChatFormatting.RESET}: \"${entry.filter()}\""
         }
      }
   }

   fun isEmpty(): Boolean {
      synchronized(this.filters) {
         return this.filters.isEmpty()
      }
   }

   fun add(id: String, filter: String) {
      synchronized(this.filters) {
         this.filters.add(ChatFilterEntry(id, filter))
      }
   }

   override fun fromJson(json: JsonElement) {
      if (json is JsonObject && json.has("filters")) {
         val serializedFilters = json.get("filters")
         if (serializedFilters is JsonArray) {
            for (filterIndex in 0 until serializedFilters.size()) {
               val filterObject = serializedFilters.get(filterIndex).asJsonObject
               if (filterObject.has("id") && filterObject.has("filter")) {
                  val id = filterObject.get("id").asString
                  val filter = filterObject.get("filter").asString
                  if (!this.containsId(id)) {
                     this.add(id, filter)
                  }
               }
            }
         }
      }
   }

   override fun toJson(): JsonElement {
      val root = JsonObject()
      val serializedFilters = JsonArray()
      synchronized(this.filters) {
         for (entry in this.filters) {
            val filterObject = JsonObject()
            filterObject.addProperty("id", entry.id())
            filterObject.addProperty("filter", entry.filter())
            serializedFilters.add(filterObject)
         }
      }
      root.add("filters", serializedFilters)
      return root
   }

   override fun getConfigName(): String = "chatfilter.json"

   fun remove(entry: ChatFilterEntry) {
      synchronized(this.filters) {
         this.filters.remove(entry)
      }
   }

   fun matchesMessage(message: String): Boolean {
      val messageCaseSensitive = chatFilterModule().caseSensitiveSetting.getValue() == true
      val normalizedMessage = if (!messageCaseSensitive) message.lowercase(Locale.ROOT) else message

      synchronized(this.filters) {
         for (entry in this.filters) {
            var filter = entry.filter()
            val filterCaseSensitive = chatFilterModule().caseSensitiveSetting.getValue() == true
            if (!filterCaseSensitive) {
               filter = filter.lowercase(Locale.ROOT)
            }
            if (normalizedMessage.contains(filter)) {
               return true
            }
         }
         return false
      }
   }

   fun containsId(id: String): Boolean {
      synchronized(this.filters) {
         val lowercaseId = id.lowercase(Locale.ROOT)
         for (entry in this.filters) {
            if (entry.id().lowercase(Locale.ROOT) == lowercaseId) {
               return true
            }
         }
         return false
      }
   }

   fun findById(id: String): Optional<ChatFilterEntry> {
      synchronized(this.filters) {
         return this.filters.stream().filter { entry -> entry.id().equals(id, ignoreCase = true) }.findAny()
      }
   }

   companion object {
      private var chatFilterModule: ChatFilterModule? = null

      private fun chatFilterModule(): ChatFilterModule {
         if (chatFilterModule == null) {
            chatFilterModule = ClientContext.getModule(ChatFilterModule::class.java)
         }
         return chatFilterModule!!
      }
   }
}
