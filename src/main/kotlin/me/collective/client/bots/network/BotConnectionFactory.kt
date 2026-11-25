package me.collective.client.bots.network

import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.util.concurrent.Future
import me.collective.client.ClientAccess
import me.collective.client.access.BotConnectionAccess
import me.collective.client.bots.BotClient
import me.collective.client.bots.proxy.ProxyConfig
import me.collective.client.bots.proxy.ProxyManager
import me.collective.client.bots.proxy.SocksProxyConnector
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress
import net.minecraft.client.multiplayer.resolver.ServerAddress
import net.minecraft.client.multiplayer.resolver.ServerNameResolver
import net.minecraft.network.Connection
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.login.LoginProtocols
import net.minecraft.network.protocol.login.ServerboundHelloPacket

object BotConnectionFactory : ClientAccess {
    private const val PROXY_TIMEOUT_MS: Long = 12000L

    fun normalizeAddress(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isEmpty() -> "127.0.0.1:25565"
            trimmed.toIntOrNull() != null -> "127.0.0.1:$trimmed"
            trimmed.startsWith(":") -> "127.0.0.1$trimmed"
            !trimmed.contains(":") -> "$trimmed:25565"
            else -> trimmed
        }
    }

    fun resolveProxy(bot: BotClient): ProxyConfig? {
        val proxyId = bot.profile.proxyId
        return if (proxyId.isNotEmpty()) ProxyManager.getById(proxyId) else null
    }

    private fun awaitProxyTunnel(channel: Channel) {
        val future: Future<Void>? = channel.attr(SocksProxyConnector.TUNNEL_FUTURE_KEY).get()
        if (future != null) {
            if (!future.awaitUninterruptibly(PROXY_TIMEOUT_MS)) {
                channel.close()
                throw IllegalStateException("Proxy tunnel timed out after 12s")
            }
            if (!future.isSuccess) {
                channel.close()
                val cause = future.cause()
                throw IllegalStateException("Proxy refused: ${cause?.message ?: "unknown"}")
            }
        }
    }

    fun connect(bot: BotClient): Channel {
        val normalizedAddress = normalizeAddress(bot.profile.serverAddress)
        val serverAddr = ServerAddress.parseString(normalizedAddress)
        val resolved = ServerNameResolver.DEFAULT.resolveAddress(serverAddr)
            .map(ResolvedServerAddress::asInetSocketAddress)
            .orElseThrow { IllegalStateException("Failed to resolve server address: ${bot.profile.serverAddress}") }

        val connection = Connection(PacketFlow.CLIENTBOUND)
        val botAccess = connection as? BotConnectionAccess
        botAccess?.isBotConnection = true

        val proxy = resolveProxy(bot)
        if (proxy != null) {
            SocksProxyConnector.attach(proxy)
            botAccess?.proxy = proxy
        }

        ViaFabricPlusProtocolAccess.injectProtocol(connection, serverAddr, resolved, bot.profile.protocolVersion)

        val channelFuture: ChannelFuture = Connection.connect(
            resolved,
            ClientAccess.collectiveMinecraft.options.useNativeTransport(),
            connection
        )
        channelFuture.syncUninterruptibly()
        val channel = channelFuture.channel()

        awaitProxyTunnel(channel)
        bot.attachConnection(connection, channel)

        val transferred = bot.isTransferred
        channel.eventLoop().execute {
            connection.initiateServerboundPlayConnection(
                resolved.hostName,
                resolved.port,
                LoginProtocols.SERVERBOUND,
                LoginProtocols.CLIENTBOUND,
                BotLoginPacketListener(bot, connection),
                transferred
            )
            connection.send(ServerboundHelloPacket(bot.profile.username, bot.profile.uuid))
        }

        return channel
    }
}
