package me.collective.client.managers

import me.collective.client.ClientAccess
import me.collective.client.ClientContext
import me.collective.client.events.EventHandler
import me.collective.client.events.EventStage
import me.collective.client.events.InventoryActionEvent
import me.collective.client.events.LocalPlayerPreTickEvent
import me.collective.client.events.LocalPlayerSendPositionEvent
import me.collective.client.events.MultiPlayerGameModeHandleInventoryMouseClickEvent
import me.collective.client.events.MultiPlayerGameModeUseItemOnEvent
import me.collective.client.events.PacketEvent
import me.collective.client.events.SelectedSlotChangeEvent
import me.collective.client.modules.client.AntiCheatModule
import me.collective.client.modules.exploit.XCarryModule
import me.collective.client.utils.ElapsedTimer
import me.collective.client.utils.network.PacketSender
import me.collective.client.utils.world.WorldUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import java.util.ArrayDeque
import java.util.Collections
import java.util.Queue

class InventoryStateManager : ClientAccess {
    private val deferredSlotUpdates: MutableMap<Int, DeferredSlotUpdate> = Collections.synchronizedMap(HashMap())
    private val clickTimestamps: Queue<Long> = ArrayDeque()
    private val lastClickTimer = ElapsedTimer()
    private val enderChestUseTimer = ElapsedTimer()

    @Volatile
    private var containerOpen = false

    @Volatile
    private var clickBatching = false

    @Volatile
    private var clickBatchCompleted = false

    private var localEntityEventPending = false
    private var serverSelectedSlot = -1
    private var slotResyncArmed = false

    init {
        ClientAccess.eventBus.subscribe(this)
    }

    fun setClickBatching(batching: Boolean) {
        if (clickBatching != batching) {
            clickBatching = batching
            if (ClientAccess.collectiveMinecraft.screen !is AbstractContainerScreen<*>) {
                if (RotationManager.isMovementSyncEnabled()) {
                    if (!xCarry().isToggled && !clickBatching && clickBatchCompleted) {
                        close()
                    }
                }
            }
            clickBatchCompleted = false
        }
    }

    fun close() {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val packet = ServerboundContainerClosePacket(player.containerMenu.containerId)
        PacketSender.send(packet)
        containerOpen = false
    }

    private fun resendHeldSlot(slot: Int) {
        if (RotationManager.isMovementSyncEnabled() && slotResyncArmed) {
            PacketSender.sendSelectedSlot((slot + 1) % 9)
            PacketSender.sendSelectedSlot(slot)
            // Ресинк разово: после отправки разряджаем флаг, иначе он остаётся взведённым
            // и (slot+1, slot) уходит на каждое входящее изменение слота.
            slotResyncArmed = false
        }
    }

    @EventHandler
    private fun onConnectionDoSendPacketEvent(event: PacketEvent.PostSend) {
        if (event.packet is ServerboundContainerClickPacket && (!clickBatching || !clickBatchCompleted)) {
            ClientAccess.eventBus.post(InventoryActionEvent(EventStage.Post))
            clickBatchCompleted = true
        }
    }

    @EventHandler
    private fun onClientPacketListenerHandleSetHeldSlotEvent(
        event: SelectedSlotChangeEvent.ClientPacketListenerHandleSetHeldSlotEvent
    ) {
        serverSelectedSlot = event.slot
        val player = ClientAccess.collectiveMinecraft.player ?: return
        val selectedSlot = player.inventory.selected
        val minecraft = ClientAccess.collectiveMinecraft
        if (!minecraft.isLocalServer && antiCheat().noServerSlotSetting.value) {
            resendHeldSlot(event.slot)
            if (player.tickCount < 5) {
                return
            }
            event.cancel()
            if (RotationManager.isMovementSyncEnabled()) {
                val restoreHeldSlot = Runnable {
                    PacketSender.sendSelectedSlot(selectedSlot)
                    val currentPlayer = ClientAccess.collectiveMinecraft.player ?: return@Runnable
                    if (currentPlayer.isUsingItem) {
                        ClientAccess.collectiveMinecraft.gameMode?.releaseUsingItem(currentPlayer)
                    }
                }
                ClientContext.taskScheduler.scheduleRotationTask(restoreHeldSlot, 0)
            } else {
                PacketSender.sendSelectedSlot(selectedSlot)
            }
        }
    }

