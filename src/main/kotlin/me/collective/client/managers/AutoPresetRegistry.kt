package me.collective.client.managers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ConnectScreenConnectEvent
import me.collective.client.events.EventHandler
import me.collective.client.settings.JsonConfigurable
import me.collective.client.settings.options.PresetCategory
import me.collective.client.utils.chat.ChatUtils
import me.collective.client.utils.inventory.Kit
import me.collective.client.utils.network.AutoPresetServerEntry
import net.minecraft.network.chat.Component
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate

class AutoPresetRegistry : ClientAccess, JsonConfigurable {
    private val serverSettings: MutableMap<String, AutoPresetServerEntry> = HashMap()
    private var lastServer: String? = null

    val servers: Collection<String>
        @JvmName("getServersProp")
        get() = serverSettings.keys

    fun getServers(): Collection<String> = serverSettings.keys

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    override fun fromJson(json: JsonElement) {
        val root = json.asJsonObject
        for ((server, serverJson) in root.entrySet()) {
            val settingsJson = serverJson.asJsonObject
            for ((categoryName, presetJson) in settingsJson.entrySet()) {
                val category = PresetCategory.findByName(categoryName)
                val presetName = presetJson.asString
                if (category != null) {
                    setPreset(server, category, presetName)
                }
            }
            if (settingsJson.has("kit")) {
                setKit(server, settingsJson.get("kit").asString)
            }
        }
    }

    override fun toJson(): JsonElement {
        val root = JsonObject()
        for (entry in serverSettings.values) {
            val settingsJson = JsonObject()
            for ((category, presetName) in entry.presets) {
                val categoryKey = category.getName().lowercase(Locale.ROOT)
                settingsJson.addProperty(categoryKey, presetName)
            }
            if (entry.hasKit()) {
                settingsJson.addProperty("kit", entry.kitName)
            }
            root.add(entry.server, settingsJson)
        }
        return root
    }

    fun removeKit(server: String?): Boolean {
        val settings = serverSettings[normalizeServer(server)] ?: return false
        settings.kitName = null
        return true
    }

    override fun getConfigName(): String {
        val presetDirectoryName = ConfigurationManager.PRESETS_DIRECTORY.fileName
        return presetDirectoryName.resolve("auto.json").toString()
    }

    fun removePreset(server: String?, category: PresetCategory): Boolean {
        val settings = serverSettings[normalizeServer(server)] ?: return false
        return settings.presets.remove(category) != null
    }

    fun setPreset(server: String?, category: PresetCategory, presetName: String) {
        val normalized = normalizeServer(server)
        val settings = serverSettings.computeIfAbsent(normalized) { AutoPresetServerEntry(normalized) }
        settings.presets[category] = presetName
    }

    @EventHandler
    fun onConnectScreenConnectEvent(event: ConnectScreenConnectEvent) {
        val serverAddress = event.serverAddress
        if (!serverAddress.equals(lastServer, ignoreCase = true)) {
            lastServer = serverAddress
            val settings = serverSettings[normalizeServer(lastServer)] ?: return

            val messageCounter = AtomicInteger(-2)

            settings.presets.forEach { (category, presetName) ->
                val presetRegistry = ClientContext.configurationManager.getPresetRegistry(category)
                presetRegistry.applyPreset(presetName)
                ClientContext.taskScheduler.scheduleRotationTask({
                    val messageId = messageCounter.getAndDecrement()
                    val signature = ChatUtils.createSignature(messageId)
                    val messageText = "Preset %s has been loaded.".format(presetName)
                    ChatUtils.printChat(Component.literal(messageText), signature)
                }, 0)
            }

            if (settings.hasKit()) {
                val kitName = settings.kitName
                if (kitName != null) {
                    ClientContext.kitRegistry.findFirst(Predicate<Kit> { kit ->
                        kit.getName().equals(kitName, ignoreCase = true)
                    }).ifPresent {
                        ClientContext.kitRegistry.setCurrentKitName(kitName)
                        ClientContext.taskScheduler.scheduleRunTickTask {
                            val messageId = messageCounter.getAndDecrement()
                            val signature = ChatUtils.createSignature(messageId)
                            val messageText = "Kit %s has been loaded.".format(kitName)
                            ChatUtils.printChat(Component.literal(messageText), signature)
                        }
                    }
                }
            }
        }
    }

    fun removeServer(server: String?): Boolean {
        return serverSettings.remove(normalizeServer(server)) != null
    }

    fun setKit(server: String?, kitName: String?) {
        val normalized = normalizeServer(server)
        val settings = serverSettings.computeIfAbsent(normalized) { AutoPresetServerEntry(normalized) }
        settings.kitName = kitName
    }

    companion object {
        /** Ключи серверов нормализуются в одном месте: имена хостов могут быть в разном регистре. */
        private fun normalizeServer(server: String?): String = server?.lowercase(Locale.ROOT) ?: ""
    }
}
