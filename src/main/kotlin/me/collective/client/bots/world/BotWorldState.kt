package me.collective.client.bots.world

import com.mojang.authlib.GameProfile
import me.collective.client.bots.BotClient
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.CommonListenerCookie
import net.minecraft.client.multiplayer.LevelLoadTracker
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.telemetry.TelemetryEventSender
import net.minecraft.client.telemetry.WorldSessionTelemetryManager
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess.Frozen
import net.minecraft.network.Connection
import net.minecraft.network.PacketProcessor
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.protocol.game.ClientboundBossEventPacket.Handler
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket.AttributeSnapshot
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.ServerLinks
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload
import net.minecraft.world.BossEvent.BossBarColor
import net.minecraft.world.BossEvent.BossBarOverlay
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.scores.Scoreboard
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArraySet

class BotWorldState {
    val chunks: MutableMap<Long, ClientboundLevelChunkWithLightPacket> = ConcurrentHashMap()
    val chunkQueue: Deque<ClientboundLevelChunkWithLightPacket> = ConcurrentLinkedDeque()
    val playerInfoMap: MutableMap<UUID, PlayerInfo> = ConcurrentHashMap()
    val listedPlayers: MutableSet<UUID> = CopyOnWriteArraySet()
    val scoreboard: Scoreboard = Scoreboard()
    val bossEvents: MutableMap<UUID, BossEventEntry> = ConcurrentHashMap()

    val activeEffects: MutableMap<Int, MutableMap<Holder<MobEffect>, ClientboundUpdateMobEffectPacket>> = ConcurrentHashMap()
    val attributes: MutableMap<Int, MutableMap<Holder<Attribute>, AttributeSnapshot>> = ConcurrentHashMap()
    val passengersMap: MutableMap<Int, ClientboundSetPassengersPacket> = ConcurrentHashMap()
    val entityLinks: MutableMap<Int, ClientboundSetEntityLinkPacket> = ConcurrentHashMap()
    val blockEntities: MutableMap<BlockPos, ClientboundBlockEntityDataPacket> = ConcurrentHashMap()
    val cooldowns: MutableMap<Identifier, ClientboundCooldownPacket> = ConcurrentHashMap()

    @Volatile var tabListPacket: ClientboundTabListPacket? = null
    @Volatile var recipesPacket: ClientboundUpdateRecipesPacket? = null
    @Volatile var initializeBorderPacket: ClientboundInitializeBorderPacket? = null
    @Volatile var borderCenterPacket: ClientboundSetBorderCenterPacket? = null
    @Volatile var borderSizePacket: ClientboundSetBorderSizePacket? = null
    @Volatile var borderLerpSizePacket: ClientboundSetBorderLerpSizePacket? = null
    @Volatile var borderWarningDelayPacket: ClientboundSetBorderWarningDelayPacket? = null
    @Volatile var borderWarningDistancePacket: ClientboundSetBorderWarningDistancePacket? = null
    @Volatile var serverDataPacket: ClientboundServerDataPacket? = null
    @Volatile var tickingStatePacket: ClientboundTickingStatePacket? = null
    @Volatile var loginPacket: ClientboundLoginPacket? = null
    @Volatile var spawnInfo: CommonPlayerSpawnInfo? = null
    @Volatile var frozenRegistry: Frozen? = null
    @Volatile var tags: Map<ResourceKey<out Registry<*>>, NetworkPayload> = emptyMap()
    @Volatile var enabledFeatures: FeatureFlagSet = FeatureFlags.DEFAULT_FLAGS
    @Volatile var serverBrand: String = ""

    @Volatile var botClientLevel: BotClientLevel? = null
    @Volatile var botLocalPlayer: BotLocalPlayer? = null
    @Volatile var clientPacketListener: ClientPacketListener? = null
    @Volatile var ownerBot: BotClient? = null

    val bossEventHandler: Handler = object : Handler {
        override fun add(uuid: UUID, name: Component, progress: Float, color: BossBarColor, overlay: BossBarOverlay, darkenScreen: Boolean, playBossMusic: Boolean, createWorldFog: Boolean) {
            bossEvents[uuid] = BossEventEntry(uuid, name, progress, color, overlay, darkenScreen, playBossMusic, createWorldFog)
        }

        override fun remove(uuid: UUID) {
            bossEvents.remove(uuid)
        }

        override fun updateProgress(uuid: UUID, progress: Float) {
            bossEvents[uuid]?.progress = progress
        }

        override fun updateName(uuid: UUID, name: Component) {
            bossEvents[uuid]?.name = name
        }

        override fun updateStyle(uuid: UUID, color: BossBarColor, overlay: BossBarOverlay) {
            val entry = bossEvents[uuid] ?: return
            entry.color = color
            entry.overlay = overlay
        }

        override fun updateProperties(uuid: UUID, darkenScreen: Boolean, playBossMusic: Boolean, createWorldFog: Boolean) {
            val entry = bossEvents[uuid] ?: return
            entry.darkenScreen = darkenScreen
            entry.playBossMusic = playBossMusic
            entry.createWorldFog = createWorldFog
        }
    }

