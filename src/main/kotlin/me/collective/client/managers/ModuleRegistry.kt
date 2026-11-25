package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.NamedFeature
import me.collective.client.config.LegacyBranding
import me.collective.client.events.EventHandler
import me.collective.client.events.InputPressEvent
import me.collective.client.events.WorldRenderEvent
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Keybind
import me.collective.client.modules.Module
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.modules.client.BaritoneModule
import me.collective.client.modules.movement.ObstaclePasserModule
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.NamedEntry
import me.collective.client.utils.ExperimentalFeatures
import me.collective.client.utils.KeyUtils
import java.util.stream.Collectors

class ModuleRegistry : Registry<Module>(), ClientAccess, JsonConfigurable {
    private val modulesByClass = Object2ObjectOpenHashMap<Class<*>, Module>()

    val placementModules: List<PlacementModule>
        @JvmName("getPlacementModulesProp")
        get() {
            val result = ArrayList<PlacementModule>()
            for (module in entries) {
                if (module is PlacementModule) {
                    result.add(module)
                }
            }
            return result
        }

    fun getPlacementModules(): List<PlacementModule> = placementModules

    private fun registerPathfindingModules() {
        val bridge = ClientContext.baritoneBridge
        registerModule(BaritoneModule())
        if (bridge.isAvailable()) {
            registerModule(ObstaclePasserModule())
        }
    }

