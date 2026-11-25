package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientInitializationEvent
import me.collective.client.events.EventHandler
import me.collective.client.modules.HudWidgetModule
import me.collective.client.modules.Module
import me.collective.client.modules.hud.ArmorModule
import me.collective.client.modules.hud.ChatModule
import me.collective.client.modules.hud.CryptoModule
import me.collective.client.modules.hud.DirectionModule
import me.collective.client.modules.hud.EffectsModule
import me.collective.client.modules.hud.EntityListModule
import me.collective.client.modules.hud.GraphModule
import me.collective.client.modules.hud.IndicatorModule
import me.collective.client.modules.hud.InventoryModule
import me.collective.client.modules.hud.LagOMeterModule
import me.collective.client.modules.hud.MapModule
import me.collective.client.modules.hud.MetricsModule
import me.collective.client.modules.hud.ModuleListModule
import me.collective.client.modules.hud.MusicModule
import me.collective.client.modules.hud.PlayerModelModule
import me.collective.client.modules.hud.PositionModule
import me.collective.client.modules.hud.TextRadarModule
import me.collective.client.modules.hud.TotemsModule
import me.collective.client.modules.hud.WatermarkModule
import me.collective.client.modules.hud.WelcomerModule
import me.collective.client.settings.JsonConfigurable
import java.util.AbstractMap.SimpleEntry
import java.util.function.Predicate

class HudModuleRegistry : ClientAccess, JsonConfigurable {

    init {
        ClientAccess.eventBus.subscribe(this)
        val registry = ClientContext.moduleRegistry
        registry.registerModule(WatermarkModule())
        registry.registerModule(ModuleListModule())
        registry.registerModule(ArmorModule())
        registry.registerModule(GraphModule())
        registry.registerModule(EffectsModule())
        registry.registerModule(WelcomerModule())
        registry.registerModule(PositionModule())
        registry.registerModule(DirectionModule())
        registry.registerModule(TextRadarModule())
        registry.registerModule(MetricsModule())
        registry.registerModule(TotemsModule())
        registry.registerModule(LagOMeterModule())
        registry.registerModule(InventoryModule())
        registry.registerModule(MusicModule())
        registry.registerModule(PlayerModelModule())
        registry.registerModule(EntityListModule())
        registry.registerModule(ChatModule())
        registry.registerModule(MapModule())
        registry.registerModule(CryptoModule())
        registry.registerModule(IndicatorModule())
    }

    override fun fromJson(json: JsonElement) {
        val configuration = json.asJsonObject
        configuration.entrySet().stream()
            .sorted(Comparator.comparing { entry ->
                try {
                    val moduleConfiguration = entry.value.asJsonObject
                    val hudConfiguration = moduleConfiguration.getAsJsonObject("hud")
                    hudConfiguration.get("index").asInt
                } catch (_: Throwable) {
                    0
                }
            })
            .map { entry -> SimpleEntry(findConfiguredModule(entry), entry.value) }
            .filter { entry -> entry.key is HudWidgetModule }
            .forEach { entry ->
                try {
                    val module = entry.key ?: return@forEach
                    module.fromJson(entry.value)
                } catch (failure: Exception) {
                    failure.printStackTrace()
                }
            }
    }

    override fun toJson(): JsonElement {
        val configuration = JsonObject()
        val registry = ClientContext.moduleRegistry
        for (module in registry.getRegistry()) {
            if (module is HudWidgetModule) {
                try {
                    configuration.add(module.getConfigName(), module.toJson())
                } catch (failure: Exception) {
                    failure.printStackTrace()
                }
            }
        }
        return configuration
    }

    override fun getConfigName(): String = "hud.json"

    @EventHandler
    private fun refreshHudAnchors(event: ClientInitializationEvent) {
        val registry = ClientContext.moduleRegistry
        for (module in registry.getRegistry()) {
            if (module is HudWidgetModule) {
                module.setAnchor(module.getAnchor())
            }
        }
    }

    private companion object {
        @JvmStatic
        fun findConfiguredModule(configurationEntry: Map.Entry<String, JsonElement>): Module? {
            val registry = ClientContext.moduleRegistry
            val entryName = configurationEntry.key
            val matchesName = Predicate<Module> { candidate ->
                candidate.getConfigName().equals(entryName, ignoreCase = true)
            }
            return registry.findFirst(matchesName).orElse(null)
        }
    }
}
