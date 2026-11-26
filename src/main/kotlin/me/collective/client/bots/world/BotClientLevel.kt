package me.collective.client.bots.world

import me.collective.client.ClientAccess
import me.collective.mixin.ClientLevelAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.SectionPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.protocol.game.*
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.DataLayer
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.scores.Scoreboard
import java.util.*
import java.util.function.Predicate

class BotClientLevel(
    val packetListener: ClientPacketListener,
    loginPacket: ClientboundLoginPacket,
    spawnInfo: CommonPlayerSpawnInfo,
    viewDistance: Int,
    simulationDistance: Int,
    private val botScoreboard: Scoreboard
) : ClientLevel(
    packetListener,
    ClientLevelData(Difficulty.NORMAL, loginPacket.hardcore(), spawnInfo.isFlat),
    spawnInfo.dimension(),
    spawnInfo.dimensionType(),
    viewDistance,
    simulationDistance,
    net.minecraft.client.renderer.extract.LevelExtractor(
        Minecraft.getInstance(),
        Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState,
        Minecraft.getInstance().levelRenderer
    ),
    spawnInfo.isDebug,
    spawnInfo.seed(),
    spawnInfo.seaLevel()
) {
    val entityTracker: BotEntityTracker = BotEntityTracker(this)

    override fun getScoreboard(): Scoreboard = botScoreboard

    override fun players(): List<AbstractClientPlayer> = entityTracker.getPlayers()

    override fun getEntities(entity: Entity?, box: AABB, predicate: Predicate<in Entity>): List<Entity> {
        return entityTracker.getEntities(entity, box, predicate)
    }

    override fun getEntityCollisions(entity: Entity?, box: AABB): List<VoxelShape> {
        return entityTracker.getEntityCollisions(entity, box)
    }

    override fun getPushableEntities(entity: Entity, box: AABB): List<Entity> {
        return entityTracker.getPushableEntities(entity, box)
    }

    fun tickBookkeeping() {
        val source = chunkSource
        try {
            source.lightEngine.runLightUpdates()
        } catch (ignored: Throwable) {}
        worldBorder.tick()
        entityTracker.tick()
    }

    fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean {
        return chunkSource.hasChunk(SectionPos.blockToSectionCoord(chunkX), SectionPos.blockToSectionCoord(chunkZ))
    }

    fun loadChunk(packet: ClientboundLevelChunkWithLightPacket) {
        val x = packet.x
        val z = packet.z
        val data = packet.chunkData
        chunkSource.replaceWithPacketData(x, z, data.readBuffer, data.heightmaps, data.getBlockEntitiesTagsConsumer(x, z))
        applyLight(x, z, packet.lightData)
        val chunk = chunkSource.getChunk(x, z, false)
        if (chunk != null) {
            enableChunkLight(chunk)
        }
    }

    private fun enableChunkLight(chunk: LevelChunk) {
        try {
            chunkSource.lightEngine.setLightEnabled(chunk.pos, true)
            chunkSource.lightEngine.propagateLightSources(chunk.pos)
        } catch (ignored: Throwable) {}
    }

    fun applyLight(chunkX: Int, chunkZ: Int, data: ClientboundLightUpdatePacketData) {
        try {
            val lightEngine = chunkSource.lightEngine
            readSectionList(chunkX, chunkZ, lightEngine, LightLayer.BLOCK, data.blockYMask, data.emptyBlockYMask, data.blockUpdates.iterator())
            readSectionList(chunkX, chunkZ, lightEngine, LightLayer.SKY, data.skyYMask, data.emptySkyYMask, data.skyUpdates.iterator())
        } catch (ignored: Throwable) {}
    }

    private fun readSectionList(
        chunkX: Int,
        chunkZ: Int,
        lightEngine: LevelLightEngine,
        layer: LightLayer,
        mask: BitSet,
        emptyMask: BitSet,
        updates: Iterator<ByteArray>
    ) {
        for (i in 0 until lightEngine.lightSectionCount) {
            val hasData = mask.get(i)
            if (hasData || emptyMask.get(i)) {
                val sectionY = lightEngine.minLightSection + i
                val pos = SectionPos.of(chunkX, sectionY, chunkZ)
                val dataLayer = if (hasData) DataLayer(updates.next().clone()) else DataLayer()
                lightEngine.queueSectionData(layer, pos, dataLayer)
            }
        }
    }

    fun applyBorderCenter(packet: ClientboundSetBorderCenterPacket) {
        worldBorder.setCenter(packet.newCenterX, packet.newCenterZ)
    }

    fun applyBorderSize(packet: ClientboundSetBorderSizePacket) {
        worldBorder.size = packet.size
    }

    fun applyBorderLerp(packet: ClientboundSetBorderLerpSizePacket) {
        worldBorder.lerpSizeBetween(packet.oldSize, packet.newSize, packet.lerpTime, gameTime)
    }

    fun applyBorderWarningDelay(packet: ClientboundSetBorderWarningDelayPacket) {
        worldBorder.warningTime = packet.warningDelay
    }

    fun applyBorderWarningDistance(packet: ClientboundSetBorderWarningDistancePacket) {
        worldBorder.warningBlocks = packet.warningBlocks
    }

    fun applyInitializeBorder(packet: ClientboundInitializeBorderPacket) {
        val border = worldBorder
        border.setCenter(packet.newCenterX, packet.newCenterZ)
        val lerpTime = packet.lerpTime
        if (lerpTime > 0L) {
            border.lerpSizeBetween(packet.oldSize, packet.newSize, lerpTime, gameTime)
        } else {
            border.size = packet.newSize
        }
        border.absoluteMaxSize = packet.newAbsoluteMaxSize
        border.warningBlocks = packet.warningBlocks
        border.warningTime = packet.warningTime
    }

    override fun syncBlockState(pos: BlockPos, state: BlockState, center: Vec3?) {
        val current = getBlockState(pos)
        if (current != state) {
            setBlock(pos, state, 19)
            val player = entityTracker.getLocalPlayer()
            if (player != null && center != null && player.isColliding(pos, state)) {
                player.absSnapTo(center.x, center.y, center.z)
            }
        }
    }

    fun predictBlockRemoval(pos: BlockPos, player: BotLocalPlayer, sequence: Int): Int {
        val state = getBlockState(pos)
        val accessor = this as? ClientLevelAccessor
        val handler = accessor?.pendingUpdateManager ?: return sequence

        val predictedSeq = handler.currentSequence()
        state.block.playerWillDestroy(this, pos, state, player)
        setBlock(pos, state.fluidState.createLegacyBlock(), 11)
        return predictedSeq
    }

    // Suppress audio & particle overhead for headless bots
    override fun playSeededSound(entity: Entity?, entity2: Entity, holder: Holder<SoundEvent>, soundSource: SoundSource, f: Float, g: Float, l: Long) {}
    override fun playSeededSound(entity: Entity?, d: Double, e: Double, f: Double, holder: Holder<SoundEvent>, soundSource: SoundSource, g: Float, h: Float, l: Long) {}
    override fun playLocalSound(entity: Entity, soundEvent: SoundEvent, soundSource: SoundSource, f: Float, g: Float) {}
    override fun playPlayerSound(soundEvent: SoundEvent, soundSource: SoundSource, f: Float, g: Float) {}
    override fun playLocalSound(x: Double, y: Double, z: Double, soundEvent: SoundEvent, soundSource: SoundSource, f: Float, g: Float, bl: Boolean) {}
    override fun addParticle(particleOptions: ParticleOptions, x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double) {}
    override fun addParticle(particleOptions: ParticleOptions, bl: Boolean, bl2: Boolean, x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double) {}
    override fun addAlwaysVisibleParticle(particleOptions: ParticleOptions, x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double) {}
    override fun addAlwaysVisibleParticle(particleOptions: ParticleOptions, bl: Boolean, x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double) {}
    override fun createFireworks(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double, explosions: List<FireworkExplosion>) {}
}
