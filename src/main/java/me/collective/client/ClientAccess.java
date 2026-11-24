package me.collective.client;

import me.collective.client.events.bus.EventBus;
import me.collective.client.utils.inventory.ItemRegistryJsonAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface ClientAccess {
   Logger logger = LoggerFactory.getLogger(CollectiveClient.NAME);
   Minecraft collectiveMinecraft = Minecraft.getInstance();
   Gson gson = new GsonBuilder()
      .registerTypeAdapter(Item.class, new ItemRegistryJsonAdapter.ItemAdapter())
      .registerTypeAdapter(BlockItem.class, new ItemRegistryJsonAdapter.BlockItemAdapter())
      .setPrettyPrinting()
      .create();
   EventBus eventBus = new EventBus();
   ExecutorService executorService = Executors.newCachedThreadPool();

   default boolean isWorldUnavailable() {
      return ClientAccess.collectiveMinecraft.player == null
         || ClientAccess.collectiveMinecraft.level == null;
   }

   static boolean hasBundledLanguageResource() {
      return ClientAccess.class.getResource("/assets/collective/lang/en_us.ini") != null;
   }

   static String getVersion() {
      return CollectiveClient.VERSION;
   }

   static boolean isLocalPlayer(Object candidate) {
      return ClientAccess.collectiveMinecraft.player == candidate;
   }

}
