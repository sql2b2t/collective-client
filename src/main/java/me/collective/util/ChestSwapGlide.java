package me.collective.util;

import me.collective.util.SwapManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.Minecraft;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.util.InvUtils;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.component.DataComponents;
import me.collective.mixin.EntityAccessor;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Полёт без траты прочности элитры.
 *
 * <p>Перенос {@code bep.hax.util.ChestSwapGlide}, GPL-3.0. Идея: надетую
 * элитру и нагрудник в руке можно менять местами прямо в пакетах, не выходя
 * из одного серверного тика. Сервер тогда ни разу не видит флаг планирования
 * между тиками и не получает оснований списать прочность — а фейерверк всё
 * равно работает, потому что запускается на тике, где флаг поднят.
 *
 * <p>Состояния, которые пришлось удерживать вместе:
 *
 * <ul>
 *   <li>elitra должна быть надета, нагрудник — в руке (вторая рука
 *       предпочтительна), иначе менять нечего;
 *   <li>«клир» от сервера (подтверждение смены слота) нельзя пропустить, иначе
 *       сервер применит смену позже и игрок окажется без элитры в воздухе —
 *       поэтому подтверждения копятся и отправляются пачкой;
 *   <li>подменять имеет смысл только там, где сервер «не дотянется» до
 *       земли, то есть в воздухе достаточно далеко;
 *   <li>телепорт в середине обмена означает, что сервер увидел состояние без
 *       элитры, и планирование надо начинать заново.
 * </ul>
 *
 * <p>Основано на BepHax Community, авторы BepHax contributors, GPL-3.0.
 */
public class ChestSwapGlide {

    /** Столько тиков ждём ответа сервера на смену слота, прежде чем забыть. */
    private static final int SWAP_FORGET = 200;
    private static final int HOLD_GRACE_TICKS = 8;
    private static final int CLEAR_WAIT_TICKS = 24;
    private static final int CONFLICT_DEFER_MAX = 2;

    private static final int NO_PING = Integer.MIN_VALUE;

    private static final double GHOST_REACH = 4.0;
    private static final double GHOST_MARGIN = 2.0;
    private static final double ARM_HYSTERESIS = 2.0;
    private static final int ARM_SETTLE_TICKS = 10;

    private static final int CLEAR_RTT_DEFAULT = 6;
    private static final int CLEAR_RTT_MAX = 12;

    private static volatile ChestSwapGlide instance;

    /** Все живые экземпляры: нужны для логирования телепортов у всех сразу. */
    private static final List<ChestSwapGlide> watchers = new CopyOnWriteArrayList<>();

    private static boolean startAwaitsMove;

    /** Кто имеет право держать неотправленные подтверждения. */
    private static final AtomicReference<Object> clearHolder = new AtomicReference<>();

    /** Подтверждения, которые копятся до разрешения. */
    private static final ArrayDeque<Integer> heldPongs = new ArrayDeque<>();

    private static int lastPongSent = Integer.MIN_VALUE;

    private final Module owner;
    private final IntSupplier intervalTicks;
    private final Supplier<String> flightContext;

    private volatile int preTick;
    private int lastClientTick = -1;
    private boolean doubleTickWarned;

    private boolean engaged;
    private boolean finishing;

    private int burstTick = -100;
    private int outTick = -1;
    private int lastOutTick = -2;
    private boolean burstNow;
    private boolean burstWanted;
    private boolean jumpThisTick;
    private int conflictDefer;
    private String lastProblem;

    private final IntOpenHashSet attachedFireworks = new IntOpenHashSet();
    private boolean rocketBurstDue;

    private int clearRttTicks = CLEAR_RTT_DEFAULT;
    private int groundClearTicks;
    private boolean groundNoted;
    private int groundHandBacks;
    private String groundState = "unknown";

    private volatile boolean holdsClears;
    private volatile boolean holdActive;
    private volatile int lastPingId = NO_PING;
    private volatile int holdPingId = NO_PING;
    private boolean clearHeld;
    private boolean exposed;
    private int holdTicks;
    private int exposedTicks;

    private final ArrayDeque<PendingSwap> pendingSwaps = new ArrayDeque<>();
    private final ArrayDeque<PendingSwap> unappliedSwaps = new ArrayDeque<>();

    private int bursts;
    private int refused;
    private int released;
    private int exposures;
    private int maxHold;
    private int lostDurability;
    private int lastDamage = -1;

    private static final int SAMPLES = 12;

    private final long[] sampleGapMs = new long[SAMPLES];
    private final int[] sampleMoves = new int[SAMPLES];
    private final boolean[] sampleBurst = new boolean[SAMPLES];
    private int sampleHead;
    private long lastTickNanos;
    private int movesThisTick;
    private long maxTickGapMs;
    private int bunchedTicks;

