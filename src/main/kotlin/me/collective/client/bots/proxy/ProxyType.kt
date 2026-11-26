package me.collective.client.bots.proxy

enum class ProxyType(val displayName: String) {
    SOCKS4("SOCKS4"),
    SOCKS5("SOCKS5");

    fun next(): ProxyType = if (this == SOCKS4) SOCKS5 else SOCKS4
}
