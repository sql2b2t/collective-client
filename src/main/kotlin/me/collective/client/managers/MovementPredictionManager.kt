package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.events.ClientLevelRemoveEntityEvent
import me.collective.client.events.EventHandler
import me.collective.client.events.LocalPlayerTickEvent
import me.collective.client.events.MinecraftDisconnectEvent
import me.collective.client.events.MinecraftRunTickEvent
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.movement.PlayerMotionPredictor
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Collections

class MovementPredictionManager : ClientAccess {
    private val playerPredictions: MutableMap<Int, PlayerMotionPredictor> = Collections.synchronizedMap(HashMap())
    private val predictionTimer = ElapsedTimer()
    private var previousPlayerPosition: Vec3? = null

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    val adjustedLocalMotion: Vec3
        @JvmName("getAdjustedLocalMotionProp")
        get() = getAdjustedLocalMotion()

    fun getAdjustedLocalMotion(): Vec3 {
        val prevPos = previousPlayerPosition ?: return Vec3.ZERO
        val player = ClientAccess.collectiveMinecraft.player ?: return Vec3.ZERO
        var motion = player.position().subtract(prevPos)
        if (player.isSprinting && !player.isFallFlying) {
            motion = motion.multiply(0.9f.toDouble(), 1.0, 0.9f.toDouble())
        }
        return motion
    }

    val predictedEyePosition: Vec3
        @JvmName("getPredictedEyePositionProp")
        get() = getPredictedEyePosition()

    fun getPredictedEyePosition(): Vec3 {
        val player = ClientAccess.collectiveMinecraft.player ?: return Vec3.ZERO
        return player.eyePosition.add(getAdjustedLocalMotion())
    }

    @EventHandler
    private fun onLocalPlayerTickEvent(event: LocalPlayerTickEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        previousPlayerPosition = Vec3(player.xo, player.yo, player.zo)
    }

    @EventHandler
    private fun onMinecraftRunTickEvent(event: MinecraftRunTickEvent) {
        if (isWorldUnavailable() || !predictionTimer.hasElapsedAndReset(25L)) {
            return
        }
        val level = ClientAccess.collectiveMinecraft.level ?: return
        for (player in level.players()) {
            playerPredictions.computeIfAbsent(player.id) { PlayerMotionPredictor(player) }
                .updatePredictions()
        }
    }

    @EventHandler
    private fun onMinecraftDisconnectEvent(event: MinecraftDisconnectEvent) {
        playerPredictions.clear()
    }

    @Synchronized
    fun getPredictedBoundingBox(player: Player, tickIndex: Int): AABB {
        val prediction = playerPredictions[player.id] ?: return player.boundingBox
        val positions = prediction.getPredictedBoxes()
        return if (positions.isEmpty()) {
            player.boundingBox
        } else {
            positions[tickIndex.coerceIn(0, positions.size - 1)]
        }
    }

    @EventHandler
    private fun onClientLevelRemoveEntityEvent(event: ClientLevelRemoveEntityEvent) {
        synchronized(playerPredictions) {
            playerPredictions.remove(event.id)
        }
    }
}
