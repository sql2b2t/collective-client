package me.collective.client;

import me.collective.client.bootstrap.ClientInitializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CollectiveClient implements ModInitializer, ClientModInitializer {
   public static final String MOD_ID = "collective";
   public static final String NAME = "Collective";
   public static final String PROJECT_URL = "https://github.com/Cypphi/open-collective";
   public static final String VERSION = FabricLoader.getInstance().getModContainer(MOD_ID)
      .orElseThrow(() -> new IllegalStateException("Collective mod metadata is missing"))
      .getMetadata().getVersion().getFriendlyString();
   public static final String USER_AGENT = NAME + "/" + VERSION;
   public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

   private static final ClientInitializer INITIALIZER = new ClientInitializer();

   @Override
   public void onInitialize() {
      INITIALIZER.initialize();
   }

   @Override
   public void onInitializeClient() {
      INITIALIZER.initializeClient();
   }
}
