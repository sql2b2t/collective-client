package me.collective.client.managers;

import me.collective.client.utils.chat.ChatUtils;
import me.collective.client.utils.inventory.Kit;
import me.collective.client.utils.network.AutoPresetServerEntry;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.events.ConnectScreenConnectEvent;
import me.collective.client.events.EventHandler;
import me.collective.client.settings.JsonConfigurable;
import me.collective.client.settings.options.PresetCategory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

public final class AutoPresetRegistry implements ClientAccess, JsonConfigurable {
   private final Map serverSettings = new HashMap();
   private String lastServer;

   /** Ключи серверов нормализуются в одном месте: имена хостов могут быть в разном регистре. */
   private static String normalizeServer(String server) {
      return server == null ? "" : server.toLowerCase(Locale.ROOT);
   }

   public Collection getServers() {
      return this.serverSettings.keySet();
   }

   public AutoPresetRegistry() {
      ClientAccess.eventBus.subscribe(this);
   }

   @Override
   public void fromJson(JsonElement json) {
      JsonObject root = json.getAsJsonObject();
      Iterator serverIterator = root.entrySet().iterator();

      while (serverIterator.hasNext()) {
         Entry serverEntry = (Entry)serverIterator.next();
         String server = (String)serverEntry.getKey();
         JsonElement serverJson = (JsonElement)serverEntry.getValue();
         JsonObject settingsJson = serverJson.getAsJsonObject();
         Iterator settingIterator = settingsJson.entrySet().iterator();

         while (settingIterator.hasNext()) {
            Entry settingEntry = (Entry)settingIterator.next();
            String categoryName = (String)settingEntry.getKey();
            PresetCategory category = PresetCategory.findByName(categoryName);
            JsonElement presetJson = (JsonElement)settingEntry.getValue();
            String presetName = presetJson.getAsString();
            if (category != null) {
               this.setPreset(server, category, presetName);
            }
         }

         if (settingsJson.has("kit")) {
            JsonElement kitJson = settingsJson.get("kit");
            this.setKit(server, kitJson.getAsString());
         }
      }
   }

   @Override
   public JsonElement toJson() {
      JsonObject root = new JsonObject();
      Iterator serverIterator = this.serverSettings.values().iterator();

      while (serverIterator.hasNext()) {
         AutoPresetServerEntry serverSettings = (AutoPresetServerEntry)serverIterator.next();
         JsonObject settingsJson = new JsonObject();

         Iterator presetIterator = serverSettings.getPresets().entrySet().iterator();

         while (presetIterator.hasNext()) {
            Entry presetEntry = (Entry)presetIterator.next();
            PresetCategory category = (PresetCategory)presetEntry.getKey();
            String categoryKey = category.getName().toLowerCase(Locale.ROOT);
            String presetName = (String)presetEntry.getValue();
            settingsJson.addProperty(categoryKey, presetName);
         }

         if (serverSettings.hasKit()) {
            String kitName = serverSettings.getKitName();
            settingsJson.addProperty("kit", kitName);
         }

         String serverName = serverSettings.getServer();
         root.add(serverName, settingsJson);
      }

      return root;
   }

   public boolean removeKit(String server) {
      AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.get(normalizeServer(server));
      if (settings == null) {
         return false;
      }

      settings.setKitName(null);
      return true;
   }

   @Override
   public String getConfigName() {
      Path presetDirectoryName = ConfigurationManager.PRESETS_DIRECTORY.getFileName();

      return presetDirectoryName.resolve("auto.json").toString();
   }

   public boolean removePreset(String server, PresetCategory category) {
      AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.get(normalizeServer(server));
      if (settings == null) {
         return false;
      }

      return settings.getPresets().remove(category) != null;
   }

   public void setPreset(
      String server, PresetCategory category, String presetName
   ) {
      AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.computeIfAbsent(normalizeServer(server), (Function<String, AutoPresetServerEntry>)ignoredServer -> new AutoPresetServerEntry(normalizeServer(server)));

      settings.getPresets().put(category, presetName);
   }

   @EventHandler
   public void onConnectScreenConnectEvent(ConnectScreenConnectEvent event) {
      String serverAddress = event.getServerAddress();
      String previousServer = this.lastServer;
      if (!serverAddress.equalsIgnoreCase(previousServer)) {
         this.lastServer = event.getServerAddress();
         AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.get(normalizeServer(this.lastServer));
         if (settings != null) {

            AtomicInteger messageCounter = new AtomicInteger(-2);

            settings.getPresets().forEach((BiConsumer<PresetCategory, String>)(category, presetName) -> {
               ConfigurationManager configurationManager = ClientContext.configurationManager;
               PresetRegistry presetRegistry = configurationManager.getPresetRegistry(category);
               presetRegistry.applyPreset(presetName);
               TaskScheduler schedulerForPreset = ClientContext.taskScheduler;
               Runnable messageForPreset = () -> {
                  int messageId = messageCounter.getAndDecrement();

                  MessageSignature signature = ChatUtils.createSignature(messageId);

                  Object[] messageArguments = new Object[]{presetName};
                  String messageFormat = "Preset %s has been loaded.";
                  String messageText = messageFormat.formatted(messageArguments);
                  ChatUtils.printChat(Component.literal(messageText), signature);
               };
               schedulerForPreset.scheduleRotationTask(messageForPreset, 0);
            });

            if (settings.hasKit()) {
               String kitName = settings.getKitName();
               ClientContext.kitRegistry.findFirst((Predicate<Kit>)kit -> kit.getName().equalsIgnoreCase(kitName))
                  .ifPresent((Consumer<Kit>)matchingKit -> {
                     KitRegistry kitRegistry = ClientContext.kitRegistry;
                     kitRegistry.setCurrentKitName(kitName);
                     TaskScheduler schedulerForKit = ClientContext.taskScheduler;
                     Runnable messageForKit = () -> {
                        int messageId = messageCounter.getAndDecrement();

                        MessageSignature signature = ChatUtils.createSignature(messageId);

                        Object[] messageArguments = new Object[]{kitName};
                        String messageFormat = "Kit %s has been loaded.";
                        String messageText = messageFormat.formatted(messageArguments);
                        ChatUtils.printChat(Component.literal(messageText), signature);
                     };
                     schedulerForKit.scheduleRunTickTask(messageForKit);
                  });
            }
         }
      }
   }

   public boolean removeServer(String server) {
      return this.serverSettings.remove(normalizeServer(server)) != null;
   }

   public void setKit(String server, String kitName) {
      AutoPresetServerEntry settings = (AutoPresetServerEntry)this.serverSettings.computeIfAbsent(normalizeServer(server), (Function<String, AutoPresetServerEntry>)ignoredServer -> new AutoPresetServerEntry(normalizeServer(server)));

      settings.setKitName(kitName);
   }

}
