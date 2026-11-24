package me.collective.client;

import me.collective.client.gui.screens.ClickGuiScreen;
import me.collective.client.gui.screens.HudEditorScreen;
import me.collective.client.integrations.baritone.BaritoneBridge;
import me.collective.client.integrations.xaero.WaypointAdapter;
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
import me.collective.client.managers.ShaderRegistry;
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
import me.collective.client.modules.Module;
import me.collective.client.render.FeatureBoxRenderer;
import me.collective.client.render.text.LanguageTable;
import me.collective.client.utils.FrameTiming;
import me.collective.client.utils.combat.TotemPopTracker;
import me.collective.client.utils.entity.PlayerStateTracker;
import me.collective.client.utils.movement.MovementSpeedTracker;
import me.collective.client.utils.world.BlockBreakTracker;
import me.collective.client.utils.world.MapDataCache;
import java.util.Objects;

public final class ClientContext {
   public static TickRateController tickRateController;
   public static ConfigurationManager configurationManager;
   public static RotationManager rotationManager;
   public static volatile ModuleRegistry moduleRegistry;
   public static CommandRegistry commandRegistry;
   public static FrameTiming frameTiming;
   public static ServerStateManager serverStateManager;
   public static SocialRegistry socialRegistry;
   public static WaypointRegistry waypointRegistry;
   public static HoleManager holeManager;
   public static SoundManager soundManager;
   public static MacroRegistry macroRegistry;
   public static InventoryStateManager inventoryStateManager;
   public static MovementPredictionManager movementPredictionManager;
   public static TotemPopTracker totemPopTracker;
   public static MovementSpeedTracker movementSpeedTracker;
   public static PlayerStateTracker playerStateTracker;
   public static FeatureBoxRenderer featureBoxRenderer;
   public static TickRateController.PlayerTicks playerTickController;
   public static StashRegistry stashRegistry;
   public static TaskScheduler taskScheduler;
   public static HudModuleRegistry hudModuleRegistry;
   public static WebhookManager webhookManager;
   public static WorldStateManager worldStateManager;
   public static CrystalInteractionManager crystalInteractionManager;
   public static MapDataCache mapDataCache;
   public static KitRegistry kitRegistry;
   public static AliasRegistry aliasRegistry;
   public static ChatFilterRegistry chatFilterRegistry;
   public static GlintRegistry glintRegistry;
   public static BlockBreakTracker blockBreakTracker;
   public static ShaderRegistry shaderRegistry;
   public static TradeRegistry tradeRegistry;
   public static BlockColorRegistry blockColorRegistry;
   public static AutoPresetRegistry autoPresetRegistry;
   public static InputOverrideManager inputOverrides;
   public static LanguageTable languageTable;
   public static SwingInteractionManager swingManager;
   public static BaritoneBridge baritoneBridge;
   public static WaypointAdapter waypointAdapter;
   public static ClickGuiScreen clickGui;
   private static HudEditorScreen hudEditor;

   private ClientContext() {
   }

   public static <T extends Module> T getModule(Class<T> moduleType) {
      Objects.requireNonNull(moduleType, "Module type");
      ModuleRegistry registry = moduleRegistry;
      return registry == null ? null : moduleType.cast(registry.getModule(moduleType));
   }

   public static ClickGuiScreen getClickGui() {
      if (clickGui == null) {
         clickGui = new ClickGuiScreen();
      }

      return clickGui;
   }

   public static HudEditorScreen getHudEditor() {
      if (hudEditor == null) {
         hudEditor = new HudEditorScreen();
      }

      return hudEditor;
   }
}