    data class BossEventEntry(
        val id: UUID,
        var name: Component,
        var progress: Float,
        var color: BossBarColor,
        var overlay: BossBarOverlay,
        var darkenScreen: Boolean,
        var playBossMusic: Boolean,
        var createWorldFog: Boolean
    )

    fun reset() {
        chunks.clear()
        chunkQueue.clear()
        playerInfoMap.clear()
        listedPlayers.clear()
        bossEvents.clear()
        activeEffects.clear()
        attributes.clear()
        passengersMap.clear()
        entityLinks.clear()
        blockEntities.clear()
        cooldowns.clear()
        tabListPacket = null
        recipesPacket = null
        initializeBorderPacket = null
        borderCenterPacket = null
        borderSizePacket = null
        borderLerpSizePacket = null
        borderWarningDelayPacket = null
        borderWarningDistancePacket = null
        serverDataPacket = null
        tickingStatePacket = null
        loginPacket = null
        spawnInfo = null
        frozenRegistry = null
        tags = emptyMap()
        enabledFeatures = FeatureFlags.DEFAULT_FLAGS
        serverBrand = ""
        botClientLevel = null
        botLocalPlayer = null
        clientPacketListener = null
    }

    fun getEffects(entityId: Int): List<ClientboundUpdateMobEffectPacket>? {
        val map = activeEffects[entityId] ?: return null
        return ArrayList(map.values)
    }

    fun getAttributes(entityId: Int): List<AttributeSnapshot>? {
        val map = attributes[entityId] ?: return null
        return ArrayList(map.values)
    }

    fun updateMobEffect(entityId: Int, packet: ClientboundUpdateMobEffectPacket) {
        activeEffects.computeIfAbsent(entityId) { ConcurrentHashMap() }[packet.effect] = packet
    }

    fun removeMobEffect(entityId: Int, effect: Holder<MobEffect>) {
        activeEffects[entityId]?.remove(effect)
    }

    fun updateAttributes(entityId: Int, packet: ClientboundUpdateAttributesPacket) {
        val map = attributes.computeIfAbsent(entityId) { ConcurrentHashMap() }
        for (snapshot in packet.values) {
            map[snapshot.attribute] = snapshot
        }
    }

    fun initializeLevelIfNeeded(connection: Connection, bot: BotClient) {
        val login = loginPacket ?: return
        val registry = frozenRegistry ?: return
        if (botClientLevel != null) return

        try {
            val mc = Minecraft.getInstance()
            val telemetry = WorldSessionTelemetryManager(
                TelemetryEventSender.DISABLED, false, null, null, java.util.UUID.randomUUID()
            )
            val profile = bot.profile.toGameProfile()
            val cookie = CommonListenerCookie(
                LevelLoadTracker(),
                profile,
                telemetry,
                registry,
                enabledFeatures,
                serverBrand,
                null,
                null,
                HashMap(),
                null,
                emptyMap(),
                ServerLinks.EMPTY,
                emptyMap(),
                false
            )
            val listener = ClientPacketListener(mc, connection, cookie)
            clientPacketListener = listener

            val spawn = spawnInfo ?: login.commonPlayerSpawnInfo()
            val level = BotClientLevel(listener, login, spawn, login.chunkRadius(), login.simulationDistance(), scoreboard)
            botClientLevel = level

            val localPlayer = BotLocalPlayer(mc, level, listener)
            botLocalPlayer = localPlayer
            level.entityTracker.addEntity(localPlayer)

            initializeBorderPacket?.let { level.applyInitializeBorder(it) }
            borderCenterPacket?.let { level.applyBorderCenter(it) }
            borderSizePacket?.let { level.applyBorderSize(it) }
            borderLerpSizePacket?.let { level.applyBorderLerp(it) }
            borderWarningDelayPacket?.let { level.applyBorderWarningDelay(it) }
            borderWarningDistancePacket?.let { level.applyBorderWarningDistance(it) }

            // Replay buffered chunks
            for (chunkPacket in chunks.values) {
                level.loadChunk(chunkPacket)
            }
        } catch (t: Throwable) {
            // Ignore initialization error
        }
    }

    companion object {
        @JvmStatic
        fun <T : Packet<ClientGamePacketListener>> dispatchPacket(
            processor: PacketProcessor,
            listener: ClientGamePacketListener,
            packet: T
        ) {
            try {
                processor.scheduleIfPossible(listener, packet)
            } catch (t: Throwable) {
                // Ignore schedule failure
            }
        }
    }
}
