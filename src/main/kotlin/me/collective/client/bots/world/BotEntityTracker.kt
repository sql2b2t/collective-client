package me.collective.client.bots.world

import com.mojang.authlib.GameProfile
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

class BotEntityTracker(val level: BotClientLevel) {
    companion object {
        const val MAX_ENTITIES: Int = 1024
    }

    private val entities: MutableMap<Int, Entity> = ConcurrentHashMap()
    private val playerList: MutableList<AbstractClientPlayer> = CopyOnWriteArrayList()

    fun getEntity(id: Int): Entity? = entities[id]

    fun getAllEntities(): Collection<Entity> = java.util.List.copyOf(entities.values)

    fun getPlayers(): List<AbstractClientPlayer> = java.util.List.copyOf(playerList)

    fun clear() {
        entities.clear()
        playerList.clear()
    }

    fun contains(entity: Entity?): Boolean {
        return entity != null && entities[entity.id] === entity
    }

    fun getLocalPlayer(): BotLocalPlayer? {
        for (entity in entities.values) {
            if (entity is BotLocalPlayer) {
                return entity
            }
        }
        return null
    }

    fun addEntity(entity: Entity) {
        val previous = entities.put(entity.id, entity)
        if (previous != null) {
            cleanupEntity(previous)
        }
        registerPlayerIfApplicable(entity)
    }

    fun handleAddEntity(packet: ClientboundAddEntityPacket, profile: GameProfile?) {
        if (entities.size >= MAX_ENTITIES || entities.containsKey(packet.id)) {
            return
        }

        val entity: Entity? = if (packet.type == EntityType.PLAYER) {
            if (profile != null) RemotePlayer(level, profile) else null
        } else {
            packet.type.create(level, EntitySpawnReason.LOAD)
        }

        if (entity != null) {
            entity.recreateFromPacket(packet)
            entities[entity.id] = entity
            registerPlayerIfApplicable(entity)
        }
    }

    fun removeEntity(id: Int) {
        val removed = entities.remove(id) ?: return
        for (passenger in ArrayList(removed.passengers)) {
            passenger.stopRiding()
        }
        removed.stopRiding()
        cleanupEntity(removed)
    }

    fun handleSetEntityData(packet: ClientboundSetEntityDataPacket) {
        val entity = entities[packet.id()] ?: return
        entity.entityData.assignValues(packet.packedItems())
    }

    fun handleSetPassengers(packet: ClientboundSetPassengersPacket) {
        val vehicle = entities[packet.vehicle]
        if (vehicle == null) {
            ejectPassengersFor(packet.vehicle)
        } else {
            for (passenger in ArrayList(vehicle.passengers)) {
                passenger.stopRiding()
            }
            for (passengerId in packet.passengers) {
                val passenger = entities[passengerId]
                passenger?.startRiding(vehicle, true, true)
            }
        }
    }

    fun handleMoveEntity(id: Int, x: Double, y: Double, z: Double, yaw: Float, pitch: Float, onGround: Boolean) {
        val entity = entities[id]
        if (entity != null && entity !is BotLocalPlayer) {
            val pos = Vec3(x, y, z)
            entity.positionCodec.setBase(pos)
            entity.moveOrInterpolateTo(pos, yaw, pitch)
            entity.setOnGround(onGround)
        }
    }

    fun handleTeleportEntity(id: Int, x: Double, y: Double, z: Double, yaw: Float, pitch: Float) {
        val entity = entities[id] ?: return
        entity.snapTo(x, y, z, yaw, pitch)
        entity.positionCodec.setBase(Vec3(x, y, z))
        entity.setOldPosAndRot()
    }

    fun handleRotateHead(id: Int, headYaw: Float) {
        entities[id]?.lerpHeadTo(headYaw, 3)
    }

    fun tick() {
        for (entity in java.util.List.copyOf(entities.values)) {
            if (entity !is BotLocalPlayer) {
                if (entity.isRemoved) {
                    removeEntity(entity.id)
                } else {
                    entity.setOldPosAndRot()
                    entity.tickCount++
                    entity.tick()
                }
            }
        }
    }

    fun getEntityCollisions(entity: Entity?, box: AABB): List<VoxelShape> {
        val collisions = ArrayList<VoxelShape>()
        for (candidate in entities.values) {
            if (candidate !== entity && candidate.boundingBox.intersects(box) && candidate.canBeCollidedWith(entity)) {
                collisions.add(Shapes.create(candidate.boundingBox))
            }
        }
        return collisions
    }

    fun getPushableEntities(entity: Entity, box: AABB): List<Entity> {
        return getEntities(entity, box, EntitySelector.pushableBy(entity))
    }

    fun getEntities(entity: Entity?, box: AABB, predicate: Predicate<in Entity>): List<Entity> {
        val result = ArrayList<Entity>()
        for (candidate in entities.values) {
            if (candidate !== entity && !candidate.isRemoved && candidate.boundingBox.intersects(box) && predicate.test(candidate)) {
                result.add(candidate)
            }
        }
        return result
    }

    fun <T : Entity> getEntities(
        typeTest: EntityTypeTest<Entity, T>,
        box: AABB,
        predicate: Predicate<in T>,
        output: MutableList<in T>,
        maxResults: Int
    ) {
        for (candidate in entities.values) {
            val casted = typeTest.tryCast(candidate)
            if (casted != null && !casted.isRemoved && casted.boundingBox.intersects(box) && predicate.test(casted)) {
                output.add(casted)
                if (output.size >= maxResults) {
                    return
                }
            }
        }
    }

    private fun registerPlayerIfApplicable(entity: Entity) {
        if (entity is AbstractClientPlayer) {
            playerList.add(entity)
        }
    }

    private fun cleanupEntity(entity: Entity) {
        if (entity is AbstractClientPlayer) {
            playerList.remove(entity)
        }
        entity.deltaMovement = Vec3.ZERO
    }

    private fun ejectPassengersFor(vehicleId: Int) {
        for (candidate in ArrayList(entities.values)) {
            val v = candidate.vehicle
            if (v != null && v.id == vehicleId) {
                candidate.stopRiding()
            }
        }
    }
}