    private fun matchesOtherHeldStack(stack: ItemStack, menuSlot: Int): Boolean {
        val player = ClientAccess.collectiveMinecraft.player ?: return false
        val offhandStack = player.offhandItem
        if (ItemStack.matches(offhandStack, stack)) {
            return true
        }
        for (hotbarSlot in 0 until 9) {
            if (menuSlot - 36 != hotbarSlot) {
                val hotbarStack = player.inventory.getItem(hotbarSlot)
                if (ItemStack.matches(hotbarStack, stack)) {
                    return true
                }
            }
        }
        return false
    }

    @EventHandler(priority = -100)
    private fun onMultiPlayerGameModeHandleInventoryMouseClickEvent(
        event: MultiPlayerGameModeHandleInventoryMouseClickEvent
    ) {
        if (event.isCancelled) {
            return
        }
        ClientAccess.collectiveMinecraft.execute(::recordClick)
        containerOpen = true
        lastClickTimer.reset()
        if (!clickBatching || !clickBatchCompleted) {
            ClientAccess.eventBus.post(InventoryActionEvent(EventStage.Pre))
        }
    }

    private fun recordClick() {
        clickTimestamps.add(System.currentTimeMillis())
    }

    @EventHandler(priority = -100)
    private fun onInventoryClickAfter(
        event: MultiPlayerGameModeHandleInventoryMouseClickEvent.After
    ) {
        if (!clickBatching || !clickBatchCompleted) {
            ClientAccess.eventBus.post(InventoryActionEvent(EventStage.Post))
            clickBatchCompleted = true
        }
    }

    @EventHandler
    private fun onMultiPlayerGameModeUseItemOnEvent(event: MultiPlayerGameModeUseItemOnEvent) {
        val player = ClientAccess.collectiveMinecraft.player ?: return
        if (!player.isShiftKeyDown) {
            val pos = event.hitResult.blockPos
            if (WorldUtils.getBlock(pos) == Blocks.ENDER_CHEST) {
                enderChestUseTimer.reset()
            }
        }
    }

    @EventHandler(priority = 200)
    private fun onLocalPlayerPreTick(event: LocalPlayerPreTickEvent) {
        val now = System.currentTimeMillis()
        clickTimestamps.removeIf { timestamp -> now - timestamp >= CLICK_WINDOW_MILLIS }
    }

    @EventHandler(priority = -100)
    private fun onConnectionSendPacketEvent(event: PacketEvent.Send) {
        if (event.isCancelled) {
            return
        }
        val packet = event.packet
        if (packet is ServerboundContainerClickPacket) {
            containerOpen = true
            lastClickTimer.reset()
            if (!clickBatching || !clickBatchCompleted) {
                ClientAccess.eventBus.post(InventoryActionEvent(EventStage.Pre))
            }
        }
        if (packet is ServerboundContainerClosePacket) {
            containerOpen = false
        }
        if (packet is ServerboundSetCarriedItemPacket) {
            if (packet.slot != serverSelectedSlot) {
                ClientAccess.eventBus.post(SelectedSlotChangeEvent(packet.slot))
                serverSelectedSlot = packet.slot
            }
        }
    }

