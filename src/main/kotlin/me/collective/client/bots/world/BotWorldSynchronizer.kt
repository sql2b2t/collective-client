package me.collective.client.bots.world

import me.collective.client.ClientAccess
import me.collective.mixin.PlayerTabOverlayAccessor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.*
import net.minecraft.world.level.chunk.status.ChunkStatus

object BotWorldSynchronizer : ClientAccess {
    private const val DEFAULT_SYNC_RADIUS: Int = 8

    fun captureWorld(targetState: BotWorldState, level: ClientLevel, player: LocalPlayer) {
        captureChunks(targetState, level, player.chunkPosition().x, player.chunkPosition().z, DEFAULT_SYNC_RADIUS)
        captureBorder(targetState, level)
        captureTabList(targetState)
    }

    private fun captureChunks(targetState: BotWorldState, level: ClientLevel, centerX: Int, centerZ: Int, radius: Int) {
        val chunkSource = level.chunkSource
        val lightEngine = chunkSource.lightEngine

        for (cx in centerX - radius..centerX + radius) {
            for (cz in centerZ - radius..centerZ + radius) {
                val chunk = chunkSource.getChunk(cx, cz, ChunkStatus.FULL, false)
                if (chunk != null) {
                    val packet = ClientboundLevelChunkWithLightPacket(chunk, lightEngine, null, null)
                    val key = (cx.toLong() and 0xFFFFFFFFL) or ((cz.toLong() and 0xFFFFFFFFL) shl 32)
                    targetState.chunks[key] = packet
                }
            }
        }
    }

    private fun captureBorder(targetState: BotWorldState, level: ClientLevel) {
        val border = level.worldBorder
        targetState.borderCenterPacket = ClientboundSetBorderCenterPacket(border)
        targetState.borderSizePacket = ClientboundSetBorderSizePacket(border)
        targetState.borderWarningDelayPacket = ClientboundSetBorderWarningDelayPacket(border)
        targetState.borderWarningDistancePacket = ClientboundSetBorderWarningDistancePacket(border)
    }

    private fun captureTabList(targetState: BotWorldState) {
        val tabList = ClientAccess.collectiveMinecraft.gui.tabList
        val accessor = tabList as? PlayerTabOverlayAccessor
        val header = accessor?.header ?: Component.empty()
        val footer = accessor?.footer ?: Component.empty()
        targetState.tabListPacket = ClientboundTabListPacket(header, footer)
    }
}
