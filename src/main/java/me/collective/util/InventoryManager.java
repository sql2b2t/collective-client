package me.collective.util;

import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;

/**
 * Серверный менеджер слота: отделяет серверный слот от клиентского,
 * исключает дублирующиеся пакеты слота (GrimAC bounce-fix).
 */
public class InventoryManager {
    private static final Minecraft mc = Minecraft.getInstance();
    private static InventoryManager INSTANCE;

    private int serverSlot = -1;
    private int lastSentSlot = -1;
    private boolean sendingPacket;
    private boolean isEating;
    private int currentPriority;

    private InventoryManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public static InventoryManager getInstance() {
        if (INSTANCE == null) INSTANCE = new InventoryManager();
        return INSTANCE;
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (this.sendingPacket) return;
        if (event.isCancelled()) return;

        // Пока ни один модуль не держит свап (currentPriority == 0) менеджер
        // полностью прозрачен: ванильные пакеты слота проходят как есть, без
        // дедупликации по локальному зеркалу — при разъезде зеркала с сервером
        // (смерть/респавн/сброс слота) это давало десинк инвентаря на копании.
        if (this.currentPriority <= 0) return;

        Packet<?> packet = event.packet;
        if (packet instanceof ClickSlotC2SPacket || packet instanceof CloseHandledScreenC2SPacket) {
            if (mc.player != null && mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            return;
        }

        if (!(packet instanceof UpdateSelectedSlotC2SPacket update)) return;

        int packetSlot = update.getSelectedSlot();

        if (!Inventory.isValidHotbarIndex(packetSlot) || this.serverSlot == packetSlot) {
            event.cancel();
            return;
        }

        if (this.lastSentSlot == packetSlot) {
            event.cancel();
            setSlotForced(packetSlot);
            return;
        }

        this.serverSlot = packetSlot;
        this.lastSentSlot = packetSlot;
    }

    @EventHandler(priority = 200)
    public void onPacketReceive(PacketEvent.Receive event) {
        // зеркало обновляем всегда — иначе после простоя свап стартует с устаревшего слота
        if (event.packet instanceof UpdateSelectedSlotS2CPacket update) {
            this.serverSlot = update.slot();
        }
    }

    @EventHandler
    public void onTick(TickEvent.Post event) {
        if (mc.player != null && this.serverSlot == -1) {
            this.serverSlot = mc.player.getInventory().selectedSlot;
        }

        if (!this.isEating && this.currentPriority > 0) this.currentPriority = 0;
    }

    public void onDisconnect() {
        this.serverSlot = -1;
        this.lastSentSlot = -1;
        this.currentPriority = 0;
        this.isEating = false;
    }

    public void setSlot(int barSlot) {
        setSlot(barSlot, 0);
    }

    public void setSlot(int barSlot, boolean highPriority) {
        setSlot(barSlot, highPriority ? 20 : 0);
    }

    public void setSlot(int barSlot, int priority) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (priority < this.currentPriority) return;

        if (this.serverSlot == -1) {
            this.serverSlot = mc.player.getInventory().selectedSlot;
        }

        if (this.serverSlot != barSlot && Inventory.isValidHotbarIndex(barSlot)) {
            setSlotForced(barSlot);
            this.currentPriority = priority;
        }
    }

    public void setSlotForced(int barSlot) {
        if (mc.getNetworkHandler() == null) return;
        if (!Inventory.isValidHotbarIndex(barSlot)) return;
        if (this.serverSlot == barSlot) return;

        this.sendingPacket = true;

        try {
            if (this.lastSentSlot == barSlot) {
                int bounce = (barSlot + 1) % 9;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(bounce));
                this.lastSentSlot = bounce;
            }

            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(barSlot));

            this.lastSentSlot = barSlot;
            this.serverSlot = barSlot;
        } finally {
            this.sendingPacket = false;
        }
    }

    public void syncToClient() {
        if (mc.player == null) return;
        if (isDesynced()) setSlotForced(mc.player.getInventory().selectedSlot);
    }

    public boolean isDesynced() {
        if (mc.player == null) return false;
        return mc.player.getInventory().selectedSlot != this.serverSlot;
    }

    public int getServerSlot() {
        if (mc.player == null) return -1;
        return this.serverSlot == -1 ? mc.player.getInventory().selectedSlot : this.serverSlot;
    }

    public int getLastSentSlot() {
        return this.lastSentSlot;
    }

    public void setEating(boolean eating) {
        this.isEating = eating;
        this.currentPriority = eating ? 10 : 0;
    }

    public boolean isEating() {
        return this.isEating;
    }

    public static class Priority {
        public static final int NORMAL = 0;
        public static final int TOTEM = 5;
        public static final int EATING = 10;
        public static final int SURROUND = 20;
        public static final int PEARL_PHASE = 30;
    }
}