    init {
        ClientContext.moduleRegistry = this
        ClientAccess.eventBus.subscribe(this)
        registerRequired(me.collective.client.modules.render.AmbienceModule(), "AmbienceModule")
        registerRequired(me.collective.client.modules.combat.AnchorAuraModule(), "AnchorAuraModule")
        registerRequired(me.collective.client.modules.render.AnimationsModule(), "AnimationsModule")
        registerRequired(me.collective.client.modules.misc.AnnouncerModule(), "AnnouncerModule")
        registerRequired(me.collective.client.modules.misc.AntiAFKModule(), "AntiAFKModule")
        registerRequired(me.collective.client.modules.misc.AntiAimModule(), "AntiAimModule")
        registerRequired(me.collective.client.modules.combat.AntiBotModule(), "AntiBotModule")
        registerRequired(me.collective.client.modules.client.AntiCheatModule(), "AntiCheatModule")
        registerRequired(me.collective.client.modules.misc.AntiCyrillicModule(), "AntiCyrillicModule")
        registerRequired(me.collective.client.modules.combat.AntiFeetPlaceModule(), "AntiFeetPlaceModule")
        registerRequired(me.collective.client.modules.exploit.AntiHungerModule(), "AntiHungerModule")
        registerRequired(me.collective.client.modules.combat.AntiPearlModule(), "AntiPearlModule")
        registerRequired(me.collective.client.modules.combat.AntiPhaseModule(), "AntiPhaseModule")
        registerRequired(me.collective.client.modules.misc.AntiQuitModule(), "AntiQuitModule")
        registerRequired(me.collective.client.modules.misc.AntiSpamModule(), "AntiSpamModule")
        registerRequired(me.collective.client.modules.misc.AutoWitherModule(), "AutoWitherModule")
        registerRequired(me.collective.client.modules.exploit.AntiVanishModule(), "AntiVanishModule")
        registerRequired(me.collective.client.modules.movement.AvoidModule(), "AvoidModule")
        registerRequired(me.collective.client.modules.combat.ArrowsModule(), "ArrowsModule")
        registerRequired(me.collective.client.modules.combat.AttributeSwapModule(), "AttributeSwapModule")
        registerRequired(me.collective.client.modules.combat.AuraModule(), "AuraModule")
        registerRequired(me.collective.client.modules.combat.AutoArmorModule(), "AutoArmorModule")
        registerRequired(me.collective.client.modules.combat.AutoBowReleaseModule(), "AutoBowReleaseModule")
        registerRequired(me.collective.client.modules.player.AutoBreedModule(), "AutoBreedModule")
        registerRequired(me.collective.client.modules.combat.AutoClickerModule(), "AutoClickerModule")
        registerRequired(me.collective.client.modules.player.AutoCraftModule(), "AutoCraftModule")
        registerRequired(me.collective.client.modules.combat.AutoCrystalModule(), "AutoCrystalModule")
        registerRequired(me.collective.client.modules.combat.AutoDropModule(), "AutoDropModule")
        registerRequired(me.collective.client.modules.player.AutoEatModule(), "AutoEatModule")
        registerRequired(me.collective.client.modules.combat.AutoExpModule(), "AutoExpModule")
        registerRequired(me.collective.client.modules.player.AutoFarmModule(), "AutoFarmModule")
        registerRequired(me.collective.client.modules.player.AutoFishModule(), "AutoFishModule")
        registerRequired(me.collective.client.modules.combat.AutoLogModule(), "AutoLogModule")
        registerRequired(me.collective.client.modules.combat.AutoMaceModule(), "AutoMaceModule")
        registerRequired(me.collective.client.modules.combat.AutoMineModule(), "AutoMineModule")
        registerRequired(me.collective.client.modules.player.AutoMountModule(), "AutoMountModule")
        registerRequired(me.collective.client.modules.player.AutoNameTagModule(), "AutoNameTagModule")
        registerRequired(me.collective.client.modules.combat.AutoPotModule(), "AutoPotModule")
        registerRequired(me.collective.client.modules.misc.AutoReconnectModule(), "AutoReconnectModule")
        registerRequired(me.collective.client.modules.misc.AutoRespawnModule(), "AutoRespawnModule")
        registerRequired(me.collective.client.modules.misc.AutoSignModule(), "AutoSignModule")
        registerRequired(me.collective.client.modules.player.AutoTameModule(), "AutoTameModule")
        registerRequired(me.collective.client.modules.player.AutoToolModule(), "AutoToolModule")
        registerRequired(me.collective.client.modules.player.AutoTradeModule(), "AutoTradeModule")
        registerRequired(me.collective.client.modules.combat.AutoTrapModule(), "AutoTrapModule")
        registerRequired(me.collective.client.modules.player.AutoTemplateModule(), "AutoTemplateModule")
        registerRequired(me.collective.client.modules.movement.AutoWalkModule(), "AutoWalkModule")
        registerRequired(me.collective.client.modules.combat.BedAuraModule(), "BedAuraModule")
        registerRequired(me.collective.client.modules.misc.BetterChatModule(), "BetterChatModule")
        registerRequired(me.collective.client.modules.combat.BlockerModule(), "BlockerModule")
        registerRequired(me.collective.client.modules.render.BlurModule(), "BlurModule")
        registerRequired(me.collective.client.modules.render.BordersModule(), "BordersModule")
        registerRequired(me.collective.client.modules.combat.BowAimModule(), "BowAimModule")
        registerRequired(me.collective.client.modules.misc.BotsModule(), "BotsModule")
        registerRequired(me.collective.client.modules.render.BreakHighlightModule(), "BreakHighlightModule")
        registerRequired(me.collective.client.modules.render.ChamsModule(), "ChamsModule")
        registerRequired(me.collective.client.modules.misc.ChatFilterModule(), "ChatFilterModule")
        registerRequired(me.collective.client.modules.misc.ChestSearchBarModule(), "ChestSearchBarModule")
        registerRequired(me.collective.client.modules.player.ChestStealerModule(), "ChestStealerModule")
        registerRequired(me.collective.client.modules.exploit.ChorusControlModule(), "ChorusControlModule")
        registerRequired(me.collective.client.modules.client.ColorsModule(), "ColorsModule")
        registerRequired(me.collective.client.modules.misc.CoordLoggerModule(), "CoordLoggerModule")
        registerRequired(me.collective.client.modules.combat.CriticalsModule(), "CriticalsModule")
        registerRequired(me.collective.client.modules.render.CrosshairModule(), "CrosshairModule")
        registerRequired(me.collective.client.modules.misc.CustomDeathTextModule(), "CustomDeathTextModule")
        registerRequired(me.collective.client.modules.misc.WebhookNotifsModule(), "WebhookNotifsModule")
        registerRequired(me.collective.client.modules.render.ESPModule(), "ESPModule")
        registerRequired(me.collective.client.modules.movement.ElytraFlyModule(), "ElytraFlyModule")
        registerRequired(me.collective.client.modules.misc.ElytraSwapModule(), "ElytraSwapModule")
        registerRequired(me.collective.client.modules.movement.EntityControlModule(), "EntityControlModule")
        registerRequired(me.collective.client.modules.misc.ExtraScreenshotModule(), "ExtraScreenshotModule")
        registerRequired(me.collective.client.modules.misc.ExtraTabModule(), "ExtraTabModule")
        registerRequired(me.collective.client.modules.movement.FakeLagModule(), "FakeLagModule")
        registerRequired(me.collective.client.modules.exploit.FakeVanillaModule(), "FakeVanillaModule")
        registerRequired(me.collective.client.modules.movement.FastClimbModule(), "FastClimbModule")
        registerRequired(me.collective.client.modules.movement.FastFallModule(), "FastFallModule")
        registerRequired(me.collective.client.modules.exploit.FastLatencyModule(), "FastLatencyModule")
        registerRequired(me.collective.client.modules.player.FastPlaceModule(), "FastPlaceModule")
        registerRequired(me.collective.client.modules.movement.FastStairsModule(), "FastStairsModule")
        registerRequired(me.collective.client.modules.movement.FastSwimModule(), "FastSwimModule")
        registerRequired(me.collective.client.modules.movement.FastWebModule(), "FastWebModule")
        registerRequired(me.collective.client.modules.combat.FeetPlaceModule(), "FeetPlaceModule")
        registerRequired(me.collective.client.modules.movement.FireworksModule(), "FireworksModule")
        registerRequired(me.collective.client.modules.combat.FishTrapModule(), "FishTrapModule")
        registerRequired(me.collective.client.modules.movement.FlightModule(), "FlightModule")
        registerRequired(me.collective.client.modules.client.FontsModule(), "FontsModule")
        registerRequired(me.collective.client.modules.render.FreeLookModule(), "FreeLookModule")
        registerRequired(me.collective.client.modules.player.FreecamModule(), "FreecamModule")
        registerRequired(me.collective.client.modules.render.GlintModule(), "GlintModule")
        registerRequired(me.collective.client.modules.client.HUDModule(), "HUDModule")
        registerRequired(me.collective.client.modules.misc.HeavenModule(), "HeavenModule")
        registerRequired(me.collective.client.modules.movement.HighJumpModule(), "HighJumpModule")
        registerRequired(me.collective.client.modules.render.HighlightModule(), "HighlightModule")
        registerRequired(me.collective.client.modules.exploit.HitboxDesyncModule(), "HitboxDesyncModule")
        registerRequired(me.collective.client.modules.render.HitmarkerModule(), "HitmarkerModule")
        registerRequired(me.collective.client.modules.render.HoleESPModule(), "HoleESPModule")
        registerRequired(me.collective.client.modules.combat.HoleFillModule(), "HoleFillModule")
        registerRequired(me.collective.client.modules.movement.HoleSnapModule(), "HoleSnapModule")
        registerRequired(me.collective.client.modules.exploit.IllegalDisconnectModule(), "IllegalDisconnectModule")
        registerRequired(me.collective.client.modules.player.InventoryCleanerModule(), "InventoryCleanerModule")
        registerRequired(me.collective.client.modules.player.InventorySorterModule(), "InventorySorterModule")
        registerRequired(me.collective.client.modules.player.InventoryTweaksModule(), "InventoryTweaksModule")
        registerRequired(me.collective.client.modules.player.ItemSaverModule(), "ItemSaverModule")
        registerRequired(me.collective.client.modules.movement.JesusModule(), "JesusModule")
        registerRequired(me.collective.client.modules.combat.KeyPearlModule(), "KeyPearlModule")
        registerRequired(me.collective.client.modules.misc.KillEffectsModule(), "KillEffectsModule")
        registerRequired(me.collective.client.modules.render.LogoutSpotsModule(), "LogoutSpotsModule")
        registerRequired(me.collective.client.modules.movement.LongJumpModule(), "LongJumpModule")
        registerRequired(me.collective.client.modules.combat.MainhandModule(), "MainhandModule")
        registerRequired(me.collective.client.modules.misc.MiddleClickModule(), "MiddleClickModule")
        registerRequired(me.collective.client.modules.exploit.MultiTaskModule(), "MultiTaskModule")
        registerRequired(me.collective.client.modules.player.NameProtectModule(), "NameProtectModule")
        registerRequired(me.collective.client.modules.render.NameTagsModule(), "NameTagsModule")
        registerRequired(me.collective.client.modules.exploit.NewChunksModule(), "NewChunksModule")
        registerRequired(me.collective.client.modules.render.NoBobModule(), "NoBobModule")
        registerRequired(me.collective.client.modules.player.NoEffectsModule(), "NoEffectsModule")
        registerRequired(me.collective.client.modules.movement.NoFallModule(), "NoFallModule")
        registerRequired(me.collective.client.modules.player.NoGlitchBlocksModule(), "NoGlitchBlocksModule")
        registerRequired(me.collective.client.modules.combat.NoHitDelayModule(), "NoHitDelayModule")
        registerRequired(me.collective.client.modules.player.NoInteractModule(), "NoInteractModule")
        registerRequired(me.collective.client.modules.movement.NoJumpDelayModule(), "NoJumpDelayModule")
        registerRequired(me.collective.client.modules.exploit.NoMineAnimationModule(), "NoMineAnimationModule")
        registerRequired(me.collective.client.modules.misc.NoNarratorModule(), "NoNarratorModule")
        registerRequired(me.collective.client.modules.misc.NoPacketKickModule(), "NoPacketKickModule")
        registerRequired(me.collective.client.modules.render.NoRenderModule(), "NoRenderModule")
        registerRequired(me.collective.client.modules.misc.NoRotateModule(), "NoRotateModule")
        registerRequired(me.collective.client.modules.movement.NoSlowModule(), "NoSlowModule")
        registerRequired(me.collective.client.modules.client.NotificationsModule(), "NotificationsModule")
        registerRequired(me.collective.client.modules.player.NukerModule(), "NukerModule")
        registerRequired(me.collective.client.modules.combat.OffhandModule(), "OffhandModule")
        registerRequired(me.collective.client.modules.render.ParticlesModule(), "ParticlesModule")
        registerRequired(me.collective.client.modules.exploit.PhaseModule(), "PhaseModule")
        registerRequired(me.collective.client.modules.render.PhaseESPModule(), "PhaseESPModule")
        registerRequired(me.collective.client.modules.exploit.PingSpoofModule(), "PingSpoofModule")
        registerRequired(me.collective.client.modules.exploit.PortalGodModeModule(), "PortalGodModeModule")
        registerRequired(me.collective.client.modules.combat.PistonCrystalModule(), "PistonCrystalModule")
        registerRequired(me.collective.client.modules.combat.PusherModule(), "PusherModule")
        registerRequired(me.collective.client.modules.exploit.RaytraceBypassModule(), "RaytraceBypassModule")
        registerRequired(me.collective.client.modules.exploit.ReachModule(), "ReachModule")
        registerRequired(me.collective.client.modules.player.ReplenishModule(), "ReplenishModule")
        registerRequired(me.collective.client.modules.exploit.RocketExtenderModule(), "RocketExtenderModule")
        registerRequired(me.collective.client.modules.player.RotationLockModule(), "RotationLockModule")
        registerRequired(me.collective.client.modules.movement.SafeWalkModule(), "SafeWalkModule")
        registerRequired(me.collective.client.modules.player.ScaffoldModule(), "ScaffoldModule")
        registerRequired(me.collective.client.modules.player.HighwayBuilderModule(), "HighwayBuilderModule")
        registerRequired(me.collective.client.modules.render.SearchModule(), "SearchModule")
        registerRequired(me.collective.client.modules.combat.SelfFillModule(), "SelfFillModule")
        registerRequired(me.collective.client.modules.combat.SelfTrapModule(), "SelfTrapModule")
        registerRequired(me.collective.client.modules.combat.SelfWebModule(), "SelfWebModule")
        registerRequired(me.collective.client.modules.client.SettingsModule(), "SettingsModule")
        registerRequired(me.collective.client.modules.render.ShaderModule(), "ShaderModule")
        registerRequired(me.collective.client.modules.render.SkeletonModule(), "SkeletonModule")
        registerRequired(me.collective.client.modules.misc.SkinFlickerModule(), "SkinFlickerModule")
        registerRequired(me.collective.client.modules.render.SkyboxModule(), "SkyboxModule")
        registerRequired(me.collective.client.modules.misc.SoundBlockerModule(), "SoundBlockerModule")
        registerRequired(me.collective.client.modules.player.SourceFillModule(), "SourceFillModule")
        registerRequired(me.collective.client.modules.misc.SpammerModule(), "SpammerModule")
        registerRequired(me.collective.client.modules.movement.SpeedModule(), "SpeedModule")
        registerRequired(me.collective.client.modules.player.SpeedMineModule(), "SpeedMineModule")
        registerRequired(me.collective.client.modules.movement.SprintModule(), "SprintModule")
        registerRequired(me.collective.client.modules.misc.StashFinderModule(), "StashFinderModule")
        registerRequired(me.collective.client.modules.player.StashMover(), "StashMover")
        registerRequired(me.collective.client.modules.player.StashSorter(), "StashSorter")
        registerRequired(me.collective.client.modules.movement.StepModule(), "StepModule")
        registerRequired(me.collective.client.modules.misc.SwingModule(), "SwingModule")
        registerRequired(me.collective.client.modules.exploit.TimerModule(), "TimerModule")
        registerRequired(me.collective.client.modules.render.TooltipsModule(), "TooltipsModule")
        registerRequired(me.collective.client.modules.render.TracersModule(), "TracersModule")
        registerRequired(me.collective.client.modules.render.TrailsModule(), "TrailsModule")
        registerRequired(me.collective.client.modules.render.TrajectoriesModule(), "TrajectoriesModule")
        registerRequired(me.collective.client.modules.exploit.TridentModule(), "TridentModule")
        registerRequired(me.collective.client.modules.render.TunnelsModule(), "TunnelsModule")
        registerRequired(me.collective.client.modules.client.UIModule(), "UIModule")
        registerRequired(me.collective.client.modules.movement.VelocityModule(), "VelocityModule")
        registerRequired(me.collective.client.modules.render.ViewClipModule(), "ViewClipModule")
        registerRequired(me.collective.client.modules.render.ViewModelModule(), "ViewModelModule")
        registerRequired(me.collective.client.modules.misc.VisualRangeModule(), "VisualRangeModule")
        registerRequired(me.collective.client.modules.render.VoidESPModule(), "VoidESPModule")
        registerRequired(me.collective.client.modules.movement.WarpModule(), "WarpModule")
        registerRequired(me.collective.client.modules.render.WaypointsModule(), "WaypointsModule")
        registerRequired(me.collective.client.modules.combat.WebAuraModule(), "WebAuraModule")
        registerRequired(me.collective.client.modules.exploit.XCarryModule(), "XCarryModule")
        registerRequired(me.collective.client.modules.render.XrayModule(), "XrayModule")
        registerRequired(me.collective.client.modules.render.ZoomModule(), "ZoomModule")
        registerRequired(me.collective.client.modules.misc.IrcModule(), "IrcModule")

        registerPathfindingModules()
        (entries as MutableList<Module>).sortWith(Comparator.comparing(NamedFeature::getName))
    }

