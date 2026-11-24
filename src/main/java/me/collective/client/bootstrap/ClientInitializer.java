package me.collective.client.bootstrap;

import me.collective.client.ClientAccess;
import me.collective.client.ClientContext;
import me.collective.client.CollectiveClient;
import me.collective.client.events.ClientInitializationEvent;
import me.collective.client.integrations.baritone.BaritoneAdapter;
import me.collective.client.integrations.baritone.DisabledBaritoneBridge;
import me.collective.client.integrations.xaero.DisabledWaypointAdapter;
import me.collective.client.managers.AliasRegistry;
import me.collective.client.managers.AutoPresetRegistry;
import me.collective.client.managers.BlockColorRegistry;
import me.collective.client.managers.ChatFilterRegistry;
import me.collective.client.managers.CommandRegistry;
import me.collective.client.managers.ConfigurationManager;
import me.collective.client.managers.CrystalInteractionManager;
import me.collective.client.managers.GlintRegistry;
import me.collective.client.managers.HoleManager;
import me.collective.client.managers.HudModuleRegistry;
import me.collective.client.managers.InputOverrideManager;
import me.collective.client.managers.InventoryStateManager;
import me.collective.client.managers.KitRegistry;
import me.collective.client.managers.MacroRegistry;
import me.collective.client.managers.ModuleRegistry;
import me.collective.client.managers.MovementPredictionManager;
import me.collective.client.managers.RotationManager;
import me.collective.client.managers.ServerStateManager;
import me.collective.client.managers.SocialRegistry;
import me.collective.client.managers.SoundManager;
import me.collective.client.managers.StashRegistry;
import me.collective.client.managers.SwingInteractionManager;
import me.collective.client.managers.TaskScheduler;
import me.collective.client.managers.TickRateController;
import me.collective.client.managers.TradeRegistry;
import me.collective.client.managers.WaypointRegistry;
import me.collective.client.managers.WebhookManager;
import me.collective.client.managers.WorldStateManager;
import me.collective.client.render.FeatureBoxRenderer;
import me.collective.client.render.FontSupport;
import me.collective.client.render.text.LanguageTable;
import me.collective.client.utils.FrameTiming;
import me.collective.client.utils.combat.TotemPopTracker;
import me.collective.client.utils.entity.PlayerStateTracker;
import me.collective.client.utils.inventory.CustomItemRegistries;
import me.collective.client.utils.movement.MovementSpeedTracker;
import me.collective.client.utils.world.BlockBreakTracker;
import me.collective.client.utils.world.MapDataCache;
import java.util.Locale;
import java.util.Locale.Category;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public final class ClientInitializer {
   private boolean managersInitialized;
   private boolean clientInitialized;

   public void initialize() {
      requireEnvironment();
      if (this.managersInitialized) {
         return;
      }
      // Constructors publish services in dependency order before modules bind to them.
      ClientContext.baritoneBridge = FabricLoader.getInstance().isModLoaded("baritone-meteor")
         ? new BaritoneAdapter() : new DisabledBaritoneBridge();
      ClientContext.waypointAdapter = new DisabledWaypointAdapter();
      FontSupport.initialize();
      ClientContext.languageTable = new LanguageTable();
      ClientContext.inputOverrides = new InputOverrideManager();
      ClientContext.blockColorRegistry = new BlockColorRegistry();
      ClientContext.playerStateTracker = new PlayerStateTracker();
      ClientContext.serverStateManager = new ServerStateManager();
      ClientContext.blockBreakTracker = new BlockBreakTracker();
      ClientContext.tickRateController = new TickRateController();
      ClientContext.playerTickController = new TickRateController.PlayerTicks();
      ClientContext.movementPredictionManager = new MovementPredictionManager();
      ClientContext.holeManager = new HoleManager();
      ClientContext.totemPopTracker = new TotemPopTracker();
      ClientContext.movementSpeedTracker = new MovementSpeedTracker();
      ClientContext.rotationManager = new RotationManager();
      ClientContext.waypointRegistry = new WaypointRegistry();
      ClientContext.featureBoxRenderer = new FeatureBoxRenderer();
      ClientContext.taskScheduler = new TaskScheduler();
      ClientContext.moduleRegistry = new ModuleRegistry();
      ClientContext.hudModuleRegistry = new HudModuleRegistry();
      ClientContext.tradeRegistry = new TradeRegistry();
      ClientContext.commandRegistry = new CommandRegistry();
      ClientContext.frameTiming = new FrameTiming();
      ClientContext.macroRegistry = new MacroRegistry();
      ClientContext.socialRegistry = new SocialRegistry();
      ClientContext.soundManager = new SoundManager();
      ClientContext.inventoryStateManager = new InventoryStateManager();
      ClientContext.stashRegistry = new StashRegistry();
      ClientContext.webhookManager = new WebhookManager();
      ClientContext.worldStateManager = new WorldStateManager();
      ClientContext.crystalInteractionManager = new CrystalInteractionManager();
      ClientContext.mapDataCache = new MapDataCache();
      ClientContext.kitRegistry = new KitRegistry();
      ClientContext.aliasRegistry = new AliasRegistry();
      ClientContext.chatFilterRegistry = new ChatFilterRegistry();
      ClientContext.glintRegistry = new GlintRegistry();
      ClientContext.autoPresetRegistry = new AutoPresetRegistry();
      ClientContext.swingManager = new SwingInteractionManager();
      Locale.setDefault(Category.FORMAT, Locale.US);
      this.managersInitialized = true;
      CollectiveClient.LOGGER.info("Collective main initialization completed.");
   }

   public void initializeClient() {
      requireEnvironment();
      if (!this.managersInitialized) {
         throw new IllegalStateException("Collective client initialization requires the main phase.");
      }
      if (this.clientInitialized) {
         return;
      }
      ModuleDependencies.bind(ClientContext.moduleRegistry);
      ClientAccess.eventBus.post(new ClientInitializationEvent());
      ClientContext.soundManager.reload();
      ClientContext.configurationManager = new ConfigurationManager();
      ConfigurationManager configuration = ClientContext.configurationManager;
      CustomItemRegistries.initialize();
      configuration.load();
      // Язык интерфейса берётся из сохранённой конфигурации: LanguageTable
      // создаётся раньше модулей и грузит English по умолчанию.
      me.collective.client.modules.client.UIModule.applyLanguageSetting();
      me.collective.addon.AddonManager.getInstance().init();
      me.collective.scripting.ScriptManager.getInstance().init();
      me.collective.client.bots.BotManager.INSTANCE.init();
      this.clientInitialized = true;
      CollectiveClient.LOGGER.info("Collective client initialization completed.");
   }

   private static void requireEnvironment() {
      FabricLoader fabric = FabricLoader.getInstance();
      if (fabric.getEnvironmentType() != EnvType.CLIENT || Runtime.version().feature() < 21) {
         throw new IllegalStateException("Collective requires the Fabric client environment and Java 21 or newer.");
      }
      String minecraft = fabric.getModContainer("minecraft")
         .orElseThrow(() -> new IllegalStateException("Minecraft mod container is missing."))
         .getMetadata().getVersion().getFriendlyString();
      if (!"1.21.11".equals(minecraft)) {
         throw new IllegalStateException("Collective requires Minecraft 1.21.11; found " + minecraft);
      }
   }
}
