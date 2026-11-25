package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.ClientLevelRemoveEntityEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.PacketEvent
import me.collective.client.modules.ModuleCategory
import me.collective.client.modules.PlacementModule
import me.collective.client.settings.BooleanSetting
import me.collective.client.settings.NumberSetting
import me.collective.client.settings.Setting
import me.collective.client.utils.combat.BlockRaycastContext
import me.collective.client.utils.combat.RaycastUtils
import me.collective.client.utils.math.MathUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.projectile.ThrownEnderpearl
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

class AntiPearlModule : PlacementModule("AntiPearl", "Stops ender pearls mid-air.", ModuleCategory.COMBAT, "pearlblocker") {
    protected val rangeSetting: Setting<Float>
    protected val predictSetting: Setting<Float>
    protected val airborneSetting: Setting<Boolean>
    protected val tryCrawlSetting: Setting<Boolean>
    private val placementExpiryByPosition: MutableMap<BlockPos, Long>
    private val nearbyPlayerByPearlId: MutableMap<Int, AbstractClientPlayer>
    private val pendingPlacementByPearlId: MutableMap<Int, PendingPlacement>

    init {
        this.rangeSetting = this.add(NumberSetting("Range", 5.0f, 1.0f, 6.0f).withUnit("m"))
        this.predictSetting = this.add(NumberSetting("Predict", 3.0f, 2.0f, 4.0f))
        this.airborneSetting = this.add(BooleanSetting("Airborne", false))
        this.tryCrawlSetting = this.add(BooleanSetting("TryCrawl", false))
        this.placementExpiryByPosition = HashMap()
        this.nearbyPlayerByPearlId = HashMap()
        this.pendingPlacementByPearlId = HashMap()

        this.placementAttackSetting.value = false
        this.placementJumpSetting.setDefaultValue(false)
        this.ignoreMissingPlacementBlocks()
    }

    private fun prependSupportPositions(positions: MutableList<BlockPos>) {
        var supportPositions: List<BlockPos>? = null
        for (position in positions) {
            val directionCheck = BlockInteractionUtils.isStrictDirectionEnabled()
            val candidateSupports = BlockInteractionUtils.findSupportPath(position, 2, true, directionCheck)
            if (candidateSupports.isNotEmpty()) {
                supportPositions = candidateSupports
                break
            }
        }

        if (supportPositions != null) {
            positions.addAll(0, supportPositions)
        }
    }

    override fun onToggle() {
        this.nearbyPlayerByPearlId.clear()
        this.pendingPlacementByPearlId.clear()
    }

    private fun refreshAirbornePearls() {
        val level = ClientAccess.collectiveMinecraft.level ?: return
        for (entity in level.entitiesForRendering()) {
            if (entity is ThrownEnderpearl) {
                val positionSnapshot = entity.position()
                val movementSnapshot = MathUtils.getPositionDelta(entity)
                val pearlId = entity.id
                this.predictBlockingPositions(positionSnapshot, movementSnapshot, pearlId)
            }
        }
    }

    private fun processPearlSpawn(pearlId: Int, position: Vec3, movement: Vec3) {
        val verticalMotion = movement.y
        if (verticalMotion >= -1.4) {
            this.associateNearbyPlayer(position, pearlId)
            this.predictBlockingPositions(position, movement, pearlId)
        }
    }

    private fun hasPlacementDirection(positions: List<BlockPos>): Boolean {
        val directionCheck = BlockInteractionUtils.isStrictDirectionEnabled()
        for (position in positions) {
            if (BlockInteractionUtils.findSupportDirection(position, directionCheck) != null) {
                return true
            }
        }
        return false
    }

    private fun associateNearbyPlayer(pearlPosition: Vec3, pearlId: Int) {
        var nearestDistance = 999.0
        var nearestPlayer: AbstractClientPlayer? = null

        val level = ClientAccess.collectiveMinecraft.level ?: return
        for (player in level.players()) {
            val distance = player.eyePosition.distanceTo(pearlPosition)
            if (distance <= 3.0 && distance < nearestDistance) {
                nearestDistance = distance
                nearestPlayer = player
            }
        }

        if (nearestPlayer != null && nearestPlayer != ClientAccess.collectiveMinecraft.player) {
            if (!ClientContext.socialRegistry.isFriendPlayer(nearestPlayer)) {
                this.nearbyPlayerByPearlId[pearlId] = nearestPlayer
            }
        }
    }

