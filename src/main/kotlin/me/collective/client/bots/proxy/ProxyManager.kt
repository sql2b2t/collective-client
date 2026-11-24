package me.collective.client.bots.proxy

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.collective.client.managers.ConfigurationManager
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

object ProxyManager {
    const val MAX_PROXIES: Int = 128
    val proxies: MutableList<ProxyConfig> = CopyOnWriteArrayList()
    var activeProxyId: String = ""
    var enabled: Boolean = false

    fun getActiveProxy(): ProxyConfig? {
        if (!enabled || activeProxyId.isEmpty()) return null
        return getProxy(activeProxyId)
    }

    fun getDefaultProxy(): ProxyConfig? = getActiveProxy()

    fun getById(id: String?): ProxyConfig? = getProxy(id)

    fun getProxy(id: String?): ProxyConfig? {
        if (id.isNullOrEmpty()) return null
        return proxies.firstOrNull { it.id == id }
    }

    fun addProxy(proxy: ProxyConfig): ProxyConfig? {
        if (!proxy.isValid() || proxies.size >= MAX_PROXIES) return null
        proxies.add(proxy)
        save()
        return proxy
    }

    fun removeProxy(id: String) {
        val proxy = getProxy(id) ?: return
        proxies.remove(proxy)
        if (activeProxyId == id) {
            activeProxyId = ""
            enabled = false
        }
        save()
    }

    fun setActive(id: String, isEnabled: Boolean) {
        activeProxyId = id
        enabled = isEnabled
        save()
    }

    fun load() {
        try {
            val file = ConfigurationManager.ROOT_DIRECTORY.resolve("proxies.json")
            if (!Files.exists(file)) return
            val jsonStr = Files.readString(file)
            val json = JsonParser.parseString(jsonStr).asJsonObject

            proxies.clear()
            val array = json.getAsJsonArray("proxies")
            if (array != null) {
                for (elem in array) {
                    val obj = elem.asJsonObject
                    val type = if ("SOCKS4".equals(obj.get("type")?.asString, ignoreCase = true)) {
                        ProxyType.SOCKS4
                    } else {
                        ProxyType.SOCKS5
                    }
                    val config = ProxyConfig(
                        id = obj.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                        name = obj.get("name")?.asString ?: "",
                        host = obj.get("host")?.asString ?: "",
                        port = obj.get("port")?.asInt ?: 1080,
                        type = type,
                        username = obj.get("username")?.asString ?: "",
                        password = obj.get("password")?.asString ?: "",
                        status = obj.get("status")?.asString ?: ""
                    )
                    if (config.isValid()) {
                        proxies.add(config)
                    }
                }
            }
            activeProxyId = json.get("activeId")?.asString ?: ""
            enabled = json.get("enabled")?.asBoolean ?: false
        } catch (_: Exception) {
        }
    }

    fun save() {
        try {
            val file = ConfigurationManager.ROOT_DIRECTORY.resolve("proxies.json")
            val root = JsonObject()
            root.addProperty("activeId", activeProxyId)
            root.addProperty("enabled", enabled)

            val array = JsonArray()
            for (proxy in proxies) {
                val obj = JsonObject()
                obj.addProperty("id", proxy.id)
                obj.addProperty("name", proxy.name)
                obj.addProperty("host", proxy.host)
                obj.addProperty("port", proxy.port)
                obj.addProperty("type", proxy.type.name)
                obj.addProperty("username", proxy.username)
                obj.addProperty("password", proxy.password)
                obj.addProperty("status", proxy.status)
                array.add(obj)
            }
            root.add("proxies", array)

            Files.createDirectories(file.parent)
            Files.writeString(file, root.toString())
        } catch (_: Exception) {
        }
    }
}
