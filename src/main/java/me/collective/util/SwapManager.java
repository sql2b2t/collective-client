package me.collective.util;

import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;

/**
 * Арбитр захвата слота: один модуль держит предмет в руке сервера, остальные ждут.
 */
public class SwapManager {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static SwapManager INSTANCE;

    private final InventoryManager inv = InventoryManager.getInstance();

    private Object owner;
    private int priority;
    private boolean silent;

    private int originalSlot = -1;
    private int heldSlot = -1;
    private int releaseTicks;

    private boolean actionConflict;
    private boolean movedSinceTickEnd;
    private boolean inTickHead;
    private int pendingTeleportConfirms;
    private boolean pendingRelease;
    private boolean bouncing;

    private Object resumeOwner;
    private int resumePriority;
    private boolean resumeSilent;
    private int resumeSlot = -1;
    private int resumeReleaseTicks;

    private SwapManager() {
        EventBus.INSTANCE.subscribe(this);
    }

    public static SwapManager getInstance() {
        if (INSTANCE == null) INSTANCE = new SwapManager();
        return INSTANCE;
    }

    public boolean hold(Object owner, int slot, int priority, int releaseDelay) {
        return hold(owner, slot, priority, true, releaseDelay);
    }

    public boolean hold(Object owner, int slot, int priority, boolean silent, int releaseDelay) {
        if (mc.player == null || mc.getNetworkHandler() == null) return false;
        if (!PlayerInventory.isValidHotbarIndex(slot)) return false;

        checkDrift();

        if (this.owner != null) {
            if (this.owner != owner) {
                if (priority <= this.priority) return false;

                this.owner = owner;
                clearResume();
            }

            if (this.resumeOwner == null) {
                this.priority = priority;
                this.silent = silent;
            }

            if (this.inv.getServerSlot() == slot) {
                this.heldSlot = slot;

                if (this.resumeOwner == null) {
                    this.releaseTicks = Math.max(this.releaseTicks, releaseDelay);
                    this.pendingRelease = false;
                }

                return true;
            }

            if (this.actionConflict || usingMainHandItem()) return false;

            swapTo(slot);
            this.heldSlot = slot;

            if (this.resumeOwner == null) {
                this.releaseTicks = Math.max(this.releaseTicks, releaseDelay);
                this.pendingRelease = false;
            }

            return true;
        }

        if (this.inv.getServerSlot() == slot) return true;
        if (this.actionConflict || usingMainHandItem()) return false;

        this.owner = owner;
        this.priority = priority;
        this.silent = silent;
        this.originalSlot = clientSlot();

        swapTo(slot);
        this.heldSlot = slot;
        this.releaseTicks = releaseDelay;
        return true;
    }

    public boolean pulse(Object owner, int slot, int priority) {
        return pulse(owner, slot, priority, 1);
    }

    public boolean pulse(Object owner, int slot, int priority, int ticks) {
        if (mc.player == null || mc.getNetworkHandler() == null) return false;
        if (!PlayerInventory.isValidHotbarIndex(slot)) return false;

        checkDrift();

        if (this.owner == owner && this.resumeOwner != null) {
            if (this.inv.getServerSlot() != slot) {
                if (this.actionConflict || usingMainHandItem()) return false;

                swapTo(slot);
                this.heldSlot = slot;
            }

            this.releaseTicks = Math.max(this.releaseTicks, ticks);
            this.pendingRelease = false;
            return true;
        }

        if (this.owner == null || this.owner == owner) return hold(owner, slot, priority, true, ticks);

        if (this.resumeOwner != null) return false;
        if (this.inv.getServerSlot() == slot) return true;
        if (this.actionConflict || usingMainHandItem()) return false;

        this.resumeOwner = this.owner;
        this.resumePriority = this.priority;
        this.resumeSilent = this.silent;
        this.resumeSlot = this.heldSlot;
        this.resumeReleaseTicks = Math.max(1, this.releaseTicks);

        this.owner = owner;
        this.priority = Math.max(priority, this.resumePriority);
        this.silent = true;

        swapTo(slot);
        this.heldSlot = slot;
        this.releaseTicks = ticks;
        return true;
    }

    public boolean isActionConflictTick() {
        return this.actionConflict;
    }

    public boolean isHolding(Object owner) {
        checkDrift();
        return this.owner == owner;
    }

    public int getServerSlot() {
        return this.inv.getServerSlot();
    }

    public boolean isForeignSession(Object owner, int priority) {
        checkDrift();
        return this.owner != null && this.owner != owner && this.priority > priority;
    }

    public boolean isBorrowing(Object owner) {
        checkDrift();
        return this.owner == owner && this.resumeOwner != null;
    }

    public void scheduleRelease(Object owner, int ticks) {
        if (this.owner != owner || this.resumeOwner != null) return;
        this.releaseTicks = Math.max(this.releaseTicks, ticks);
    }

