package me.collective.client.bots.network

import com.mojang.authlib.exceptions.AuthenticationException
import me.collective.client.ClientAccess
import me.collective.client.bots.BotClient
import me.collective.client.bots.BotConnectionState
import me.collective.client.bots.control.BotCustomPayloadCache
import net.minecraft.client.ClientBrandRetriever
import net.minecraft.network.Connection
import net.minecraft.network.ConnectionProtocol
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.PacketSendListener
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
import net.minecraft.network.protocol.common.custom.BrandPayload
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket
import net.minecraft.network.protocol.login.*
import net.minecraft.util.Crypt
import net.minecraft.util.Util
import java.math.BigInteger
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.SecretKey

class BotLoginPacketListener(
    val bot: BotClient,
    val connection: Connection
) : ClientLoginPacketListener, ClientAccess {

    override fun handleLoginFinished(packet: ClientboundLoginFinishedPacket) {
        bot.playerState.gameProfile = packet.gameProfile
        bot.connectionState = BotConnectionState.CONFIGURING

        connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, BotConfigurationPacketListener(bot, connection))
        connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE)
        connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND)
        connection.send(ServerboundCustomPayloadPacket(BrandPayload(ClientBrandRetriever.getClientModName())))
        connection.send(ServerboundClientInformationPacket(ClientAccess.collectiveMinecraft.options.buildPlayerInformation()))
        BotCustomPayloadCache.replay(bot.channel, connection, ConnectionProtocol.CONFIGURATION)
    }

    override fun handleDisconnect(packet: ClientboundLoginDisconnectPacket) {
        val reason = packet.reason.string
        bot.disconnectReason = reason
        bot.disconnect(reason)
    }

    override fun onDisconnect(details: DisconnectionDetails) {
        bot.disconnectReason = details.reason.string
    }

    override fun handleHello(packet: ClientboundHelloPacket) {
        bot.connectionState = BotConnectionState.LOGGING_IN

        val secretKey: SecretKey
        val publicKey: PublicKey
        val serverHash: String
        val decryptCipher: Cipher
        val encryptCipher: Cipher
        val keyPacket: ServerboundKeyPacket

        try {
            secretKey = Crypt.generateSecretKey()
            publicKey = packet.publicKey
            serverHash = BigInteger(Crypt.digestData(packet.serverId, publicKey, secretKey)).toString(16)
            decryptCipher = Crypt.getCipher(Cipher.DECRYPT_MODE, secretKey)
            encryptCipher = Crypt.getCipher(Cipher.ENCRYPT_MODE, secretKey)
            keyPacket = ServerboundKeyPacket(secretKey, publicKey, packet.challenge)
        } catch (e: Exception) {
            bot.disconnectReason = "Encryption error: ${e.message}"
            bot.disconnect("Encryption error")
            return
        }

        if (packet.shouldAuthenticate()) {
            Util.ioPool().execute {
                val authError = authenticate(serverHash)
                if (authError != null) {
                    bot.disconnectReason = authError
                    bot.disconnect(authError)
                } else {
                    applyEncryption(keyPacket, decryptCipher, encryptCipher)
                }
            }
        } else {
            applyEncryption(keyPacket, decryptCipher, encryptCipher)
        }
    }

    private fun applyEncryption(keyPacket: ServerboundKeyPacket, decrypt: Cipher, encrypt: Cipher) {
        connection.send(keyPacket, PacketSendListener.thenRun {
            connection.setEncryptionKey(decrypt, encrypt)
        })
    }

    private fun authenticate(serverHash: String): String? {
        if (!bot.profile.isOnlineMode) {
            return "Server is in online-mode, but bot has no access token"
        }

        return try {
            ClientAccess.collectiveMinecraft.services().sessionService().joinServer(
                bot.profile.uuid,
                bot.profile.accessToken,
                serverHash
            )
            null
        } catch (e: AuthenticationException) {
            "Session rejected: ${e.message}"
        }
    }

    override fun handleCustomQuery(packet: ClientboundCustomQueryPacket) {
        connection.send(ServerboundCustomQueryAnswerPacket(packet.transactionId, null))
    }

    override fun handleRequestCookie(packet: ClientboundCookieRequestPacket) {
        connection.send(ServerboundCookieResponsePacket(packet.key, bot.getCookie(packet.key)))
    }

    override fun handleCompression(packet: ClientboundLoginCompressionPacket) {
        connection.setupCompression(packet.compressionThreshold, false)
    }

    override fun isAcceptingMessages(): Boolean = connection.isConnected
}
