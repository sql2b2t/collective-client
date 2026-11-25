package me.collective.client.bots.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.collective.client.bots.BotClient
import me.collective.client.bots.BotManager
import me.collective.client.bots.BotProfile
import me.collective.client.bots.BotRole
import me.collective.client.bots.BotTaskStatus
import me.collective.client.managers.ConfigurationManager
import java.nio.file.Files
import java.nio.file.Path

object BotSettingsStorage {
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val CONFIG_PATH: Path get() = ConfigurationManager.ROOT_DIRECTORY.resolve("bots.json")

    fun save() {
        try {
            val root = JsonObject()
            root.addProperty("connectDelay", BotManager.connectDelayMs)
            root.addProperty("viewDistance", BotManager.viewDistance)
            root.addProperty("autoReconnect", BotManager.autoReconnect)
            root.addProperty("autoRespawn", BotManager.autoRespawn)

            val botsArray = JsonArray()
            for (bot in BotManager.bots) {
                val botObj = JsonObject()
                val p = bot.profile
                botObj.addProperty("name", p.username)
                botObj.addProperty("address", p.serverAddress)
                botObj.addProperty("proxyId", p.proxyId)
                botObj.addProperty("task", p.taskStatus.name)
                botObj.addProperty("version", p.protocolVersion)
                botObj.addProperty("role", p.role.name)
                botObj.addProperty("anarchyServerNumber", p.anarchyServerNumber)
                botObj.addProperty("bankNumber", p.bankNumber)
                botsArray.add(botObj)
            }
            root.add("bots", botsArray)

            Files.createDirectories(CONFIG_PATH.parent)
            Files.writeString(CONFIG_PATH, GSON.toJson(root))
        } catch (ignored: Throwable) {}
    }

    fun load() {
        if (!Files.exists(CONFIG_PATH)) return
        try {
            val content = Files.readString(CONFIG_PATH)
            val root = JsonParser.parseString(content).asJsonObject

            if (root.has("connectDelay")) {
                BotManager.connectDelayMs = root.get("connectDelay").asLong
            }
            if (root.has("viewDistance")) {
                BotManager.viewDistance = root.get("viewDistance").asInt
            }
            if (root.has("autoReconnect")) {
                BotManager.autoReconnect = root.get("autoReconnect").asBoolean
            }
            if (root.has("autoRespawn")) {
                BotManager.autoRespawn = root.get("autoRespawn").asBoolean
            }

            if (root.has("bots")) {
                val array = root.getAsJsonArray("bots")
                for (elem in array) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        val name = obj.get("name")?.asString ?: continue
                        val addr = obj.get("address")?.asString ?: continue
                        val proxyId = obj.get("proxyId")?.asString ?: ""

                        val profile = BotProfile(name, addr, proxyId)
                        if (obj.has("task")) {
                            profile.taskStatus = try {
                                BotTaskStatus.valueOf(obj.get("task").asString)
                            } catch (e: Exception) {
                                BotTaskStatus.IDLE
                            }
                        }
                        if (obj.has("role")) {
                            profile.role = try {
                                BotRole.valueOf(obj.get("role").asString)
                            } catch (e: Exception) {
                                BotRole.NONE
                            }
                        }
                        if (obj.has("version")) {
                            profile.protocolVersion = obj.get("version").asString
                        }
                        if (obj.has("anarchyServerNumber")) {
                            profile.anarchyServerNumber = obj.get("anarchyServerNumber").asInt
                        }
                        if (obj.has("bankNumber")) {
                            profile.bankNumber = obj.get("bankNumber").asInt
                        }
                        BotManager.addBot(profile)
                    }
                }
            }
        } catch (ignored: Throwable) {}
    }
}