    private fun registerRequired(module: Module, name: String) {
        if (!registerModule(module)) {
            throw IllegalStateException("Registration rejected $name")
        }
    }

    override fun register(entry: Any): Boolean = registerModule(entry as Module)

    override fun unregister(entry: Any): Boolean = unregisterModule(entry as Module)

    fun unregisterModule(module: Module): Boolean {
        modulesByClass.remove(module.javaClass, module)
        return super.unregisterEntry(module)
    }

    override fun fromJson(config: JsonElement) {
        for (module in getRegistry()) {
            if (module !is HudWidgetModule) {
                try {
                    val configObject = config.asJsonObject
                    val moduleConfigName = module.getConfigName()
                    val moduleConfig = LegacyBranding.findModuleConfig(configObject, moduleConfigName) ?: continue
                    module.fromJson(moduleConfig)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun toJson(): JsonElement {
        val config = JsonObject()
        config.addProperty("client", "collective")
        config.addProperty("category", "all")
        for (module in getRegistry()) {
            if (module !is HudWidgetModule) {
                try {
                    config.add(module.getConfigName(), module.toJson())
                } catch (_: Exception) {
                }
            }
        }
        return config
    }

    override fun getConfigName(): String = "modules.json"

    override fun registerEntry(entry: NamedEntry?): Boolean =
        entry is Module && registerModule(entry)

    @Deprecated("Use getModule instead")
    @Suppress("UNCHECKED_CAST")
    override fun getByClass(moduleType: Class<*>): NamedEntry? =
        getModule(moduleType as Class<out Module>)

    fun getModulesByCategory(category: ModuleCategory): List<Module> {
        val registeredModules = entries as List<Module>
        return registeredModules.stream()
            .filter { module -> module.getCategory() == category }
            .collect(Collectors.toList())
    }

    @EventHandler
    private fun onInputPress(inputEvent: InputPressEvent) {
        if (KeyUtils.isPhysicalKeyDown(292)) {
            return
        }
        for (module in entries) {
            val keybind = module.getKeybind()
            if (keybind.mouse() != inputEvent.isMouse) {
                continue
            }
            if (keybind.code() != inputEvent.code) {
                continue
            }
            if (keybind.mode() == Keybind.Mode.TOGGLE) {
                module.toggle()
            }
        }
    }

    @EventHandler(priority = -200)
    private fun onWorldRender(renderEvent: WorldRenderEvent.Main) {
        for (module in entries) {
            val keybind = module.getKeybind()
            val holdBinding = keybind.mode() == Keybind.Mode.HOLD
            if (keybind.isUnbound()) {
                continue
            }
            if (keybind.mode() == Keybind.Mode.TOGGLE) {
                continue
            }
            if (ClientAccess.collectiveMinecraft.screen != null && module.isToggled() != holdBinding) {
                continue
            }
            val shouldToggle = holdBinding == KeyUtils.isPhysicallyDown(keybind)
            module.setToggled(shouldToggle)
        }
    }

    override fun unregisterEntry(entry: NamedEntry?): Boolean =
        entry is Module && unregisterModule(entry)

    fun registerModule(module: Module): Boolean {
        val moduleType = module.javaClass
        if (ExperimentalFeatures.isClassHidden(moduleType)) {
            return false
        }
        ExperimentalFeatures.unregisterHiddenSettings(module)
        modulesByClass[moduleType] = module
        return super.registerEntry(module)
    }

    @Suppress("UNCHECKED_CAST")
    @Deprecated("Use ClientContext.getModule instead")
    fun <T : Module> getModule(moduleType: Class<T>): T = modulesByClass[moduleType] as T

    @Suppress("UNCHECKED_CAST")
    fun <T : Module> getModuleOrNull(moduleType: Class<T>): T? = modulesByClass[moduleType] as T?
}
