package me.collective.client.modules.client

import me.collective.client.ClientContext
import me.collective.client.utils.network.ImportantPacket
import me.collective.client.managers.RotationManager
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Predicate
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket

class PacketRateLimiter {
    private val lock: Lock = ReentrantLock()
    private val global: PacketWindow
    private val interacts: PacketWindow
    private val windows: List<PacketWindow>

    init {
        global = PacketWindow.create("Global", 280, 320) { packet: Packet<*> -> isGlobalPacket(packet) }
        interacts = PacketWindow.create("Interacts", 90, 100) { packet: Packet<*> -> isInteractPacket(packet) }
        windows = listOf(global, interacts)
    }

    val globalWindow: PacketWindow
        get() = this.global

    val interactWindow: PacketWindow
        get() = this.interacts

    fun countRecentPackets(window: PacketWindow): Int {
        this.lock.lock()
        try {
            return window.countRecentPackets(System.currentTimeMillis())
        } finally {
            this.lock.unlock()
        }
    }

    fun isLimitReached(window: PacketWindow): Boolean {
        return this.countRecentPackets(window) >= window.limit
    }

    fun shouldCancelPacket(packet: Packet<*>): Boolean {
        if (packet is ServerboundMovePlayerPacket ||
         packet is ServerboundKeepAlivePacket ||
         packet is ServerboundAcceptTeleportationPacket ||
         packet is ServerboundContainerClosePacket
        ) {
            return false
        }
        val rotationManager: RotationManager = ClientContext.rotationManager
        if (rotationManager.isExecutingAction()) {
            return false
        }
        this.lock.lock()
        try {
            val now = System.currentTimeMillis()
            if (packet is ServerboundSwingPacket && this.interacts.countRecentPackets(now) >= this.interacts.limit) {
                return true
            }
            for (window in this.windows) {
                if (window.predicate.test(packet) &&
         window.countRecentPackets(now) >= this.limitForPacket(window, packet)
                ) {
                    return true
                }
            }
            for (window in this.windows) {
                if (window.predicate.test(packet)) {
                    window.timestamps.addLast(now)
                }
            }
            return false
        } finally {
            this.lock.unlock()
        }
    }

    private fun limitForPacket(window: PacketWindow, packet: Packet<*>): Int {
        return if (this.isImportantPacket(packet)) window.importantLimit else window.limit
    }

    private fun isImportantPacket(packet: Packet<*>): Boolean {
        if (packet is ServerboundPlayerActionPacket) {
            if (packet.action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK ||
         packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
            ) {
                return true
            }
            if (packet.action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
                return true
            }
        }
        if (packet is ServerboundPongPacket) {
            return true
        }
        return packet is ImportantPacket && packet.`collective$isImportant`()
    }

    data class PacketWindow(
        val name: String,
        val limit: Int,
        val importantLimit: Int,
        val predicate: Predicate<Packet<*>>,
        val timestamps: Deque<Long>
    ) {
        fun countRecentPackets(now: Long): Int {
            while (!this.timestamps.isEmpty()) {
                val ageMillis = now - this.timestamps.peekFirst()!!
                if (ageMillis <= 1000L) {
                    break
                }
                this.timestamps.pollFirst()
            }
            return this.timestamps.size
        }

        companion object {
            @JvmStatic
            fun create(name: String, limit: Int, importantLimit: Int, predicate: Predicate<Packet<*>>): PacketWindow {
                return PacketWindow(name, limit, importantLimit, predicate, ArrayDeque())
            }
        }
    }

    companion object {
        private fun isInteractPacket(packet: Packet<*>): Boolean {
            return packet is ServerboundInteractPacket || packet is ServerboundPlayerActionPacket
        }

        private fun isGlobalPacket(packet: Packet<*>): Boolean {
            return packet is ServerboundInteractPacket ||
         packet is ServerboundUseItemPacket ||
         packet is ServerboundUseItemOnPacket ||
         packet is ServerboundPlayerActionPacket ||
         packet is ServerboundPongPacket
        }
    }
}
