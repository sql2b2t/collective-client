package me.collective.util;

import me.collective.util.SwapManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.MinecraftClient;
import me.collective.event.events.PacketEvent;
import me.collective.module.Module;
import me.collective.util.InvUtils;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerActionResponseS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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

    private static boolean startAwaitsMove;

    /** Кто имеет право держать неотправленные подтверждения. */
    private static final AtomicReference<Object> clearHolder = new AtomicReference<>();

    /** Подтверждения, которые копятся до разрешения. */


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
    /** Пишется из netty-обработчика понга (clearHeld = true) и из тика (сбросы). */
    private volatile boolean clearHeld;
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

    }

    public boolean isEngaged() {
        return this.engaged;
    }

    /** Сервер сейчас считает, что игрок планирует. */
    public boolean isServerGliding() {
        return !this.engaged
            || this.burstNow
            || MinecraftClient.getInstance().player != null
                && isGlider(MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST));
    }

    public int bursts() {
        return this.bursts;
    }

    public static boolean gliderWorn() {
        return MinecraftClient.getInstance().player != null
            && isGlider(MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST));
    }

    /** Элитра где угодно на теле: надета, в руке или в хотбаре. */
    public static ItemStack gliderStack() {
        if (MinecraftClient.getInstance().player == null) return ItemStack.EMPTY;

        ItemStack chest = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST);
        if (isGlider(chest)) return chest;

        if (isGlider(MinecraftClient.getInstance().player.getOffHandStack())) return MinecraftClient.getInstance().player.getOffHandStack();
        if (isGlider(MinecraftClient.getInstance().player.getMainHandStack())) return MinecraftClient.getInstance().player.getMainHandStack();

        return ItemStack.EMPTY;
    }

    public static boolean gliderAtHand() {
        return MinecraftClient.getInstance().player != null
            && (gliderWorn() || gliderHand() != null || gliderHotbarSlot() >= 0);
    }

    /** Надевает элитру один раз, чтобы начать полёт. */
    public static boolean putGliderOn() {
        if (instance != null || MinecraftClient.getInstance().player == null || gliderWorn()) return false;

        Hand hand = gliderHand();
        if (hand != null) return handUsable(hand) && useSwap(hand) >= 0;

        int slot = gliderHotbarSlot();
        if (slot < 0 || !handUsable(Hand.MAIN_HAND)) return false;

        InvUtils.swap(slot, true);
        try {
            return useSwap(Hand.MAIN_HAND) >= 0;
        } finally {
            InvUtils.swapBack();
        }
    }

    private static Hand gliderHand() {
        for (Hand hand : new Hand[]{Hand.OFF_HAND, Hand.MAIN_HAND}) {
            ItemStack held = MinecraftClient.getInstance().player.getStackInHand(hand);
            if (!isGlider(held) || !isChestEquippable(held)) continue;

            return hand;
        }

        return null;
    }

    private static int gliderHotbarSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = MinecraftClient.getInstance().player.getInventory().getStack(slot);
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

        if (MinecraftClient.getInstance().player == null) {
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
        }

        watchDurability();

        // тик без пакетов движения: сервер не видел угла, шевелим пакет
        if (wireIdle) RotationUtils.getInstance().keepWireFresh();

        ItemStack chest = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST);
        Hand hand = pairHand(chest);

        if (this.finishing) {
            finish(chest, hand);
        } else if (this.holdActive || this.exposed) {
            serviceHold(chest, hand);
        } else if (isGlider(chest)) {
            if (hand == null) {
                problem("Chest swap: hold a chestplate in a hand (off hand preferred) with the "
                    + "elytra worn - gliding on durability until it is.");
            } else if (EnchantmentHelper.hasAnyEnchantmentsWith(
                chest, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) {
                problem("Chest swap: the worn elytra has Curse of Binding and cannot be swapped - "
                    + "gliding on durability.");
            } else if (this.preTick - this.lastOutTick >= 2 && handUsable(hand) && sendSwap(hand, true)) {
                this.outTick = this.preTick;
                this.lastProblem = null;
            }
        } else if (hand == null) {
            problem("Chest swap: chest armour worn with no elytra in a hand - nothing to glide with.");
        } else if (this.outTick < 0 ? due() : this.preTick - this.outTick >= this.intervalTicks.getAsInt() + CLEAR_WAIT_TICKS) {

            burst(chest, hand, true);
        }

        this.holdsClears = this.engaged;
    }

    /** Сброс состояния — при выключении модуля или смене мира. */
    public void reset() {
        if (this.engaged && MinecraftClient.getInstance().player != null) {
            boolean handed;

            ItemStack chest = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST);
            Hand hand = pairHand(chest);

            // если ещё летит — успеваем вернуть элитру
            handed = !MinecraftClient.getInstance().player.isOnGround()
                && MinecraftClient.getInstance().player.isGliding()
                && this.clearHeld
                && burst(chest, hand, false);

            if (!handed) {
                if (this.holdActive || this.clearHeld) release("the chest swap stopped");

                chest = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST);

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
        if (!(packet instanceof PlayerMoveC2SPacket move)) return;

        startAwaitsMove = false;

        if (move.changesPosition()) this.movesThisTick++;
    }

    /** Догоняет пакет движения с текущим углом после старта планирования. */

    /** Исчез ли один из наших фейерверков. */
    private boolean trackFireworks() {
        if (MinecraftClient.getInstance().world == null) {
            this.attachedFireworks.clear();
            return false;
        }

        List<FireworkRocketEntity> attached = MinecraftClient.getInstance().world.getEntitiesByClass(
            FireworkRocketEntity.class, MinecraftClient.getInstance().player.getBoundingBox().expand(2.0),
            rocket -> rocket.shooter == MinecraftClient.getInstance().player);

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
        if (MinecraftClient.getInstance().player == null) return "no player";

        String worn = isGlider(MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST))
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
            "engaged, %s, last burst %d ticks ago, %s, ground %s%s%s",
            worn, this.preTick - this.burstTick, hold, this.groundState,
            this.exposed ? ", exposed" : "", this.finishing ? ", handing back" : "");
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
    private void serviceHold(ItemStack chest, Hand hand) {
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
    private void finish(ItemStack chest, Hand hand) {
        if (MinecraftClient.getInstance().player.isOnGround() || !MinecraftClient.getInstance().player.isGliding()) {
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
    private boolean burst(ItemStack chest, Hand hand, boolean swapOut) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return false;
        if (MinecraftClient.getInstance().player.isOnGround()) return false;

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

        this.holdActive = false;
        this.clearHeld = false;
        this.exposed = false;
        this.exposedTicks = 0;
        this.holdTicks = 0;

        if (!gliderOn) sendSwap(hand, false);

        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
            MinecraftClient.getInstance().player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        startAwaitsMove = true;
        this.jumpThisTick = true;
        this.burstNow = true;
        this.burstTick = this.preTick;
        this.bursts++;

        this.rocketBurstDue = false;

        this.outTick = -1;

        if (swapOut) {
            Hand outHand = pairHand(MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST));

            if (outHand != null && sendSwap(outHand, true)) this.outTick = this.preTick;
        }

        return true;
    }

    /** Отпустить клир: серверу нельзя держать нас без элитры. */
    private void release(String why) {
        this.released++;

        this.holdActive = false;
        this.holdTicks = 0;
        this.exposed = false;
        this.exposedTicks = 0;
        this.rocketBurstDue = false;


        if (this.clearHeld) {
            this.clearHeld = false;

            if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isGliding()) {
                MinecraftClient.getInstance().player.stopGliding();
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

        int damage = elytra.getDamage();

        if (this.lastDamage >= 0 && damage > this.lastDamage) {
            this.lostDurability += damage - this.lastDamage;
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
        double vy = MinecraftClient.getInstance().player.getVelocity().y;

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
            }

            return clear;
        }

        this.groundClearTicks = clear ? this.groundClearTicks + 1 : 0;

        if (this.groundClearTicks >= ARM_SETTLE_TICKS) {
            this.groundNoted = false;
            return true;
        }

        if (!clear) this.groundNoted = true;

        return false;
    }

    private static boolean airBelow(double depth) {
        return MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().world.isBlockSpaceEmpty(
            MinecraftClient.getInstance().player,
            MinecraftClient.getInstance().player.getBoundingBox().stretch(0.0, -depth, 0.0));
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

        clearHolder.compareAndSet(holder, null);
    }

    public static boolean ownsClearHold(Object holder) {
        return clearHolder.get() == holder;
    }

    public static boolean rawJumpDown() {
        readingRawJump = true;

        try {
            return MinecraftClient.getInstance().options.jumpKey.isPressed();
        } finally {
            readingRawJump = false;
        }
    }

    public void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;

        if (packet instanceof CommonPingS2CPacket ping) {
            this.lastPingId = ping.getParameter();

            long now = System.nanoTime();
            if (this.lastPingNanos != 0L) {
                long gapMs = (now - this.lastPingNanos) / 1_000_000L;
                if (gapMs > this.maxPingGapMs) this.maxPingGapMs = gapMs;
            }

            this.lastPingNanos = now;
            return;
        }

        packet = event.packet;

        if (packet instanceof EntityTrackerUpdateS2CPacket data) {
            if (!this.holdsClears || MinecraftClient.getInstance().player == null) return;
            if (data.id() != MinecraftClient.getInstance().player.getId()) return;
            if (!MinecraftClient.getInstance().player.isGliding()) return;

            int flagsId = net.minecraft.entity.Entity.FLAGS.id();

            for (var entry : data.trackedValues()) {
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
        if (packet instanceof InventoryS2CPacket sync && sync.syncId() == 0) {
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

        if (packet instanceof PlayerActionResponseS2CPacket ack) {
            synchronized (this.pendingSwaps) {
                while (!this.pendingSwaps.isEmpty()
                    && this.pendingSwaps.peekFirst().sequence() <= ack.sequence()) {
                    this.unappliedSwaps.addLast(this.pendingSwaps.pollFirst());
                }
            }
        }
    }

    /** Смена брони и удерживаемого предмета местами одним использованием. */
    private boolean sendSwap(Hand hand, boolean out) {
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

        if (MinecraftClient.getInstance().player == null) return;

        for (PendingSwap swap : undo) {
            ItemStack chest = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST);
            ItemStack held = MinecraftClient.getInstance().player.getStackInHand(swap.hand());

            MinecraftClient.getInstance().player.equipStack(EquipmentSlot.CHEST, held);
            MinecraftClient.getInstance().player.setStackInHand(swap.hand(), chest);

            if (swap.sentTick() != this.outTick) continue;

            this.outTick = -1;
        }

        this.refused += undo.size();
    }

    /** Рука, в которой лежит «вторая часть» пары: элитра или нагрудник. */
    private static Hand pairHand(ItemStack chest) {
        boolean gliderOn = isGlider(chest);
        if (!gliderOn && !isChestArmor(chest)) return null;

        for (Hand hand : new Hand[]{Hand.OFF_HAND, Hand.MAIN_HAND}) {
            ItemStack held = MinecraftClient.getInstance().player.getStackInHand(hand);

            if (gliderOn) {
                if (isChestArmor(held)) return hand;
            } else if (isGlider(held) && isChestEquippable(held)) {
                return hand;
            }
        }

        return null;
    }

    /** Главная рука свободна только если сервер видит тот же слот. */
    private static boolean handUsable(Hand hand) {
        if (hand == Hand.OFF_HAND) return true;

        int server = SwapManager.getInstance().getServerSlot();
        return server < 0 || server == MinecraftClient.getInstance().player.getInventory().selectedSlot;
    }

    private static boolean isGlider(ItemStack stack) {
        return stack.contains(DataComponentTypes.GLIDER);
    }

    private static boolean isChestEquippable(ItemStack stack) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);

        return equippable != null && equippable.swappable() && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static boolean isChestArmor(ItemStack stack) {
        return isChestEquippable(stack) && !isGlider(stack);
    }

    /** Можно ли сейчас поменять слот: рука свободна, броня не защищена, вещи разные. */
    private static boolean canSwap(Hand hand) {
        ItemStack held = MinecraftClient.getInstance().player.getStackInHand(hand);
        ItemStack chest = MinecraftClient.getInstance().player.getEquippedStack(EquipmentSlot.CHEST);

        return isChestEquippable(held)
            && held.getCount() <= 1
            && !EnchantmentHelper.hasAnyEnchantmentsWith(
                chest, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)
            && !ItemStack.areItemsAndComponentsEqual(held, chest);
    }

    /**
     * Использование предмета для смены брони.
     *
     * <p>Пакет уходит с текущим серверным углом, иначе сервер применит смену
     * под другим углом и заметит подмену.
     *
     * @return sequence отправки или -1, если смена невозможна
     */
    private static int useSwap(Hand hand) {
        if (MinecraftClient.getInstance().world == null) return -1;
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return -1;
        if (!canSwap(hand)) return -1;

        ItemStack stack = MinecraftClient.getInstance().player.getStackInHand(hand);
        RotationUtils rot = RotationUtils.getInstance();

        int sequence;

        try (PendingUpdateManager prediction =
                 MinecraftClient.getInstance().world.getPendingUpdateManager().incrementSequence()) {
            sequence = prediction.getSequence();

            MinecraftClient.getInstance().getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
                hand, sequence, rot.getSentYaw(), rot.getSentPitch()));
        }

        ActionResult result = stack.use(MinecraftClient.getInstance().world, MinecraftClient.getInstance().player, hand);

        ItemStack held;
        if (result instanceof ActionResult.Success success && success.getNewHandStack() != null) {
            held = success.getNewHandStack();
        } else {
            held = MinecraftClient.getInstance().player.getStackInHand(hand);
        }

        if (held != stack) MinecraftClient.getInstance().player.setStackInHand(hand, held);

        return result.isAccepted() ? sequence : -1;
    }

    /** Отложенная смена брони, ждущая подтверждения сервера. */
    private record PendingSwap(int sentTick, Hand hand, int sequence) {
    }
}