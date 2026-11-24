package me.collective.client.managers;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.bus.EventBus;
import me.collective.client.managers.Registry;
import me.collective.client.modules.misc.StashFinderModule;
import me.collective.client.modules.misc.StashEntry;
import me.collective.client.settings.JsonConfigurable;
import me.collective.client.settings.Setting;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Iterator;
import java.util.Collection;

public final class StashRegistry extends Registry<StashEntry> implements ClientAccess, JsonConfigurable {
   private static StashFinderModule stashFinderModule;

   public StashRegistry() {
      EventBus eventBus = ClientAccess.eventBus;
      eventBus.subscribe(this);
   }

   private static StashFinderModule stashFinderModule() {
      if (stashFinderModule == null) {
         stashFinderModule = ClientContext.getModule(StashFinderModule.class);
      }
      return stashFinderModule;
   }

   @Override
   public void fromJson(JsonElement element) {
      if (!element.isJsonObject()) {
         return;
      }
      JsonObject object = element.getAsJsonObject();
      if (!object.has("stashes")) {
         return;
      }
      object = element.getAsJsonObject();
      JsonArray stashes = object.getAsJsonArray("stashes");
      Iterator<JsonElement> iterator = stashes.iterator();
      while (iterator.hasNext()) {
         JsonElement stashElement = iterator.next();
         try {
            Collection<StashEntry> stashEntries = this.entries;
            Gson gson = ClientAccess.gson;
            stashEntries.add((StashEntry)gson.fromJson(stashElement, StashEntry.class));
         } catch (Exception ignored) {
         }
      }
   }

   @Override
   public JsonElement toJson() {
      // Сохраняем стэши всегда: настройка logSetting управляет только записью новых
      // стэшей, но не сериализацией — иначе сохранение конфига стирает stashfinder.json.
      JsonArray stashes = new JsonArray();
      JsonObject object = new JsonObject();
      Iterator<StashEntry> iterator = ClientContext.stashRegistry.getRegistry().iterator();

      while (iterator.hasNext()) {
         StashEntry stash = iterator.next();
         Gson gson = ClientAccess.gson;
         stashes.add(gson.toJsonTree(stash));
      }

      object.add("stashes", stashes);
      return object;
   }

   @Override
   public String getConfigName() {
      return "stashfinder.json";
   }

}
