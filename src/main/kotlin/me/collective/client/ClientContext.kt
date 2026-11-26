package me.collective.client

import me.collective.client.gui.screens.ClickGuiScreen
import me.collective.client.gui.screens.HudEditorScreen
import me.collective.client.integrations.baritone.BaritoneBridge
import me.collective.client.integrations.xaero.WaypointAdapter
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
import me.collective.client.managers.ShaderRegistry
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
import me.collective.client.modules.Module
import me.collective.client.render.FeatureBoxRenderer
import me.collective.client.render.text.LanguageTable
import me.collective.client.utils.FrameTiming
import me.collective.client.utils.combat.TotemPopTracker
import me.collective.client.utils.entity.PlayerStateTracker
import me.collective.client.utils.movement.MovementSpeedTracker
import me.collective.client.utils.world.BlockBreakTracker
import me.collective.client.utils.world.MapDataCache
import java.util.Objects

object ClientContext {
    lateinit var tickRateController: TickRateController
    lateinit var configurationManager: ConfigurationManager
    lateinit var rotationManager: RotationManager
    @Volatile
    lateinit var moduleRegistry: ModuleRegistry
    lateinit var commandRegistry: CommandRegistry
    lateinit var frameTiming: FrameTiming
    lateinit var serverStateManager: ServerStateManager
    lateinit var socialRegistry: SocialRegistry
    lateinit var waypointRegistry: WaypointRegistry
    lateinit var holeManager: HoleManager
    lateinit var soundManager: SoundManager
    lateinit var macroRegistry: MacroRegistry
    lateinit var inventoryStateManager: InventoryStateManager
    lateinit var movementPredictionManager: MovementPredictionManager
    lateinit var totemPopTracker: TotemPopTracker
    lateinit var movementSpeedTracker: MovementSpeedTracker
    lateinit var playerStateTracker: PlayerStateTracker
    lateinit var featureBoxRenderer: FeatureBoxRenderer
    lateinit var playerTickController: TickRateController.PlayerTicks
    lateinit var stashRegistry: StashRegistry
    lateinit var taskScheduler: TaskScheduler
    lateinit var hudModuleRegistry: HudModuleRegistry
    lateinit var webhookManager: WebhookManager
    lateinit var worldStateManager: WorldStateManager
    lateinit var crystalInteractionManager: CrystalInteractionManager
    lateinit var mapDataCache: MapDataCache
    lateinit var kitRegistry: KitRegistry
    lateinit var aliasRegistry: AliasRegistry
    lateinit var chatFilterRegistry: ChatFilterRegistry
    lateinit var glintRegistry: GlintRegistry
    lateinit var blockBreakTracker: BlockBreakTracker
    lateinit var shaderRegistry: ShaderRegistry
    lateinit var tradeRegistry: TradeRegistry
    lateinit var blockColorRegistry: BlockColorRegistry
    lateinit var autoPresetRegistry: AutoPresetRegistry
    lateinit var inputOverrides: InputOverrideManager
    lateinit var languageTable: LanguageTable
    lateinit var swingManager: SwingInteractionManager
    lateinit var baritoneBridge: BaritoneBridge
    lateinit var waypointAdapter: WaypointAdapter

    private var clickGuiInstance: ClickGuiScreen? = null

    val clickGui: ClickGuiScreen
        @JvmName("getClickGuiProp")
        get() = getClickGui()

    private var hudEditor: HudEditorScreen? = null

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Module> getModuleOrNull(moduleType: Class<T>): T? {
        Objects.requireNonNull(moduleType, "Module type")
        return if (::moduleRegistry.isInitialized) {
            moduleType.cast(moduleRegistry.getModuleOrNull(moduleType))
        } else {
            null
        }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <T : Module> getModule(moduleType: Class<T>): T {
        Objects.requireNonNull(moduleType, "Module type")
        val result: T? = if (::moduleRegistry.isInitialized) {
            moduleType.cast(moduleRegistry.getModule(moduleType))
        } else {
            null
        }
        return result as T
    }

    @JvmStatic
    fun getClickGui(): ClickGuiScreen {
        if (clickGuiInstance == null) {
            clickGuiInstance = ClickGuiScreen()
        }
        return clickGuiInstance!!
    }

    @JvmStatic
    fun getHudEditor(): HudEditorScreen {
        if (hudEditor == null) {
            hudEditor = HudEditorScreen()
        }
        return hudEditor!!
    }
}
