package me.collective.client.bots.network

import me.collective.client.bots.BotClient
import me.collective.client.bots.BotConnectionState
import net.minecraft.network.Connection
import net.minecraft.network.DisconnectionDetails
import net.minecraft.network.protocol.common.*
import net.minecraft.network.protocol.configuration.ConfigurationProtocols
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket
import net.minecraft.network.protocol.game.*

class BotPlayPacketListener(
    val bot: BotClient,
    val connection: Connection
) : AbstractBotPlayPacketListener() {

    override fun isAcceptingMessages(): Boolean = connection.isConnected

    override fun handleGameRuleValues(packet: ClientboundGameRuleValuesPacket) {
    }

    override fun handleLowDiskSpaceWarning(packet: ClientboundLowDiskSpaceWarningPacket) {
    }

    override fun onDisconnect(details: DisconnectionDetails) {
        bot.disconnectReason = details.reason.string
        bot.disconnect(details.reason.string)
    }

    override fun handleDisconnect(packet: ClientboundDisconnectPacket) {
        bot.disconnectReason = packet.reason.string
        bot.disconnect(packet.reason.string)
    }

    override fun handleLogin(packet: ClientboundLoginPacket) {
        bot.connectTimeMs = System.currentTimeMillis()
        bot.worldState.loginPacket = packet
        bot.worldState.spawnInfo = packet.commonPlayerSpawnInfo
        bot.connectionState = BotConnectionState.IN_GAME
        bot.worldState.initializeLevelIfNeeded(connection, bot)
    }

    override fun handlePlayerInfoUpdate(packet: ClientboundPlayerInfoUpdatePacket) {
        if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY)) {
            for (entry in packet.entries()) {
                if (entry.profileId == bot.profile.uuid) {
                    bot.ping = entry.latency
                }
            }
        }
    }

    override fun handleRespawn(packet: ClientboundRespawnPacket) {
        bot.worldState.spawnInfo = packet.commonPlayerSpawnInfo
    }

    override fun handleLevelChunkWithLight(packet: ClientboundLevelChunkWithLightPacket) {
        val key = (packet.x.toLong() and 0xFFFFFFFFL) or ((packet.z.toLong() and 0xFFFFFFFFL) shl 32)
        bot.worldState.chunks[key] = packet
        bot.worldState.botClientLevel?.loadChunk(packet)
    }

    override fun handleForgetLevelChunk(packet: ClientboundForgetLevelChunkPacket) {
        val key = (packet.pos.x.toLong() and 0xFFFFFFFFL) or ((packet.pos.z.toLong() and 0xFFFFFFFFL) shl 32)
        bot.worldState.chunks.remove(key)
    }

    override fun handleBlockUpdate(packet: ClientboundBlockUpdatePacket) {
        bot.worldState.botClientLevel?.setBlock(packet.pos, packet.blockState, 19)
    }

    override fun handleChunkBlocksUpdate(packet: ClientboundSectionBlocksUpdatePacket) {
        val level = bot.worldState.botClientLevel ?: return
        packet.runUpdates { pos, state ->
            level.setBlock(pos, state, 19)
        }
    }

    override fun handleBlockEntityData(packet: ClientboundBlockEntityDataPacket) {
        bot.worldState.blockEntities[packet.pos] = packet
    }

    override fun handleAddEntity(packet: ClientboundAddEntityPacket) {
        val profile = if (packet.type == net.minecraft.world.entity.EntityTypes.PLAYER) {
            bot.worldState.playerInfoMap[packet.uuid]?.profile
        } else null
        bot.worldState.botClientLevel?.entityTracker?.handleAddEntity(packet, profile)
    }

    override fun handleRemoveEntities(packet: ClientboundRemoveEntitiesPacket) {
        val tracker = bot.worldState.botClientLevel?.entityTracker ?: return
        for (id in packet.entityIds) {
            tracker.removeEntity(id)
        }
    }

    override fun handleMoveEntity(packet: ClientboundMoveEntityPacket) {
        val level = bot.worldState.botClientLevel ?: return
        val entity = packet.getEntity(level) ?: return
        val tracker = level.entityTracker
        val newX = entity.x + packet.xa.toDouble() / 4096.0
        val newY = entity.y + packet.ya.toDouble() / 4096.0
        val newZ = entity.z + packet.za.toDouble() / 4096.0
        val newYaw = if (packet.hasRotation()) packet.yRot * 360.0f / 256.0f else entity.yRot
        val newPitch = if (packet.hasRotation()) packet.xRot * 360.0f / 256.0f else entity.xRot
        tracker.handleMoveEntity(entity.id, newX, newY, newZ, newYaw, newPitch, packet.isOnGround)
    }

    override fun handleTeleportEntity(packet: ClientboundTeleportEntityPacket) {
        val tracker = bot.worldState.botClientLevel?.entityTracker ?: return
        val change = packet.change()
        val pos = change.position()
        tracker.handleTeleportEntity(packet.id(), pos.x, pos.y, pos.z, change.yRot(), change.xRot())
    }

    override fun handleRotateMob(packet: ClientboundRotateHeadPacket) {
        val level = bot.worldState.botClientLevel ?: return
        val entity = packet.getEntity(level) ?: return
        level.entityTracker.handleRotateHead(entity.id, packet.yHeadRot)
    }

    override fun handleMovePlayer(packet: ClientboundPlayerPositionPacket) {
        bot.playerState.applyPositionMoveRotation(packet.change, packet.id)
        bot.worldState.botLocalPlayer?.syncFrom(bot.playerState)
        connection.send(ServerboundAcceptTeleportationPacket(packet.id))
    }

    override fun handleRotatePlayer(packet: ClientboundPlayerRotationPacket) {
        bot.playerState.setRotation(packet.yRot, packet.xRot)
    }

    override fun handleSetEntityData(packet: ClientboundSetEntityDataPacket) {
        if (packet.id == bot.playerState.entityId) {
            bot.playerState.updateEntityData(packet.packedItems())
            bot.worldState.botLocalPlayer?.applyMetadata(packet.packedItems())
        } else {
            bot.worldState.botClientLevel?.entityTracker?.handleSetEntityData(packet)
        }
    }

    override fun handleSetEntityPassengersPacket(packet: ClientboundSetPassengersPacket) {
        bot.worldState.passengersMap[packet.vehicle] = packet
        bot.worldState.botClientLevel?.entityTracker?.handleSetPassengers(packet)
    }

    override fun handleUpdateAttributes(packet: ClientboundUpdateAttributesPacket) {
        bot.worldState.updateAttributes(packet.entityId, packet)
    }

    override fun handleUpdateMobEffect(packet: ClientboundUpdateMobEffectPacket) {
        bot.worldState.updateMobEffect(packet.entityId, packet)
    }

    override fun handleRemoveMobEffect(packet: ClientboundRemoveMobEffectPacket) {
        packet.effect?.let { bot.worldState.removeMobEffect(packet.entityId, it) }
    }

    override fun handleContainerSetSlot(packet: ClientboundContainerSetSlotPacket) {
        bot.playerState.updateContainerSlot(packet)
        bot.containerController.handleContainerSetSlot(packet)
    }

    override fun handleContainerContent(packet: ClientboundContainerSetContentPacket) {
        bot.playerState.updateContainerContent(packet)
        bot.containerController.handleContainerSetContent(packet)
    }

    override fun handleSetCursorItem(packet: ClientboundSetCursorItemPacket) {
        bot.playerState.cursorItemPacket = packet
    }

    override fun handleSetHeldSlot(packet: ClientboundSetHeldSlotPacket) {
        bot.playerState.updateHeldSlot(packet)
    }

    override fun handleSetHealth(packet: ClientboundSetHealthPacket) {
        bot.playerState.updateHealth(packet)
    }

    override fun handleSetExperience(packet: ClientboundSetExperiencePacket) {
        bot.playerState.updateExperience(packet)
    }

    override fun handlePlayerAbilities(packet: ClientboundPlayerAbilitiesPacket) {
        bot.playerState.updateAbilities(packet)
    }

    override fun handleKeepAlive(packet: ClientboundKeepAlivePacket) {
        connection.send(ServerboundKeepAlivePacket(packet.id))
        bot.lastKeepAliveId = packet.id
    }

    override fun handlePing(packet: ClientboundPingPacket) {
        connection.send(ServerboundPongPacket(packet.id))
    }

    override fun handleRequestCookie(packet: ClientboundCookieRequestPacket) {
        connection.send(ServerboundCookieResponsePacket(packet.key, bot.getCookie(packet.key)))
    }

    override fun handleStoreCookie(packet: ClientboundStoreCookiePacket) {
        bot.setCookie(packet.key, packet.payload)
    }

    override fun handleBossUpdate(packet: ClientboundBossEventPacket) {
        packet.dispatch(bot.worldState.bossEventHandler)
    }

    override fun handleTabListCustomisation(packet: ClientboundTabListPacket) {
        bot.worldState.tabListPacket = packet
    }

    companion object {
        private val COMMAND_NODE_BUILDER = object : ClientboundCommandsPacket.NodeBuilder<net.minecraft.commands.SharedSuggestionProvider> {
            override fun createLiteral(name: String): com.mojang.brigadier.builder.ArgumentBuilder<net.minecraft.commands.SharedSuggestionProvider, *> {
                return com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(name)
            }

            @Suppress("UNCHECKED_CAST")
            override fun createArgument(
                name: String,
                type: com.mojang.brigadier.arguments.ArgumentType<*>,
                id: net.minecraft.resources.Identifier?
            ): com.mojang.brigadier.builder.ArgumentBuilder<net.minecraft.commands.SharedSuggestionProvider, *> {
                val builder = com.mojang.brigadier.builder.RequiredArgumentBuilder.argument<net.minecraft.commands.SharedSuggestionProvider, Any?>(
                    name,
                    type as com.mojang.brigadier.arguments.ArgumentType<Any?>
                )
                if (id != null) {
                    builder.suggests(net.minecraft.commands.synchronization.SuggestionProviders.getProvider(id))
                }
                return builder
            }

            override fun configure(
                builder: com.mojang.brigadier.builder.ArgumentBuilder<net.minecraft.commands.SharedSuggestionProvider, *>,
                executable: Boolean,
                restricted: Boolean
            ): com.mojang.brigadier.builder.ArgumentBuilder<net.minecraft.commands.SharedSuggestionProvider, *> {
                if (executable) {
                    builder.executes { 0 }
                }
                return builder
            }
        }
    }

    override fun handleCommands(packet: ClientboundCommandsPacket) {
        val registry = bot.worldState.frozenRegistry ?: return
        bot.commandDispatcher = packet.getRoot(
            net.minecraft.commands.CommandBuildContext.simple(registry, bot.worldState.enabledFeatures),
            COMMAND_NODE_BUILDER
        )
    }

    override fun handleSystemChat(packet: ClientboundSystemChatPacket) {
        bot.onChatMessageReceived(packet.content().string)
    }

    override fun handlePlayerChat(packet: ClientboundPlayerChatPacket) {
        val content = packet.unsignedContent()?.string ?: packet.body().content()
        bot.onChatMessageReceived(content)
    }

    override fun handleOpenScreen(packet: ClientboundOpenScreenPacket) {
        bot.containerController.handleOpenScreen(packet)
    }

    override fun handleContainerClose(packet: ClientboundContainerClosePacket) {
        bot.containerController.handleContainerClose(packet.containerId)
    }

    override fun handleConfigurationStart(packet: ClientboundStartConfigurationPacket) {
        bot.connectionState = BotConnectionState.CONFIGURING
        connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, BotConfigurationPacketListener(bot, connection))
        connection.send(ServerboundConfigurationAcknowledgedPacket.INSTANCE)
        connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND)
    }
}
