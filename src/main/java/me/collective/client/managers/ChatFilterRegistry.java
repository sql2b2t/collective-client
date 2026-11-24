package me.collective.client.managers;

import me.collective.client.utils.chat.ChatFilterEntry;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.modules.misc.ChatFilterModule;
import me.collective.client.settings.JsonConfigurable;
import me.collective.client.settings.Setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;

public class ChatFilterRegistry implements ClientAccess, JsonConfigurable {
   private static ChatFilterModule chatFilterModule;
   private final List<ChatFilterEntry> filters = Collections.synchronizedList(new ArrayList<>());

   public int size() {
      synchronized (this.filters) {
         return this.filters.size();
      }
   }

   public Stream<String> streamIds() {
      synchronized (this.filters) {
         return this.filters.stream().map(ChatFilterEntry::id).toList().stream();
      }
   }

   public List<String> describeFilters() {
      synchronized (this.filters) {
         return this.filters.stream().map(entry -> "%s%s%s: \"%s\"".formatted(
            ChatFormatting.GRAY, entry.id(), ChatFormatting.RESET, entry.filter())).toList();
      }
   }

   private static ChatFilterModule chatFilterModule() {
      if (chatFilterModule == null) {
         chatFilterModule = ClientContext.getModule(ChatFilterModule.class);
      }
      return chatFilterModule;
   }

   public boolean isEmpty() {
      synchronized (this.filters) {
         return this.filters.isEmpty();
      }
   }

   public void add(String id, String filter) {
      synchronized (this.filters) {
         ChatFilterEntry entry = new ChatFilterEntry(id, filter);
         this.filters.add(entry);
      }
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json instanceof JsonObject root && root.has("filters") && root.get("filters") instanceof JsonArray serializedFilters) {
         for (int filterIndex = 0; filterIndex < serializedFilters.size(); filterIndex++) {
            JsonObject filterObject = serializedFilters.get(filterIndex).getAsJsonObject();
            if (filterObject.has("id") && filterObject.has("filter")) {
               String id = filterObject.get("id").getAsString();
               String filter = filterObject.get("filter").getAsString();
               if (!this.containsId(id)) {
                  this.add(id, filter);
               }
            }
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      JsonArray serializedFilters = new JsonArray();
      synchronized (this.filters) {
         for (ChatFilterEntry entry : this.filters) {
            JsonObject filterObject = new JsonObject();
            filterObject.addProperty("id", entry.id());
            filterObject.addProperty("filter", entry.filter());
            serializedFilters.add(filterObject);
         }
      }
      root.add("filters", serializedFilters);
      return root;
   }

   @Override
   public String getConfigName() {

      return "chatfilter.json";
   }

   public void remove(ChatFilterEntry entry) {
      synchronized (this.filters) {
         this.filters.remove(entry);
      }
   }

   public boolean matchesMessage(String message) {
      Setting<Boolean> messageCaseSetting = chatFilterModule().caseSensitiveSetting;
      Boolean messageCaseSensitive = messageCaseSetting.getValue();
      if (!messageCaseSensitive) {
         message = message.toLowerCase(Locale.ROOT);
      }

      synchronized (this.filters) {
         for (ChatFilterEntry entry : this.filters) {
            String filter = entry.filter();
            Boolean filterCaseSensitive = chatFilterModule().caseSensitiveSetting.getValue();
            if (!filterCaseSensitive) {
               filter = filter.toLowerCase(Locale.ROOT);
            }
            if (message.contains(filter)) {
               return true;
            }
         }
         return false;
      }
   }

   public boolean containsId(String id) {
      synchronized (this.filters) {
         String lowercaseId = id.toLowerCase(Locale.ROOT);
         for (ChatFilterEntry entry : this.filters) {
            if (entry.id().toLowerCase(Locale.ROOT).equals(lowercaseId)) {
               return true;
            }
         }
         return false;
      }
   }

   public Optional<ChatFilterEntry> findById(String id) {
      synchronized (this.filters) {
         Stream<ChatFilterEntry> matchingFilters = this.filters.stream().filter(entry -> entry.id().equalsIgnoreCase(id));
         return matchingFilters.findAny();
      }
   }

}
