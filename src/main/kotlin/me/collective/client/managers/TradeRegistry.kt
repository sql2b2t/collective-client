package me.collective.client.managers

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.function.Predicate
import me.collective.client.ClientAccess
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.inventory.TradeDefinition
import me.collective.client.utils.inventory.TradeOfferSnapshot
import net.minecraft.world.item.Items

class TradeRegistry : Registry<TradeDefinition>(), ClientAccess, JsonConfigurable {
   fun createDefault(name: String): TradeDefinition {
      return TradeDefinition(name, Items.EMERALD, Items.DIAMOND_SWORD, 64, ArrayList())
   }

   override fun register(entry: Any): Boolean {
      return this.registerTrade(entry as TradeDefinition)
   }

   override fun fromJson(json: JsonElement) {
      val tradesArray = json.asJsonObject.getAsJsonArray("trades")
      for (element in tradesArray) {
         try {
            val trade = ClientAccess.gson.fromJson(element, TradeDefinition::class.java)
            this.registerTrade(trade)
         } catch (failure: Throwable) {
            failure.printStackTrace()
         }
      }
   }

   override fun toJson(): JsonElement {
      val root = JsonObject()
      val trades = JsonArray()
      for (trade in this.getRegistry()) {
         trades.add(ClientAccess.gson.toJsonTree(trade))
      }
      root.add("trades", trades)
      return root
   }

   fun reregisterByName(name: String) {
      val match = this.findFirst(Predicate { trade -> trade.getName().equals(name, ignoreCase = true) })
      match.ifPresent { this.registerTrade(it) }
   }

   override fun getConfigName(): String = "trades.json"

   override fun registerEntry(entry: NamedEntry?): Boolean {
      return this.registerTrade(entry as TradeDefinition)
   }

   fun matches(offer: TradeOfferSnapshot): Boolean {
      return this.findFirst(Predicate { trade -> trade.matches(offer) }).isPresent
   }

   fun registerTrade(trade: TradeDefinition): Boolean {
      if (trade.from() != Items.EMERALD && trade.to() != Items.EMERALD) {
         return false
      }
      return super.registerEntry(trade)
   }
}
