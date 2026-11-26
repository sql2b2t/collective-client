package me.collective.client.bootstrap

import me.collective.addon.AddonManager
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.CollectiveClient
import me.collective.client.bots.BotManager
import me.collective.client.events.ClientInitializationEvent
import me.collective.client.integrations.baritone.BaritoneAdapter
import me.collective.client.integrations.baritone.DisabledBaritoneBridge
import me.collective.client.integrations.xaero.DisabledWaypointAdapter
import me.collective.client.managers.AliasRegistry
import me.collective.client.managers.AutoPresetRegistry
import me.collective.client.managers.BlockColorRegistry
import me.collective.client.managers.ChatFilterRegistry
import me.collective.client.managers.CommandRegistry
import me.collective.client.managers.ConfigurationManager
import me.collective.client.managers.CrystalInteractionManager
import me.collective.client.managers.GlintRegistry
import me.collective.client.managers.HoleManager
import me.collective.client.managers.HudModuleRegistry
import me.collective.client.managers.InputOverrideManager
import me.collective.client.managers.InventoryStateManager
import me.collective.client.managers.KitRegistry
import me.collective.client.managers.MacroRegistry
import me.collective.client.managers.ModuleRegistry
import me.collective.client.managers.MovementPredictionManager
import me.collective.client.managers.RotationManager
import me.collective.client.managers.ServerStateManager
import me.collective.client.managers.SocialRegistry
import me.collective.client.managers.SoundManager
import me.collective.client.managers.StashRegistry
import me.collective.client.managers.SwingInteractionManager
import me.collective.client.managers.TaskScheduler
import me.collective.client.managers.TickRateController
import me.collective.client.managers.TradeRegistry
import me.collective.client.managers.WaypointRegistry
import me.collective.client.managers.WebhookManager
import me.collective.client.managers.WorldStateManager
import me.collective.client.modules.client.UIModule
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.render.FontSupport
import me.collective.client.render.text.LanguageTable
import me.collective.client.utils.FrameTiming
import me.collective.client.utils.combat.TotemPopTracker
import me.collective.client.utils.entity.PlayerStateTracker
import me.collective.client.utils.inventory.CustomItemRegistries
import me.collective.client.utils.movement.MovementSpeedTracker
import me.collective.client.utils.world.BlockBreakTracker
import me.collective.client.utils.world.MapDataCache
import me.collective.scripting.ScriptManager
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import java.util.Locale

class ClientInitializer {
    private var managersInitialized = false
    private var clientInitialized = false

    fun initialize() {
        requireEnvironment()
        if (this.managersInitialized) {
            return
        }
        // Constructors publish services in dependency order before modules bind to them.
        ClientContext.baritoneBridge = if (FabricLoader.getInstance().isModLoaded("baritone-meteor")) {
            BaritoneAdapter()
        } else {
            DisabledBaritoneBridge()
        }
        ClientContext.waypointAdapter = DisabledWaypointAdapter()
        FontSupport.initialize()
        ClientContext.languageTable = LanguageTable()
        ClientContext.inputOverrides = InputOverrideManager()
        ClientContext.blockColorRegistry = BlockColorRegistry()
        ClientContext.playerStateTracker = PlayerStateTracker()
        ClientContext.serverStateManager = ServerStateManager()
        ClientContext.blockBreakTracker = BlockBreakTracker()
        ClientContext.tickRateController = TickRateController()
        ClientContext.playerTickController = TickRateController.PlayerTicks()
        ClientContext.movementPredictionManager = MovementPredictionManager()
        ClientContext.holeManager = HoleManager()
        ClientContext.totemPopTracker = TotemPopTracker()
        ClientContext.movementSpeedTracker = MovementSpeedTracker()
        ClientContext.rotationManager = RotationManager()
        ClientContext.waypointRegistry = WaypointRegistry()
        ClientContext.featureBoxRenderer = FeatureBoxRenderer()
        ClientContext.taskScheduler = TaskScheduler()
        ClientContext.moduleRegistry = ModuleRegistry()
        ClientContext.hudModuleRegistry = HudModuleRegistry()
        ClientContext.tradeRegistry = TradeRegistry()
        ClientContext.commandRegistry = CommandRegistry()
        ClientContext.frameTiming = FrameTiming()
        ClientContext.macroRegistry = MacroRegistry()
        ClientContext.socialRegistry = SocialRegistry()
        ClientContext.soundManager = SoundManager()
        ClientContext.inventoryStateManager = InventoryStateManager()
        ClientContext.stashRegistry = StashRegistry()
        ClientContext.webhookManager = WebhookManager()
        ClientContext.worldStateManager = WorldStateManager()
        ClientContext.crystalInteractionManager = CrystalInteractionManager()
        ClientContext.mapDataCache = MapDataCache()
        ClientContext.kitRegistry = KitRegistry()
        ClientContext.aliasRegistry = AliasRegistry()
        ClientContext.chatFilterRegistry = ChatFilterRegistry()
        ClientContext.glintRegistry = GlintRegistry()
        ClientContext.autoPresetRegistry = AutoPresetRegistry()
        ClientContext.swingManager = SwingInteractionManager()
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
        this.managersInitialized = true
        CollectiveClient.LOGGER.info("Collective main initialization completed.")
    }

    fun initializeClient() {
        requireEnvironment()
        if (!this.managersInitialized) {
            throw IllegalStateException("Collective client initialization requires the main phase.")
        }
        if (this.clientInitialized) {
            return
        }
        ModuleDependencies.bind(ClientContext.moduleRegistry)
        ClientAccess.eventBus.post(ClientInitializationEvent())
        ClientContext.soundManager.reload()
        ClientContext.configurationManager = ConfigurationManager()
        val configuration = ClientContext.configurationManager
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            CustomItemRegistries.initialize()
        }
        configuration.load()
        // Язык интерфейса берётся из сохранённой конфигурации: LanguageTable
        // создаётся раньше модулей и грузит English по умолчанию.
        UIModule.applyLanguageSetting()
        AddonManager.getInstance().init()
        ScriptManager.getInstance().init()
        BotManager.init()
        this.clientInitialized = true
        CollectiveClient.LOGGER.info("Collective client initialization completed.")
    }

    companion object {
        @JvmStatic
        private fun requireEnvironment() {
            val fabric = FabricLoader.getInstance()
            if (fabric.environmentType != EnvType.CLIENT || Runtime.version().feature() < 21) {
                throw IllegalStateException("Collective requires the Fabric client environment and Java 21 or newer.")
            }
            val minecraft = fabric.getModContainer("minecraft")
                .orElseThrow { IllegalStateException("Minecraft mod container is missing.") }
                .metadata.version.friendlyString
            if (minecraft != "26.2") {
                throw IllegalStateException("Collective requires Minecraft 26.2; found $minecraft")
            }
        }
    }
}