    private fun predictBlockingPositions(position: Vec3, movement: Vec3, pearlId: Int) {
        if (!this.nearbyPlayerByPearlId.containsKey(pearlId)) {
            return
        }
        if (this.pendingPlacementByPearlId.containsKey(pearlId)) {
            return
        }

        val predictionScale = this.predictSetting.value.toDouble()
        val scaledMovement = movement.scale(predictionScale)
        var predictedPosition = position.add(scaledMovement)

        val contextBuilderSnapshot = BlockRaycastContext.Builder(position, predictedPosition)
        val hitResult = RaycastUtils.raycast(contextBuilderSnapshot.withBlockMode(ClipContext.Block.COLLIDER).build())
        if (hitResult.type != HitResult.Type.MISS) {
            if (!this.tryCrawlSetting.value) {
                return
            }
            predictedPosition = hitResult.location
        }

        val predictionBounds = AABB.ofSize(predictedPosition, 0.1, 0.1, 0.1)
        val blockingPositions = BlockPos.betweenClosedStream(predictionBounds)
            .map { it.immutable() }
            .toList()
            .toMutableList()

        if (!this.hasPlacementDirection(blockingPositions)) {
            if (!this.placementAirPlaceSetting.value) {
                this.prependSupportPositions(blockingPositions)
            }
        }

        if (this.tryCrawlSetting.value) {
            this.raiseCrawlPositions(blockingPositions)
        }

        this.pendingPlacementByPearlId[pearlId] = PendingPlacement(
            blockingPositions,
            System.currentTimeMillis() + 100L
        )
    }

    private fun raiseCrawlPositions(positions: MutableList<BlockPos>) {
        val lowerPositions = ArrayList<BlockPos>()
        val raisedPositions = HashSet<BlockPos>()
        for (position in positions) {
            if (!WorldUtils.isReplaceable(position)) {
                continue
            }
            val below = position.below()
            if (!WorldUtils.isReplaceable(below)) {
                lowerPositions.add(position)
                raisedPositions.add(position.above())
            }
        }
        if (lowerPositions.isNotEmpty()) {
            positions.removeAll(lowerPositions)
        }
        if (raisedPositions.isNotEmpty()) {
            positions.addAll(raisedPositions)
        }
    }

    override fun onPlacementAttempt(position: BlockPos, placementFailed: Boolean) {
        if (this.tryCrawlSetting.value) {
            val expiresAt = System.currentTimeMillis() + 250L
            this.placementExpiryByPosition[position.immutable()] = expiresAt
        }
    }

    @EventHandler
    fun onClientLevelRemoveEntityEvent(event: ClientLevelRemoveEntityEvent) {
        this.nearbyPlayerByPearlId.remove(event.id)
    }

    override fun getPlacementPositions(): MutableList<BlockPos> {
        if (this.airborneSetting.value) {
            this.refreshAirbornePearls()
        }

        val positions = ArrayList<BlockPos>()

        for (pendingPlacement in this.pendingPlacementByPearlId.values) {
            positions.addAll(pendingPlacement.positions)
        }

        val now = System.currentTimeMillis()
        this.pendingPlacementByPearlId.values.removeIf { now > it.expiresAtMillis }
        this.placementExpiryByPosition.values.removeIf { System.currentTimeMillis() > it }

        val player = ClientAccess.collectiveMinecraft.player
        val eyePos = player?.eyePosition ?: Vec3.ZERO
        val range = this.rangeSetting.value.toDouble()

        positions.removeIf { position ->
            val distance = position.center.distanceTo(eyePos)
            distance > range || this.placementExpiryByPosition.containsKey(position)
        }

        return positions
    }

    @EventHandler
    fun onPearlSpawnPacket(event: PacketEvent.Receive) {
        val packet = event.packet
        if (packet is ClientboundAddEntityPacket && packet.type == EntityType.ENDER_PEARL) {
            val spawnPosition = Vec3(packet.x, packet.y, packet.z)
            ClientAccess.collectiveMinecraft.execute {
                val queuedIdSnapshot = packet.id
                val queuedMovement = Vec3(packet.xa, packet.ya, packet.za)
                this.processPearlSpawn(queuedIdSnapshot, spawnPosition, queuedMovement)
            }
        }
    }

    private data class PendingPlacement(
        val positions: List<BlockPos>,
        val expiresAtMillis: Long
    )
}
