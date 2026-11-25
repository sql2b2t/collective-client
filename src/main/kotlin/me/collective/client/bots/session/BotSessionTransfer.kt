package me.collective.client.bots.session

import me.collective.client.ClientAccess
import me.collective.client.access.BotConnectionAccess
import me.collective.client.bots.BotClient
import me.collective.client.bots.BotConnectionState
import me.collective.client.bots.world.BotPlayerSynchronizer
import me.collective.client.bots.world.BotWorldState
import me.collective.client.bots.world.BotWorldSynchronizer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.CommonListenerCookie
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.server.ServerLinks
import java.util.*

object BotSessionTransfer : ClientAccess {

    fun canTransfer(bot: BotClient?): Boolean {
        if (bot == null) return false
        return bot.connectionState.isInGame && bot.connection?.isConnected == true
    }

    fun transferToClient(bot: BotClient): Boolean {
        if (!canTransfer(bot)) return false
        val conn = bot.connection ?: return false
        val mc = ClientAccess.collectiveMinecraft

        try {
            val serverData = ServerData(bot.profile.username, bot.profile.serverAddress, ServerData.Type.OTHER)
            val cookie = CommonListenerCookie(
                bot.profile.toGameProfile(),
                mc.telemetryManager.createWorldSessionManager(false, null, null),
                bot.worldState.frozenRegistry ?: return false,
                bot.worldState.enabledFeatures,
                bot.worldState.serverBrand,
                serverData,
                null,
                HashMap(),
                null,
                emptyMap(),
                ServerLinks.EMPTY
            )

            (conn as? BotConnectionAccess)?.isBotConnection = false
            bot.connectionState = BotConnectionState.CONTROLLED
            BotSessionManager.controlledBot = bot

            val listener = ClientPacketListener(mc, conn, cookie)
            // Replay state
            BotWorldState.dispatchPacket(mc, listener, bot.worldState.loginPacket ?: return false)
            bot.playerState.replayPackets(mc, listener)
            return true
        } catch (t: Throwable) {
            return false
        }
    }

    fun transferBackToBot(bot: BotClient): Boolean {
        val mc = ClientAccess.collectiveMinecraft
        val player = mc.player ?: return false
        val level = mc.level ?: return false

        try {
            BotWorldSynchronizer.captureWorld(bot.worldState, level, player)
            bot.playerState.syncFromLocalPlayer(player, mc.gameMode?.playerMode ?: net.minecraft.world.level.GameType.SURVIVAL)
            bot.connectionState = BotConnectionState.IN_GAME
            BotSessionManager.controlledBot = null
            return true
        } catch (t: Throwable) {
            return false
        }
    }
}