    public void releaseNow(Object owner) {
        if (this.owner != owner) return;

        if (this.actionConflict || usingMainHandItem() || !this.inTickHead) {
            this.releaseTicks = 0;
            this.pendingRelease = true;
            return;
        }

        doRelease();
    }

    public int referenceSlot() {
        checkDrift();
        return this.owner != null ? this.originalSlot : clientSlot();
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;

        Packet<?> packet = event.packet;

        if (packet instanceof PlayerInteractEntityC2SPacket
            || packet instanceof PlayerInteractItemC2SPacket
            || packet instanceof PlayerInteractBlockC2SPacket
            || packet instanceof ClientCommandC2SPacket) {
            this.actionConflict = true;
            return;
        }

        if (packet instanceof PlayerActionC2SPacket action
            && action.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            this.actionConflict = true;
            return;
        }

        if (packet instanceof PlayerMoveC2SPacket) {
            if (this.pendingTeleportConfirms > 0) {
                this.pendingTeleportConfirms--;
                return;
            }

            this.actionConflict = false;
            this.movedSinceTickEnd = true;
            this.inTickHead = false;
            return;
        }

        if (packet instanceof ClientTickEndC2SPacket) {
            if (!this.movedSinceTickEnd) this.actionConflict = false;

            this.movedSinceTickEnd = false;
            this.inTickHead = false;
        }
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) this.pendingTeleportConfirms++;
    }

    /** Полный сброс при выходе с сервера. */
    public void onDisconnect() {
        clearSession();
        this.actionConflict = false;
        this.movedSinceTickEnd = false;
        this.inTickHead = false;
        this.pendingTeleportConfirms = 0;
        this.bouncing = false;
    }

    private boolean usingMainHandItem() {
        return mc.player != null
            && mc.player.isUsingItem()
            && mc.player.getActiveHand() == Hand.MAIN_HAND;
    }

    @EventHandler(priority = 200)
    public void onTickPre(TickEvent.Pre event) {
        this.inTickHead = true;

        checkDrift();

        if (this.pendingRelease && !this.actionConflict && !usingMainHandItem()) {
            this.pendingRelease = false;

            if (this.owner != null) doRelease();
        }
    }

    @EventHandler(priority = -200)
    public void onTickPost(TickEvent.Post event) {
        checkDrift();

        if (this.owner == null || this.releaseTicks <= 0) return;

        if (--this.releaseTicks == 0) {
            if (this.actionConflict || usingMainHandItem()) {
                this.releaseTicks = 1;
            } else {
                this.pendingRelease = true;
            }
        }
    }

    private void checkDrift() {
        if (this.bouncing) return;
        if (this.owner != null && this.inv.getServerSlot() != this.heldSlot) clearSession();
    }

    private void doRelease() {
        if (this.resumeOwner != null) {
            this.owner = this.resumeOwner;
            this.priority = this.resumePriority;
            this.silent = this.resumeSilent;

            int slot = this.resumeSlot;
            this.releaseTicks = Math.max(1, this.resumeReleaseTicks);

            clearResume();

            if (mc.player == null || mc.getNetworkHandler() == null) {
                clearSession();
                return;
            }

            if (!PlayerInventory.isValidHotbarIndex(slot)) {
                clearSession();
                return;
            }

            swapTo(slot);
            this.heldSlot = slot;
            return;
        }

        boolean visible = !this.silent;
        int restore = this.originalSlot;

        clearSession();

        if (mc.player == null || mc.getNetworkHandler() == null) return;

        if (visible && PlayerInventory.isValidHotbarIndex(restore)) {
            mc.player.getInventory().setSelectedSlot(restore);
            this.inv.setSlotForced(restore);
        } else {
            this.inv.syncToClient();
        }
    }

    private void swapTo(int slot) {
        if (!this.silent) mc.player.getInventory().setSelectedSlot(slot);
        this.inv.setSlotForced(slot);
    }

    private void clearSession() {
        this.owner = null;
        this.priority = 0;
        this.silent = true;
        this.originalSlot = -1;
        this.heldSlot = -1;
        this.releaseTicks = 0;
        this.pendingRelease = false;

        clearResume();
    }

    private void clearResume() {
        this.resumeOwner = null;
        this.resumePriority = 0;
        this.resumeSilent = true;
        this.resumeSlot = -1;
        this.resumeReleaseTicks = 0;
    }

    private int clientSlot() {
        return mc.player != null ? mc.player.getInventory().selectedSlot : 0;
    }

    public static final class Priority {
        public static final int FEED = 2;
        public static final int PRECHARGE = 3;
        public static final int MINING = 10;
        public static final int WEAPON = 20;
        public static final int SURROUND = 25;
        public static final int CRYSTAL = 30;
        public static final int PLACE = 35;
        public static final int PEARL = 40;
    }
}
