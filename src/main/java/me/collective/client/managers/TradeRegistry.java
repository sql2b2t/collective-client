package me.collective.client.managers;

import me.collective.client.utils.inventory.TradeDefinition;
import me.collective.client.utils.inventory.TradeOfferSnapshot;

import me.collective.client.ClientAccess;
import me.collective.client.managers.Registry;
import me.collective.client.settings.JsonConfigurable;
import me.collective.client.settings.NamedEntry;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.world.item.Items;

public final class TradeRegistry extends Registry<TradeDefinition> implements ClientAccess, JsonConfigurable {
   public TradeDefinition createDefault(String name) {
      return new TradeDefinition(name, Items.EMERALD, Items.DIAMOND_SWORD, 64, new ArrayList<>());
   }

   @Override
   public boolean register(Object entry) {
      return this.registerTrade((TradeDefinition)entry);
   }

   @Override
   public void fromJson(JsonElement json) {
      Iterator<JsonElement> iterator = json.getAsJsonObject().getAsJsonArray("trades").iterator();
      while (iterator.hasNext()) {
         JsonElement element = iterator.next();
         try {
            Gson gson = ClientAccess.gson;
            Class<TradeDefinition> tradeType = TradeDefinition.class;
            TradeDefinition trade = gson.fromJson(element, tradeType);
            this.registerTrade(trade);
         } catch (Throwable failure) {
            failure.printStackTrace();
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      JsonArray trades = new JsonArray();
      Iterator<TradeDefinition> iterator = this.getRegistry().iterator();
      while (iterator.hasNext()) {
         TradeDefinition trade = iterator.next();
         Gson gson = ClientAccess.gson;
         JsonElement tradeJson = gson.toJsonTree(trade);
         trades.add(tradeJson);
      }
      root.add("trades", trades);
      return root;
   }

   public void reregisterByName(String name) {
      Predicate<TradeDefinition> predicate = trade -> trade.getName().equalsIgnoreCase(name);
      Optional<TradeDefinition> match = this.findFirst(predicate);
      Consumer<TradeDefinition> consumer = this::registerTrade;
      match.ifPresent(consumer);
   }

   @Override
   public String getConfigName() {
      return "trades.json";
   }

   @Override
   public boolean registerEntry(NamedEntry entry) {
      return this.registerTrade((TradeDefinition)entry);
   }

   public boolean matches(TradeOfferSnapshot offer) {
      Predicate<TradeDefinition> predicate = trade -> trade.matches(offer);
      return this.findFirst(predicate).isPresent();
   }

   public boolean registerTrade(TradeDefinition trade) {
      if (trade.from() != Items.EMERALD && trade.to() != Items.EMERALD) {
         return false;
      }

      return super.registerEntry(trade);
   }

}