    @EventHandler(priority = -200)
    private fun onLocalPlayerSendPositionEvent(event: LocalPlayerSendPositionEvent) {
        if (event.stage == EventStage.Post) {
            return
        }
        synchronized(deferredSlotUpdates) {
            val now = System.currentTimeMillis()
            val readySlots = ArrayList<Int>()
            deferredSlotUpdates.forEach { (slot, update) ->
                if (now >= update.resumeAtMillis) {
                    readySlots.add(slot)
                }
            }
            val connection = ClientAccess.collectiveMinecraft.player?.connection
            for (readySlot in readySlots) {
                val removedUpdate = deferredSlotUpdates.remove(readySlot) ?: continue
                connection?.handleContainerSetSlot(removedUpdate.packet)
            }
        }
        localEntityEventPending = false
        if (xCarry().isToggled || ClientAccess.collectiveMinecraft.screen is AbstractContainerScreen<*>) {
            return
        }
        if (!lastClickTimer.hasElapsed(50L) || !containerOpen) {
            return
        }
        val nowMillis = System.currentTimeMillis()
        if (nowMillis - ClientContext.serverStateManager.getLastPacketTime() > 2500L) {
            return
        }
        if (RotationManager.is2b2tMode() && !enderChestUseTimer.hasElapsed(200L)) {
            return
        }
        close()
    }

    @EventHandler(priority = 100)
    private fun onPacketReceive(event: PacketEvent.Receive) {
        val packet = event.packet
        if (packet is ClientboundDisconnectPacket) {
            // Сброс при дисконнекте: раньше ветка стояла в Send-хендлере (там clientbound-пакеты
            // не постятся) и никогда не срабатывала — состояние «залипало» между сессиями.
            containerOpen = false
            serverSelectedSlot = -1
            slotResyncArmed = false
            deferredSlotUpdates.clear()
        }
        if (packet is ClientboundLoginPacket) {
            slotResyncArmed = true
        }
        if (packet is ClientboundContainerClosePacket) {
            containerOpen = false
        }
        if (packet is ClientboundContainerSetContentPacket) {
            deferredSlotUpdates.clear()
        }
        if (packet is ClientboundContainerSetSlotPacket) {
            if (RotationManager.isMovementSyncEnabled()) {
                val player = ClientAccess.collectiveMinecraft.player ?: return
                val slotWord = packet.slot
                if (slotWord == 36 + player.inventory.selected) {
                    return
                }
                if (slotWord < 36 || slotWord > 44) {
                    return
                }
                if (!matchesOtherHeldStack(packet.item, slotWord)) {
                    deferredSlotUpdates.remove(slotWord)
                    return
                }
                deferredSlotUpdates[slotWord] = DeferredSlotUpdate(packet, System.currentTimeMillis() + 500L)
                event.cancel()
            }
        }
        if (packet is ClientboundEntityEventPacket) {
            if (packet.eventId.toInt() == 35) {
                val level = ClientAccess.collectiveMinecraft.level ?: return
                if (packet.getEntity(level) == ClientAccess.collectiveMinecraft.player) {
                    localEntityEventPending = true
                }
            }
        }
    }

    fun hasPendingLocalEntityEvent(): Boolean {
        return localEntityEventPending
    }

    private data class DeferredSlotUpdate(
        val packet: ClientboundContainerSetSlotPacket,
        val resumeAtMillis: Long,
    )

    companion object {
        private const val CLICK_WINDOW_MILLIS = 4000L
        private var antiCheat: AntiCheatModule? = null
        private var xCarry: XCarryModule? = null

        private fun antiCheat(): AntiCheatModule {
            if (antiCheat == null) {
                antiCheat = ClientContext.getModule(AntiCheatModule::class.java)
            }
            return antiCheat!!
        }

        private fun xCarry(): XCarryModule {
            if (xCarry == null) {
                xCarry = ClientContext.getModule(XCarryModule::class.java)
            }
            return xCarry!!
        }

        @JvmStatic
        fun wouldReachClickLimit(manager: InventoryStateManager, additionalClicks: Int): Boolean {
            return antiCheat().is2b2tEnabled && manager.clickTimestamps.size + additionalClicks >= 71
        }

        @JvmStatic
        fun isClickRateLimited(manager: InventoryStateManager): Boolean {
            return wouldReachClickLimit(manager, 0)
        }
    }
}