    private volatile long lastPingNanos;
    private volatile long maxPingGapMs;
    private int teleports;
    private int grimSetbacks;

    private static boolean readingRawJump;

    public ChestSwapGlide(Module owner, IntSupplier intervalTicks) {
        this(owner, intervalTicks, () -> "");
    }

    public ChestSwapGlide(Module owner, IntSupplier intervalTicks, Supplier<String> flightContext) {
        this.owner = owner;
        this.intervalTicks = intervalTicks;
        this.flightContext = flightContext;

        watchers.add(this);
    }

    public boolean isEngaged() {
        return this.engaged;
    }

    /** Сервер сейчас считает, что игрок планирует. */
    public boolean isServerGliding() {
        return !this.engaged
            || this.burstNow
            || Minecraft.getInstance().player != null
                && isGlider(Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST));
    }

    public int bursts() {
        return this.bursts;
    }

    public static boolean gliderWorn() {
        return Minecraft.getInstance().player != null
            && isGlider(Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST));
    }

    /** Элитра где угодно на теле: надета, в руке или в хотбаре. */
    public static ItemStack gliderStack() {
        if (Minecraft.getInstance().player == null) return ItemStack.EMPTY;

        ItemStack chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
        if (isGlider(chest)) return chest;

        if (isGlider(Minecraft.getInstance().player.getOffhandItem())) return Minecraft.getInstance().player.getOffhandItem();
        if (isGlider(Minecraft.getInstance().player.getMainHandItem())) return Minecraft.getInstance().player.getMainHandItem();

        return ItemStack.EMPTY;
    }

    public static boolean gliderAtHand() {
        return Minecraft.getInstance().player != null
            && (gliderWorn() || gliderHand() != null || gliderHotbarSlot() >= 0);
    }

    /** Надевает элитру один раз, чтобы начать полёт. */
    public static boolean putGliderOn() {
        if (instance != null || Minecraft.getInstance().player == null || gliderWorn()) return false;

        InteractionHand hand = gliderHand();
        if (hand != null) return handUsable(hand) && useSwap(hand) >= 0;

        int slot = gliderHotbarSlot();
        if (slot < 0 || !handUsable(InteractionHand.MAIN_HAND)) return false;

        InvUtils.swap(slot, true);
        try {
            return useSwap(InteractionHand.MAIN_HAND) >= 0;
        } finally {
            InvUtils.swapBack();
        }
    }

    private static InteractionHand gliderHand() {
        for (InteractionHand hand : new InteractionHand[]{InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND}) {
            ItemStack held = Minecraft.getInstance().player.getItemInHand(hand);
            if (!isGlider(held) || !isChestEquippable(held)) continue;

            return hand;
        }

        return null;
    }

    private static int gliderHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(slot);
            if (!isGlider(stack) || !isChestEquippable(stack)) continue;

            return slot;
        }

        return -1;
    }

    public String stats() {
        return String.format(
            "%d bursts, %d swaps refused, %d clears let go, %d clears exposed, %d ground hand-backs, "
                + "%d durability lost, hold max %d",
            this.bursts, this.refused, this.released, this.exposures, this.groundHandBacks,
            this.lostDurability, this.maxHold);
    }

    public String telemetry() {
        String line = String.format(
            "ticks: max gap %d ms, %d bunched | pings: max gap %d ms | %d teleports (%d Grim setbacks)",
            this.maxTickGapMs, this.bunchedTicks, this.maxPingGapMs, this.teleports, this.grimSetbacks);

        this.maxTickGapMs = 0L;
        this.bunchedTicks = 0;
        this.maxPingGapMs = 0L;

        return line;
    }

    public void tick(boolean armed) {
        tick(armed, false);
    }

    /** Основной шаг автомата. */
    public void tick(boolean armed, boolean wantBurst) {
        int clientTick = TickPacer.clientTick();

        // модуль не должен вызываться дважды за тик: иначе логика врёт
        if (clientTick == this.lastClientTick) {
            if (!this.doubleTickWarned) {
                this.doubleTickWarned = true;
                this.owner.warning("Chest swap: %s ticks twice per client tick (its handlers are "
                    + "subscribed twice) - ignoring the second call.", this.owner.getName());
            }

            return;
        }

        this.lastClientTick = clientTick;

        boolean wireIdle = this.movesThisTick == 0;

        this.preTick++;
        sampleTick();

        this.burstNow = false;
        this.jumpThisTick = false;
        this.burstWanted = wantBurst;

        settleSwapVerdicts();

        if (Minecraft.getInstance().player == null) {
            if (this.engaged) disengage();
            this.attachedFireworks.clear();
            return;
        }

        boolean rocketGone = trackFireworks();

        if (armed) {
            armed = groundClear();
        } else {
            this.groundClearTicks = 0;
            this.groundNoted = false;
        }

        // держать клиры может только один: иначе два модуля перехватят друг друга
        if (armed && !this.engaged && (instance != null || clearHolder.get() != null)) armed = false;

        if (!armed) {
            if (!this.engaged) return;
            this.finishing = true;
        } else if (!this.engaged) {
            if (!engage()) return;
        } else {
            this.finishing = false;
        }

        // ракета сгорела, а клир так и лежит — надо взорвать на первом же тике
        if (rocketGone && this.holdActive && !this.rocketBurstDue) {
            this.rocketBurstDue = true;

            if (false) {
                this.owner.info("Chest swap: a rocket burnt out with a clear held - bursting at the "
                    + "first legal tick so Grim gets its pong.");
            }
        }

        watchDurability();

        // тик без пакетов движения: сервер не видел угла, шевелим пакет
        if (wireIdle) RotationUtils.getInstance().keepWireFresh();

        ItemStack chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
        InteractionHand hand = pairHand(chest);

        if (this.finishing) {
            finish(chest, hand);
        } else if (this.holdActive || this.exposed) {
            serviceHold(chest, hand);
        } else if (isGlider(chest)) {
            if (hand == null) {
                problem("Chest swap: hold a chestplate in a hand (off hand preferred) with the "
                    + "elytra worn - gliding on durability until it is.");
            } else if (EnchantmentHelper.has(
                chest, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                problem("Chest swap: the worn elytra has Curse of Binding and cannot be swapped - "
                    + "gliding on durability.");
            } else if (this.preTick - this.lastOutTick >= 2 && handUsable(hand) && sendSwap(hand, true)) {
                this.outTick = this.preTick;
                this.lastProblem = null;
            }
        } else if (hand == null) {
            problem("Chest swap: chest armour worn with no elytra in a hand - nothing to glide with.");
        } else if (this.outTick < 0 ? due() : this.preTick - this.outTick >= this.intervalTicks.getAsInt() + CLEAR_WAIT_TICKS) {
            if (this.outTick >= 0 && false) {
                this.owner.warning("Chest swap: no clear back %d ticks after a swap-out, restarting "
                    + "the glide anyway.", this.preTick - this.outTick);
            }

            burst(chest, hand, true);
        }

        this.holdsClears = this.engaged;
    }

    /** Сброс состояния — при выключении модуля или смене мира. */
    public void reset() {
        if (this.engaged && Minecraft.getInstance().player != null) {
            boolean handed;

            ItemStack chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
            InteractionHand hand = pairHand(chest);

            // если ещё летит — успеваем вернуть элитру
            handed = !Minecraft.getInstance().player.onGround()
                && Minecraft.getInstance().player.isFallFlying()
                && this.clearHeld
                && burst(chest, hand, false);

            if (!handed) {
                if (this.holdActive || this.clearHeld) release("the chest swap stopped");

                chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);

                if (!isGlider(chest) && hand != null && handUsable(hand)) sendSwap(hand, false);
            }
        }

        disengage();

        synchronized (this.pendingSwaps) {
            this.pendingSwaps.clear();
            this.unappliedSwaps.clear();
        }

        this.lastOutTick = -2;
        this.bursts = 0;
        this.refused = 0;
        this.released = 0;
        this.exposures = 0;
        this.maxHold = 0;
        this.lostDurability = 0;
        this.lastDamage = -1;
        this.lastProblem = null;
        this.teleports = 0;
        this.grimSetbacks = 0;
        this.maxTickGapMs = 0L;
        this.bunchedTicks = 0;
        this.maxPingGapMs = 0L;
    }

    private void sampleTick() {
        long now = System.nanoTime();

        long gapMs = this.lastTickNanos == 0L ? 50L : (now - this.lastTickNanos) / 1_000_000L;
        this.lastTickNanos = now;

        this.sampleGapMs[this.sampleHead] = gapMs;
        this.sampleMoves[this.sampleHead] = this.movesThisTick;
        this.sampleBurst[this.sampleHead] = this.burstNow;
        this.sampleHead = (this.sampleHead + 1) % SAMPLES;

        this.movesThisTick = 0;

        if (gapMs > this.maxTickGapMs) this.maxTickGapMs = gapMs;

        // тики короче 15 мс — клиент догоняет несколько тиков за один кадр
        if (gapMs < 15L) this.bunchedTicks++;
    }

    public void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.packet;
        if (!(packet instanceof ServerboundMovePlayerPacket move)) return;

        startAwaitsMove = false;

        if (move.hasPosition()) this.movesThisTick++;
    }

    /** Догоняет пакет движения с текущим углом после старта планирования. */
    public static void afterSendPosition() {
        if (!startAwaitsMove || Minecraft.getInstance().player == null
            || Minecraft.getInstance().getConnection() == null) {
            return;
        }

        startAwaitsMove = false;

        RotationUtils rot = RotationUtils.getInstance();

        Minecraft.getInstance().getConnection().send(new ServerboundMovePlayerPacket.Rot(
            rot.getSentYaw(), rot.getSentPitch(),
            Minecraft.getInstance().player.onGround(), Minecraft.getInstance().player.horizontalCollision));
    }

    /** Исчез ли один из наших фейерверков. */
    private boolean trackFireworks() {
        if (Minecraft.getInstance().level == null) {
            this.attachedFireworks.clear();
            return false;
        }

        List<FireworkRocketEntity> attached = Minecraft.getInstance().level.getEntitiesOfClass(
            FireworkRocketEntity.class, Minecraft.getInstance().player.getBoundingBox().inflate(2.0),
            rocket -> rocket.getOwner() == Minecraft.getInstance().player);

        IntOpenHashSet ids = new IntOpenHashSet(attached.size());
        for (FireworkRocketEntity rocket : attached) ids.add(rocket.getId());

        boolean gone = false;
        for (int id : this.attachedFireworks) {
            if (ids.contains(id)) continue;

            gone = true;
            break;
        }

        this.attachedFireworks.clear();
        this.attachedFireworks.addAll(ids);

        return gone;
    }

    private String recentTicks() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < SAMPLES; i++) {
            int idx = (this.sampleHead + i) % SAMPLES;

            if (i > 0) sb.append(' ');

            sb.append(this.sampleGapMs[idx]).append('/').append(this.sampleMoves[idx]);

            if (this.sampleBurst[idx]) sb.append('*');
        }

        return sb.toString();
    }

    private String swapState() {
        if (Minecraft.getInstance().player == null) return "no player";

        String worn = isGlider(Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST))
            ? "elytra worn"
            : "chest armour worn";

        if (!this.engaged) return "off, " + worn + ", ground " + this.groundState;

        String hold;
        if (this.holdActive) {
            hold = "clear held " + this.holdTicks + " ticks";
        } else if (this.clearHeld) {
            hold = "clear in hand";
        } else if (this.outTick >= 0) {
            hold = "swap-out unanswered " + (this.preTick - this.outTick) + " ticks";
        } else {
            hold = "no clear in flight";
        }

        return String.format(
            "engaged, %s, last burst %d ticks ago, %s, %d pongs held, ground %s%s%s",
            worn, this.preTick - this.burstTick, hold, heldPongs.size(), this.groundState,
            this.exposed ? ", exposed" : "", this.finishing ? ", handing back" : "");
    }

    /** Разбор телепорта: сервер откатил игрока — значит увидел не то состояние. */
    private void logTeleport(ClientboundPlayerPositionPacket packet) {
        this.teleports++;

        boolean grim = packet.id() < 0;
        if (grim) this.grimSetbacks++;

        if (!false || Minecraft.getInstance().player == null) return;

        Set<Relative> relatives = packet.relatives();
        Vec3 target = packet.change().position();

        double tx = relatives.contains(Relative.X) ? Minecraft.getInstance().player.getX() + target.x : target.x;
        double ty = relatives.contains(Relative.Y) ? Minecraft.getInstance().player.getY() + target.y : target.y;
        double tz = relatives.contains(Relative.Z) ? Minecraft.getInstance().player.getZ() + target.z : target.z;

        Vec3 back = Minecraft.getInstance().player.position().subtract(tx, ty, tz);
        Vec3 delta = packet.change().deltaMovement();

        String velocity;
        if (relatives.contains(Relative.DELTA_X) || relatives.contains(Relative.DELTA_Y)
            || relatives.contains(Relative.DELTA_Z)) {
            velocity = "kept";
        } else if (delta.lengthSqr() == 0.0) {
            velocity = "zeroed";
        } else {
            velocity = String.format("set to %.2f b/t", delta.length());
        }

        this.owner.warning(
            "Server teleport #%d (%s): %.1f blocks behind the client (dx %.1f, dy %.1f, dz %.1f), "
                + "velocity %s | %s | swap: %s | last ticks (gap ms/moves): %s | ping gap max %d ms",
            packet.id(),
            grim ? "Grim setback" : "vanilla: moved too quickly or wrongly",
            back.length(), back.x, back.y, back.z, velocity,
            this.flightContext.get(), swapState(), recentTicks(), this.maxPingGapMs);
    }

    private boolean engage() {
        if (!claimClearHold(this)) return false;

        this.engaged = true;
        instance = this;
        this.finishing = false;
        this.burstTick = this.preTick;
        this.outTick = -1;
        this.conflictDefer = 0;
        this.lastProblem = null;
        this.lastDamage = -1;

        return true;
    }

    private boolean due() {
        return this.burstWanted
            || this.rocketBurstDue
            || this.preTick - this.burstTick >= this.intervalTicks.getAsInt();
    }

    /** Обслуживание удержания: ждём окно, в котором можно взорвать. */
    private void serviceHold(ItemStack chest, InteractionHand hand) {
        this.holdTicks++;
        if (this.holdTicks > this.maxHold) this.maxHold = this.holdTicks;

        if (this.outTick >= 0) {
            this.clearRttTicks = Math.min(this.preTick - this.outTick, CLEAR_RTT_MAX);
        }

        this.outTick = -1;

        if (this.clearHeld && (this.exposed || due()) && burst(chest, hand, true)) return;

        if (this.exposed) {
            // сервер уже видел состояние без элитры: дальше ждать бессмысленно
            if (++this.exposedTicks > HOLD_GRACE_TICKS) release("no legal burst while the server already had the clear");
            return;
        }

        if (this.holdTicks > this.intervalTicks.getAsInt() + HOLD_GRACE_TICKS) {
            release(this.clearHeld
                ? "no legal burst inside " + this.holdTicks + " ticks"
                : "the clear never reached the main thread");
        }
    }

    /** Возврат обычного полёта с тратом прочности. */
    private void finish(ItemStack chest, InteractionHand hand) {
        if (Minecraft.getInstance().player.onGround() || !Minecraft.getInstance().player.isFallFlying()) {
            if (this.holdActive || this.clearHeld) release("the glide ended");

            if (!isGlider(chest) && hand != null && handUsable(hand)) sendSwap(hand, false);

            disengage();
            return;
        }

        if (this.holdActive || this.exposed) {
            this.holdTicks++;

            if (this.clearHeld && burst(chest, hand, false)) {
                disengage();
                return;
            }

            if (this.exposed ? ++this.exposedTicks > HOLD_GRACE_TICKS
                : this.holdTicks > this.intervalTicks.getAsInt() + HOLD_GRACE_TICKS) {
                release("no legal burst to hand the glide back");
                disengage();
            }

            return;
        }

        if (isGlider(chest) || hand == null) {
            disengage();
            return;
        }

        if (this.outTick >= 0 && this.preTick - this.outTick < this.intervalTicks.getAsInt() + CLEAR_WAIT_TICKS) {
            return;
        }

        if (burst(chest, hand, false)) disengage();
    }

    private void disengage() {
        this.engaged = false;

        if (instance == this) instance = null;

        this.finishing = false;
        this.holdsClears = false;
        this.holdActive = false;
        this.clearHeld = false;
        this.exposed = false;
        this.exposedTicks = 0;
        this.holdTicks = 0;
        this.outTick = -1;
        this.rocketBurstDue = false;
        this.groundClearTicks = 0;

        releaseClearHold(this);
    }

    /**
     * Взрыв: запустить планирование, пока элитра надета.
     *
     * @param swapOut сразу вернуть нагрудник, чтобы сервер не увидел элитру
     */
    private boolean burst(ItemStack chest, InteractionHand hand, boolean swapOut) {
        if (Minecraft.getInstance().getConnection() == null) return false;
        if (Minecraft.getInstance().player.onGround()) return false;

        // раньше двух тиков после прошлого взрыва нельзя
        if (this.preTick - this.burstTick < 2) return false;

        boolean gliderOn = isGlider(chest);

        if (!(gliderOn || hand != null && handUsable(hand) && canSwap(hand))) return false;

        // игрок в этом тике что-то нажал — сервер применит действие не к тому
        if (SwapManager.getInstance().isActionConflictTick() && this.conflictDefer < CONFLICT_DEFER_MAX) {
            this.conflictDefer++;
            return false;
        }

        this.conflictDefer = 0;

        int held = this.holdTicks;

        flushHeldPongs();
        this.holdActive = false;
        this.clearHeld = false;
        this.exposed = false;
        this.exposedTicks = 0;
        this.holdTicks = 0;

        if (!gliderOn && !sendSwap(hand, false) && false) {
            this.owner.warning("Chest swap: the elytra could not be swapped in ahead of a glide start.");
        }

        Minecraft.getInstance().getConnection().send(new ServerboundPlayerCommandPacket(
            Minecraft.getInstance().player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));

        startAwaitsMove = true;
        this.jumpThisTick = true;
        this.burstNow = true;
        this.burstTick = this.preTick;
        this.bursts++;

        this.rocketBurstDue = false;

        if (false && held > this.intervalTicks.getAsInt()) {
            this.owner.info("Chest swap: held the clear %d ticks before the burst.", held);
        }

        this.outTick = -1;

        if (swapOut) {
            InteractionHand outHand = pairHand(Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST));

            if (outHand != null && sendSwap(outHand, true)) this.outTick = this.preTick;
        }

        return true;
    }

    /** Отпустить клир: серверу нельзя держать нас без элитры. */
    private void release(String why) {
        this.released++;

        if (false) this.owner.warning("Chest swap: let a glide clear go - %s.", why);

        this.holdActive = false;
        this.holdTicks = 0;
        this.exposed = false;
        this.exposedTicks = 0;
        this.rocketBurstDue = false;

        flushHeldPongs();

        if (this.clearHeld) {
            this.clearHeld = false;

            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isFallFlying()) {
                Minecraft.getInstance().player.stopFallFlying();
            }
        }

        this.outTick = -1;
    }

    /** Следит за прочностью: любая потеря означает, что сервер увидел элитру. */
    private void watchDurability() {
        ItemStack elytra = gliderStack();
        if (elytra.isEmpty()) {
            this.lastDamage = -1;
            return;
        }

        int damage = elytra.getDamageValue();

        if (this.lastDamage >= 0 && damage > this.lastDamage) {
            this.lostDurability += damage - this.lastDamage;

            if (false) {
                String why;
                if (this.outTick >= 0) {
                    why = "a swap-out unanswered for " + (this.preTick - this.outTick) + " ticks";
                } else if (this.holdActive) {
                    why = "a clear held " + this.holdTicks + " ticks";
                } else {
                    why = "no swap in flight";
                }

                this.owner.warning(
                    "Chest swap: the elytra lost %d durability (%d/%d left) - %d ticks after the last "
                        + "burst, %s.",
                    damage - this.lastDamage, elytra.getMaxDamage() - damage, elytra.getMaxDamage(),
                    this.preTick - this.burstTick, why);
            }
        }

        this.lastDamage = damage;
    }

    /**
     * Хватает ли воздуха под ногами, чтобы сервер не дотянулся до земли.
     *
     * <p>Запас зависит от вертикальной скорости: чем быстрее падение, тем
     * дальше сервер проверяет землю.
     */
    private boolean groundClear() {
        double vy = Minecraft.getInstance().player.getDeltaMovement().y;

        double travel = vy < 0.0
            ? -vy * (this.clearRttTicks + 4)
            : vy * (this.clearRttTicks / 2.0 + 2);

        double need = GHOST_REACH + GHOST_MARGIN + travel;

        boolean arming = !this.engaged || this.finishing;
        if (arming) need += ARM_HYSTERESIS;

        boolean clear = airBelow(need);

        this.groundState = String.format("%s (%.1f needed)", clear ? "clear" : "within reach", need);

        if (this.engaged) {
            if (!clear && !this.finishing) {
                this.groundHandBacks++;

                if (false) {
                    this.owner.info("Chest swap: ground within the server's reach (%.1f blocks of air "
                        + "needed) - handing the glide back.", need);
                }
            }

            return clear;
        }

        this.groundClearTicks = clear ? this.groundClearTicks + 1 : 0;

        if (this.groundClearTicks >= ARM_SETTLE_TICKS) {
            this.groundNoted = false;
            return true;
        }

        if (!clear && !this.groundNoted) {
            this.groundNoted = true;

            if (false) {
                this.owner.info("Chest swap: %.1f blocks of air below needed before the first swap - "
                    + "gliding on the elytra until then.", need);
            }
        }

        return false;
    }

    private static boolean airBelow(double depth) {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.noBlockCollision(
            Minecraft.getInstance().player,
            Minecraft.getInstance().player.getBoundingBox().expandTowards(0.0, -depth, 0.0));
    }

    /** Сообщение показывается один раз, а не каждый тик. */
    private void problem(String message) {
        if (message.equals(this.lastProblem)) return;

        this.lastProblem = message;
        this.owner.warning(message);
    }

    public static boolean claimClearHold(Object holder) {
        return clearHolder.compareAndSet(null, holder) || clearHolder.get() == holder;
    }

    public static void releaseClearHold(Object holder) {
        if (clearHolder.get() != holder) return;

        flushHeldPongs();
        clearHolder.compareAndSet(holder, null);
    }

    public static boolean ownsClearHold(Object holder) {
        return clearHolder.get() == holder;
    }

    /** Отложить ответ на ping, пока держится клир. */
    public static boolean keepPong(Object holder, int id) {
        if (clearHolder.get() != holder) return false;

        heldPongs.addLast(id);
        return true;
    }

    /** Отправить всё накопленное. */
    public static void flushHeldPongs() {
        if (heldPongs.isEmpty()) return;

        if (Minecraft.getInstance().getConnection() == null) {
            heldPongs.clear();
            return;
        }

        while (!heldPongs.isEmpty()) {
            int id = heldPongs.pollFirst();

            Minecraft.getInstance().getConnection().send(new ServerboundPongPacket(id));
            lastPongSent = id;
        }
    }

    public static void onPongSent(int id) {
        flushHeldPongs();
        lastPongSent = id;
    }

    public static int lastPongSent() {
        return lastPongSent;
    }

    public static boolean onPingMainThread(int id) {
        ChestSwapGlide swap = instance;
        return swap != null && swap.holdPong(id);
    }

    private boolean holdPong(int id) {
        return this.holdActive && keepPong(this, id);
    }

    public static void beforeTeleportApplied(ClientboundPlayerPositionPacket packet) {
        for (ChestSwapGlide watcher : watchers) {
            if (!watcher.owner.isActive()) continue;

            watcher.logTeleport(packet);
        }

        ChestSwapGlide swap = instance;
        if (swap != null) swap.onTeleport();
    }

    /** Телепорт означает, что сервер увидел игрока без элитры. */
    private void onTeleport() {
        flushHeldPongs();

        if (!this.clearHeld || this.exposed) return;

        this.exposures++;
        this.holdActive = false;
        this.outTick = -1;
        this.exposed = true;
        this.exposedTicks = 0;

        if (false) {
            this.owner.info("Chest swap: a teleport let the held clear through - restarting the glide.");
        }
    }

    /** Сервер подтвердил смену слота — клир можно удерживать. */
    public static boolean onGlideClearApplied() {
        ChestSwapGlide swap = instance;
        if (swap == null || !swap.holdActive) return false;

        swap.clearHeld = true;

        // клир пришёл раньше собственного пинга — значит, сервер уже всё видел
        if (!swap.exposed && swap.holdPingId != NO_PING && swap.holdPingId == lastPongSent) {
            swap.exposures++;
            swap.exposed = true;
            swap.exposedTicks = 0;

            if (false) {
                swap.owner.info("Chest swap: the server's clear arrived behind its own pong - "
                    + "restarting the glide.");
            }
        }

        return true;
    }

    public static boolean holdsGlideClear() {
        ChestSwapGlide swap = instance;
        return swap != null && swap.holdActive;
    }

    /** Подмена состояния клавиши прыжка: сервер не должен видеть прыжок. */
    public static Boolean jumpKeyOverride() {
        if (readingRawJump) return null;

        ChestSwapGlide swap = instance;
        if (swap == null) return null;

        if (swap.jumpThisTick) return Boolean.TRUE;

        return swap.engaged ? Boolean.FALSE : null;
    }

    public static boolean rawJumpDown() {
        readingRawJump = true;

        try {
            return Minecraft.getInstance().options.keyJump.isDown();
        } finally {
            readingRawJump = false;
        }
    }

    public void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundPingPacket ping) {
            this.lastPingId = ping.getId();

            long now = System.nanoTime();
            if (this.lastPingNanos != 0L) {
                long gapMs = (now - this.lastPingNanos) / 1_000_000L;
                if (gapMs > this.maxPingGapMs) this.maxPingGapMs = gapMs;
            }

            this.lastPingNanos = now;
            return;
        }

        packet = event.packet;

        if (packet instanceof ClientboundSetEntityDataPacket data) {
            if (!this.holdsClears || Minecraft.getInstance().player == null) return;
            if (data.id() != Minecraft.getInstance().player.getId()) return;
            if (!Minecraft.getInstance().player.isFallFlying()) return;

            int flagsId = EntityAccessor.getSharedFlagsId().id();

            for (var entry : data.packedItems()) {
                if (entry.id() != flagsId) continue;
                if (!(entry.value() instanceof Byte flags)) continue;

                // бит 0x80 — флаг планирования
                if ((flags & 0x80) != 0) continue;

                this.holdPingId = this.lastPingId;
                this.holdActive = true;
                break;
            }

            return;
        }

        packet = event.packet;

        // подтверждение смены слота: не пропускаем, пока ждём применения
        if (packet instanceof ClientboundContainerSetContentPacket sync && sync.containerId() == 0) {
            synchronized (this.pendingSwaps) {
                if (!this.pendingSwaps.isEmpty()) {
                    this.pendingSwaps.pollFirst();

                    // следующая подмена в очереди — ответ пока преждевременный
                    if (!this.pendingSwaps.isEmpty()) event.cancel();
                }

                if (!event.isCancelled()) this.unappliedSwaps.clear();
            }

            return;
        }

        packet = event.packet;

        if (packet instanceof ClientboundBlockChangedAckPacket ack) {
            synchronized (this.pendingSwaps) {
                while (!this.pendingSwaps.isEmpty()
                    && this.pendingSwaps.peekFirst().sequence() <= ack.sequence()) {
                    this.unappliedSwaps.addLast(this.pendingSwaps.pollFirst());
                }
            }
        }
    }

    /** Смена брони и удерживаемого предмета местами одним использованием. */
    private boolean sendSwap(InteractionHand hand, boolean out) {
        int sequence = useSwap(hand);
        if (sequence < 0) return false;

        if (out) this.lastOutTick = this.preTick;

        synchronized (this.pendingSwaps) {
            this.pendingSwaps.addLast(new PendingSwap(this.preTick, hand, sequence));
        }

        return true;
    }

    /** Возвращает на место всё, что сервер не применил. */
    private void settleSwapVerdicts() {
        List<PendingSwap> undo;

        synchronized (this.pendingSwaps) {
            while (!this.pendingSwaps.isEmpty()
                && this.preTick - this.pendingSwaps.peekFirst().sentTick() > SWAP_FORGET) {
                this.pendingSwaps.pollFirst();
            }

            if (this.unappliedSwaps.isEmpty()) return;

            undo = new ArrayList<>(this.unappliedSwaps);
            this.unappliedSwaps.clear();
        }

        if (Minecraft.getInstance().player == null) return;

        for (PendingSwap swap : undo) {
            ItemStack chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack held = Minecraft.getInstance().player.getItemInHand(swap.hand());

            Minecraft.getInstance().player.setItemSlot(EquipmentSlot.CHEST, held);
            Minecraft.getInstance().player.setItemInHand(swap.hand(), chest);

            if (swap.sentTick() != this.outTick) continue;

            this.outTick = -1;
        }

        this.refused += undo.size();

        if (false) {
            this.owner.info("Chest swap: the server did not apply %d swap(s) - view put back.", undo.size());
        }
    }

    /** Рука, в которой лежит «вторая часть» пары: элитра или нагрудник. */
    private static InteractionHand pairHand(ItemStack chest) {
        boolean gliderOn = isGlider(chest);
        if (!gliderOn && !isChestArmor(chest)) return null;

        for (InteractionHand hand : new InteractionHand[]{InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND}) {
            ItemStack held = Minecraft.getInstance().player.getItemInHand(hand);

            if (gliderOn) {
                if (isChestArmor(held)) return hand;
            } else if (isGlider(held) && isChestEquippable(held)) {
                return hand;
            }
        }

        return null;
    }

    /** Главная рука свободна только если сервер видит тот же слот. */
    private static boolean handUsable(InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) return true;

        int server = SwapManager.getInstance().getServerSlot();
        return server < 0 || server == Minecraft.getInstance().player.getInventory().getSelectedSlot();
    }

    private static boolean isGlider(ItemStack stack) {
        return stack.has(DataComponents.GLIDER);
    }

    private static boolean isChestEquippable(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

        return equippable != null && equippable.swappable() && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static boolean isChestArmor(ItemStack stack) {
        return isChestEquippable(stack) && !isGlider(stack);
    }

    /** Можно ли сейчас поменять слот: рука свободна, броня не защищена, вещи разные. */
    private static boolean canSwap(InteractionHand hand) {
        ItemStack held = Minecraft.getInstance().player.getItemInHand(hand);
        ItemStack chest = Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.CHEST);

        return isChestEquippable(held)
            && held.getCount() <= 1
            && !EnchantmentHelper.has(
                chest, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
            && !ItemStack.isSameItemSameComponents(held, chest);
    }

    /**
     * Использование предмета для смены брони.
     *
     * <p>Пакет уходит с текущим серверным углом, иначе сервер применит смену
     * под другим углом и заметит подмену.
     *
     * @return sequence отправки или -1, если смена невозможна
     */
    private static int useSwap(InteractionHand hand) {
        if (Minecraft.getInstance().level == null) return -1;
        if (Minecraft.getInstance().getConnection() == null) return -1;
        if (!canSwap(hand)) return -1;

        ItemStack stack = Minecraft.getInstance().player.getItemInHand(hand);
        RotationUtils rot = RotationUtils.getInstance();

        int sequence = GrimUtils.nextSequence();
        Minecraft.getInstance().getConnection().send(new ServerboundUseItemPacket(
            hand, sequence, rot.getSentYaw(), rot.getSentPitch()));

        InteractionResult result = stack.use(Minecraft.getInstance().level, Minecraft.getInstance().player, hand);

        ItemStack held;
        if (result instanceof InteractionResult.Success success && success.heldItemTransformedTo() != null) {
            held = success.heldItemTransformedTo();
        } else {
            held = Minecraft.getInstance().player.getItemInHand(hand);
        }

        if (held != stack) Minecraft.getInstance().player.setItemInHand(hand, held);

        return result.consumesAction() ? sequence : -1;
    }

    /** Отложенная смена брони, ждущая подтверждения сервера. */
    private record PendingSwap(int sentTick, InteractionHand hand, int sequence) {
    }
}