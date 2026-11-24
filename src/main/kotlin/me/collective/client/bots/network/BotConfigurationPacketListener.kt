package me.collective.client.bots.network

import me.collective.client.bots.BotClient
import me.collective.client.bots.control.BotPacketDispatcher
import me.collective.client.bots.control.RegistryDataMerger
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess.Frozen
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry
import net.minecraft.network.Connection
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.common.*
import net.minecraft.network.protocol.common.custom.BrandPayload
import net.minecraft.network.protocol.configuration.*
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket
import net.minecraft.network.protocol.game.GameProtocols
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload
import net.minecraft.world.flag.FeatureFlags
import java.util.concurrent.ConcurrentHashMap

class BotConfigurationPacketListener(
    val bot: BotClient,
    val connection: Connection
) : ClientConfigurationPacketListener {

    private val registryDataMap: MutableMap<ResourceKey<out Registry<*>>, MutableList<PackedRegistryEntry>> = ConcurrentHashMap()
    private val tagPayloads: MutableMap<ResourceKey<out Registry<*>>, NetworkPayload> = ConcurrentHashMap()

    override fun handleTransfer(packet: ClientboundTransferPacket) {
        bot.handleTransfer(packet.host, packet.port)
    }

    override fun handleResourcePackPop(packet: ClientboundResourcePackPopPacket) {}

    override fun onDisconnect(details: DisconnectionDetails) {
        bot.disconnectReason = details.reason.string
        bot.disconnect(details.reason.string)
    }

    override fun handleShowDialog(packet: ClientboundShowDialogPacket) {}

    override fun handleCustomPayload(packet: ClientboundCustomPayloadPacket) {
        val payload = packet.payload
        if (payload is BrandPayload) {
            bot.worldState.serverBrand = payload.brand
        }
    }

    override fun handlePing(packet: ClientboundPingPacket) {
        connection.send(ServerboundPongPacket(packet.id))
    }

    override fun handleClearDialog(packet: ClientboundClearDialogPacket) {}

    override fun handleServerLinks(packet: ClientboundServerLinksPacket) {}

    override fun handleCodeOfConduct(packet: ClientboundCodeOfConductPacket) {
        BotPacketDispatcher.scheduleDelayed(bot.eventLoop, {
            if (connection.isConnected && connection.packetListener is BotConfigurationPacketListener) {
                connection.send(ServerboundAcceptCodeOfConductPacket.INSTANCE)
            }
        }, 1400L)
    }

    override fun handleUpdateTags(packet: ClientboundUpdateTagsPacket) {
        tagPayloads.putAll(packet.tags)
        bot.worldState.tags = HashMap(packet.tags)
    }

    override fun handleResourcePackPush(packet: ClientboundResourcePackPushPacket) {
        BotPacketDispatcher.acknowledgeResourcePack(bot.eventLoop, connection, packet.id)
    }

    override fun handleConfigurationFinished(packet: ClientboundFinishConfigurationPacket) {
        val frozenRegistry: Frozen = try {
            RegistryDataMerger.mergeRegistries(registryDataMap, tagPayloads)
        } catch (t: Throwable) {
            bot.worldState.frozenRegistry ?: run {
                val err = "Failed to assemble server registries: ${t.message}"
                bot.disconnectReason = err
                bot.disconnect(err)
                return
            }
        }

        bot.worldState.frozenRegistry = frozenRegistry

        connection.setupInboundProtocol(
            GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(frozenRegistry)),
            BotPlayPacketListener(bot, connection)
        )
        connection.send(ServerboundFinishConfigurationPacket.INSTANCE)
        connection.setupOutboundProtocol(
            GameProtocols.SERVERBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(frozenRegistry),
                GameProtocols.Context { false }
            )
        )
    }

    override fun handleDisconnect(packet: ClientboundDisconnectPacket) {
        val reason = packet.reason.string
        bot.disconnectReason = reason
        bot.disconnect(reason)
    }

    override fun handleCustomReportDetails(packet: ClientboundCustomReportDetailsPacket) {}

    override fun handleRegistryData(packet: ClientboundRegistryDataPacket) {
        registryDataMap.computeIfAbsent(packet.registry) { ArrayList() }.addAll(packet.entries)
    }

    override fun isAcceptingMessages(): Boolean = connection.isConnected

    override fun handleSelectKnownPacks(packet: ClientboundSelectKnownPacks) {
        connection.send(ServerboundSelectKnownPacks(emptyList()))
    }

    override fun handleEnabledFeatures(packet: ClientboundUpdateEnabledFeaturesPacket) {
        bot.worldState.enabledFeatures = FeatureFlags.REGISTRY.fromNames(packet.features)
    }

    override fun handleRequestCookie(packet: ClientboundCookieRequestPacket) {
        connection.send(ServerboundCookieResponsePacket(packet.key, bot.getCookie(packet.key)))
    }

    override fun handleKeepAlive(packet: ClientboundKeepAlivePacket) {
        connection.send(ServerboundKeepAlivePacket(packet.id))
        bot.lastKeepAliveId = packet.id
    }

    override fun handleResetChat(packet: ClientboundResetChatPacket) {}

    override fun handleStoreCookie(packet: ClientboundStoreCookiePacket) {
        bot.setCookie(packet.key, packet.payload)
    }
}
