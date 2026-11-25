package me.collective.client.bots.proxy

import io.netty.channel.Channel
import io.netty.util.AttributeKey
import io.netty.util.concurrent.Future
import io.netty.util.concurrent.Promise
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

object SocksProxyConnector {
    const val HANDLER_NAME: String = "collective_socks_tunnel"
    val HANDSHAKE_FUTURE_KEY: AttributeKey<Future<Void>> = AttributeKey.valueOf("collective_socks_handshake")
    val TUNNEL_FUTURE_KEY: AttributeKey<Future<Void>> get() = HANDSHAKE_FUTURE_KEY
    private val addressCache: MutableMap<String, InetSocketAddress> = ConcurrentHashMap()

    fun attach(proxy: ProxyConfig?) = prepare(proxy)

    fun resolveProxyAddress(proxy: ProxyConfig): InetSocketAddress? {
        val key = proxy.formatAddress()
        val cached = addressCache[key]
        if (cached != null) return cached

        val resolved = InetSocketAddress(proxy.host, proxy.port)
        if (resolved.isUnresolved) return null
        addressCache[key] = resolved
        return resolved
    }

    fun prepare(proxy: ProxyConfig?) {
        if (proxy != null && proxy.isValid()) {
            resolveProxyAddress(proxy)
        }
    }

    fun install(channel: Channel?, proxy: ProxyConfig?) {
        if (channel == null || proxy == null || !proxy.isValid()) return

        val pipeline = channel.pipeline()
        if (pipeline.get(HANDLER_NAME) == null) {
            val addr = resolveProxyAddress(proxy)
            if (addr == null) {
                val promise = channel.eventLoop().newPromise<Void>()
                promise.tryFailure(IOException("Не удалось разрешить адрес прокси ${proxy.formatAddress()}"))
                channel.attr(HANDSHAKE_FUTURE_KEY).set(promise)
                channel.close()
            } else {
                val promise = channel.eventLoop().newPromise<Void>()
                channel.attr(HANDSHAKE_FUTURE_KEY).set(promise)
                pipeline.addFirst(HANDLER_NAME, SocksHandshakeHandler(proxy, addr, promise))
            }
        }
    }
}
