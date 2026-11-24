package me.collective.client.modules.combat

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.PacketEvent
import me.collective.client.managers.InventoryStateManager
import me.collective.client.utils.entity.EntityUtils
import me.collective.client.utils.entity.PlayerStateUtils
import me.collective.client.utils.entity.SwingAction
import me.collective.client.utils.inventory.InventoryUtils
import me.collective.client.utils.inventory.ItemUtils
import me.collective.client.utils.world.BlockInteractionUtils
import me.collective.client.utils.world.WorldUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Items
import java.util.ArrayDeque
import java.util.Deque
import java.util.HashMap

class FeetPlaceFireworkSupport : ClientAccess {
    private val pendingFireworks = HashMap<BlockPos, PendingFirework>()
    private val itemSpawnTimes = HashMap<BlockPos, Deque<Long>>()

    fun onEnable() {
        this.pendingFireworks.clear()
    }

    private fun hasHorizontalNeighbor(position: BlockPos, positions: Iterable<BlockPos>): Boolean {
        for (neighbor in positions) {
            if (neighbor.distManhattan(position) == 1 && position.y == neighbor.y) {
                return true
            }
        }
        return false
    }

    private fun pruneSpawnTimes(times: Deque<Long>, now: Long) {
        while (true) {
            val oldest = times.peekFirst() ?: break
            if (now - oldest > 500L) {
                times.pollFirst()
            } else {
                break
            }
        }
    }

    fun processFireworks(airPlace: Boolean, raytrace: Boolean) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (player.isSpectator || !player.isAlive || player.isHandsBusy || player.isSleeping) {
            return
        }

        val now = System.currentTimeMillis()
        this.pruneSpawnHistory(now)

        val position = this.selectFireworkPosition() ?: return
        if (BlockInteractionUtils.isOutsidePlacementBounds(position)) {
            return
        }

        val strictDirection = BlockInteractionUtils.isStrictDirectionEnabled()
        val face = BlockInteractionUtils.findSupportDirection(position, strictDirection, raytrace)
        if (face == null && !airPlace) {
            return
        }

        val previousSlot = InventoryUtils.getSelectedSlot()
        val rocketItem = Items.FIREWORK_ROCKET
        val rocketSlot = InventoryUtils.findSwapSlot(rocketItem, true)

        if (InventoryStateManager.wouldReachClickLimit(ClientContext.inventoryStateManager, 2)) {
            if (!rocketSlot.hotbar) {
                return
            }
        }

        InventoryUtils.runWithSwap(previousSlot, rocketSlot) {
            val hand = rocketSlot.hand
            BlockInteractionUtils.sendPlacement(position, null, face, true, hand)
            val swingManager = ClientContext.swingManager
            swingManager.swing(InteractionHand.MAIN_HAND, SwingAction.INTERACT)

            this.pendingFireworks[position] = PendingFirework(
                now,
                this.getFireworkDuration(rocketSlot),
                position
            )
        }
    }

    private fun getFireworkDuration(rocketSlot: InventoryUtils.SwapSlot): Int {
        val rocketStack = rocketSlot.stack
        return ItemUtils.getFireworkDuration(rocketStack)
    }

    private fun selectFireworkPosition(): BlockPos? {
        val player = ClientAccess.collectiveMinecraft.player ?: return null
        val openPositions = PlayerStateUtils.getOccupiedBlockPositions(player)
            .filter { pos -> WorldUtils.isAir(pos) }
            .toSet()

        val occupiedPositions = PlayerStateUtils.getSurroundPositions(player)
        val fireworkPositions = EntityUtils.getFireworkPositions()
        val now = System.currentTimeMillis()
        var hasThreatenedPosition = false

        for (position in occupiedPositions) {
            if (this.hasCoveredAirBlock(position) && this.isThreatenedPosition(position)) {
                hasThreatenedPosition = true
                break
            }
        }

        for (candidate in occupiedPositions) {
            if (this.hasCoveredAirBlock(candidate) && this.hasHorizontalNeighbor(candidate, openPositions)) {
                val belowPosition = candidate.below()
                if (!WorldUtils.isBedrockOrObsidian(belowPosition)) {
                    continue
                }

                val threatened = this.isThreatenedPosition(candidate)
                if (!hasThreatenedPosition || threatened) {
                    val pending = this.pendingFireworks[candidate]
                    if (pending == null) {
                        if (threatened && !fireworkPositions.contains(candidate)) {
                            return candidate
                        }
                    } else {
                        val latencyMillis = ClientContext.serverStateManager.latencyMillis
                        val retryDelay = latencyMillis + 50L
                        val sentAtMillis = pending.sentAtMillis
                        val durationTicks = pending.durationTicks

                        val expiryTime = sentAtMillis + durationTicks * 50L
                        val ready = now >= expiryTime || !fireworkPositions.contains(candidate)
                        if (ready && now > pending.sentAtMillis + retryDelay) {
                            return candidate
                        }
                    }
                }
            }
        }

        return null
    }

    fun isTrackedFireworkPosition(positions: Set<BlockPos>, position: BlockPos): Boolean {
        val latencyMillis = ClientContext.serverStateManager.latencyMillis
        val retryDelay = latencyMillis + 50L
        val pending = this.pendingFireworks[position]
        if (pending != null) {
            if (positions.contains(position)) {
                return true
            }

            val now = System.currentTimeMillis()
            if (now < pending.sentAtMillis + retryDelay) {
                return true
            }
        }

        return positions.contains(position)
    }

    fun observeSpawnPacket(event: PacketEvent.Receive) {
        val receivedAt = System.currentTimeMillis()
        val packet = event.packet
        if (packet is ClientboundAddEntityPacket) {
            if (packet.type == EntityType.ITEM) {
                val itemX = packet.x
                val itemY = packet.y
                val itemZ = packet.z

                val position = BlockPos.containing(itemX, itemY, itemZ)
                ClientAccess.collectiveMinecraft.execute {
                    val spawnTimes = this.itemSpawnTimes.computeIfAbsent(position) { ArrayDeque() }
                    spawnTimes.addLast(receivedAt)
                    this.pruneSpawnTimes(spawnTimes, receivedAt)
                }
            }
        }
    }

    private fun pruneSpawnHistory(now: Long) {
        val iterator = this.itemSpawnTimes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            this.pruneSpawnTimes(entry.value, now)
            if (entry.value.isEmpty()) {
                iterator.remove()
            }
        }
    }

    private fun hasCoveredAirBlock(position: BlockPos): Boolean {
        val level = ClientAccess.collectiveMinecraft.level ?: return false
        val state = level.getBlockState(position)
        if (state.canBeReplaced()) {
            val abovePosition = position.above()
            val aboveState = level.getBlockState(abovePosition)
            if (!aboveState.canBeReplaced()) {
                return true
            }
        }
        return false
    }

    private fun isThreatenedPosition(position: BlockPos): Boolean {
        val now = System.currentTimeMillis()
        val spawnTimes = this.itemSpawnTimes[position]
        if (spawnTimes != null) {
            this.pruneSpawnTimes(spawnTimes, now)
            val recentItemCount = spawnTimes.size
            if (recentItemCount >= 2) {
                return true
            }
        }

        val damage = ClientContext.blockBreakTracker.getBreakProgress(position)
        return damage > 0.9
    }

    data class PendingFirework(
        val sentAtMillis: Long,
        val durationTicks: Int,
        val position: BlockPos
    )
}
