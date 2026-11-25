package me.collective.client.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.collective.client.CollectiveClient
import java.util.Locale

object LegacyBranding {

    @JvmStatic
    fun canonicalModuleName(name: String): String {
        return when (name.lowercase(Locale.ROOT)) {
            "antivoid" -> "Avoid"
            "autoupgrade" -> "AutoTemplate"
            "discordnotifs" -> "WebhookNotifs"
            else -> name
        }
    }

    @JvmStatic
    fun findModuleConfig(config: JsonObject, moduleName: String): JsonElement? {
        if (config.has(moduleName)) {
            return config.get(moduleName)
        }
        return when (moduleName) {
            "Avoid" -> config.get("AntiVoid")
            "AutoTemplate" -> config.get("AutoUpgrade")
            "WebhookNotifs" -> config.get("DiscordNotifs")
            else -> null
        }
    }

    @JvmStatic
    fun migrateModule(moduleName: String, input: JsonElement?): JsonElement? {
        val module = input as? JsonObject ?: return input
        if (module.get("settings") !is JsonObject) {
            return input
        }
        if (moduleName != "Watermark" && moduleName != "Welcomer" &&
         moduleName != "AutoSign" && moduleName != "DiscordNotifs" &&
         moduleName != "WebhookNotifs" && moduleName != "FeetPlace" &&
         moduleName != "AutoFarm" && moduleName != "EntityControl"
        ) {
            return input
        }
        val migrated = module.deepCopy()
        val settings = migrated.getAsJsonObject("settings")
        when (moduleName) {
            "AutoFarm" -> {
                if (!settings.has("Frequency") && settings.has("BPT")) {
                    settings.add("Frequency", settings.get("BPT"))
                }
                settings.remove("BPT")
            }
            "EntityControl" -> {
                if (!settings.has("Baritone") && settings.has("PathFinder")) {
                    settings.add("Baritone", settings.get("PathFinder"))
                }
                settings.remove("PathFinder")
            }
            "FeetPlace" -> {
                val blockIgnore = settings.get("BlockIgnore")
                val mineIgnore = settings.get("MineIgnore")
                val mining = mineIgnore != null && mineIgnore.isJsonPrimitive &&
         mineIgnore.getAsJsonPrimitive().isBoolean && mineIgnore.getAsBoolean()
                if (blockIgnore != null && blockIgnore.isJsonPrimitive && blockIgnore.getAsJsonPrimitive().isBoolean) {
                    settings.addProperty(
                        "BlockIgnore",
                        if (blockIgnore.getAsBoolean()) "Both" else if (mining) "Mining" else "None"
                    )
                } else if (blockIgnore == null && mining) {
                    settings.addProperty("BlockIgnore", "Mining")
                }
                settings.remove("MineIgnore")
                val sequential = settings.get("Sequential")
                if (sequential != null && sequential.isJsonPrimitive && sequential.getAsJsonPrimitive().isBoolean) {
                    settings.addProperty("Sequential", if (sequential.getAsBoolean()) "Strict" else "None")
                }
            }
            "Watermark" -> replaceDefault(settings, "Text", "Collective", CollectiveClient.NAME)
            "Welcomer" -> replaceDefault(settings, "Text", "Welcome to Collective.", "Welcome to Collective.")
            "AutoSign" -> {
                // Пустые строки оставляем пустыми — текст на табличках не рекламируем.
            }
            "DiscordNotifs", "WebhookNotifs" -> {
            }
            else -> throw AssertionError(moduleName)
        }
        return migrated
    }

    private fun replaceDefault(settings: JsonObject, key: String, previous: String, current: String) {
        if (equalsString(settings.get(key), previous)) {
            settings.addProperty(key, current)
        }
    }

    private fun equalsString(value: JsonElement?, expected: String): Boolean {
        return value != null && value.isJsonPrimitive && value.getAsJsonPrimitive().isString &&
         expected == value.getAsString()
    }
}
