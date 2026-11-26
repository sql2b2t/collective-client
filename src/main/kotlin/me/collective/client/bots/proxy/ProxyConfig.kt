package me.collective.client.bots.proxy

import java.util.UUID

class ProxyConfig(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var host: String = "",
    var port: Int = 1080,
    var type: ProxyType = ProxyType.SOCKS5,
    var username: String = "",
    var password: String = "",
    var status: String = ""
) {
    fun formatAddress(): String = "$host:$port"

    fun getDisplayName(): String {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) formatAddress() else trimmed
    }

    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535

    fun hasAuth(): Boolean = username.isNotEmpty() || password.isNotEmpty()

    companion object {
        fun parse(
            name: String,
            address: String,
            type: ProxyType = ProxyType.SOCKS5,
            user: String = "",
            pass: String = ""
        ): ProxyConfig? {
            val trimmed = address.trim()
            val colonIndex = trimmed.lastIndexOf(':')
            if (colonIndex <= 0 || colonIndex == trimmed.length - 1) return null

            val parsedPort = trimmed.substring(colonIndex + 1).toIntOrNull() ?: return null
            if (parsedPort !in 1..65535) return null

            val hostPart = trimmed.substring(0, colonIndex).trim()
            if (hostPart.isEmpty()) return null

            val config = ProxyConfig(
                name = name,
                host = hostPart,
                port = parsedPort,
                type = type,
                username = user,
                password = pass
            )
            return if (config.isValid()) config else null
        }
    }
}
