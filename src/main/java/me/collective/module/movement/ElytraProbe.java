package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ресёрч-модуль: проверяет вживую дыры Grim, найденные при разборе исходников.
 *
 * <p>Что именно тестирует (по пунктам аудита Grim master):
 *
 * <ul>
 *   <li><b>Находка A — повозочная снисходительность</b> ({@code UncertaintyHandler}.
 *       {@code reduceOffset}): когда в траекторию скольжения (bbox, вытянутый на
 *       скорость и расширенный на 1.0) попадает живая лодка или шалкер, Grim три
 *       тика прощает до 1.2 блока отклонения за тик. Индикатор показывает, когда
 *       «лаз» открыт — например, при эскорте напарником на лодке.</li>
 *   <li><b>Находка B — 0.03-окно</b>: при почти нулевой скорости предсказание
 *       разрешает скип тика, и порог резкости растёт до 0.03. Индикатор отмечает
 *       тики, где окно правдоподобно.</li>
 *   <li><b>Находка C — пропуски тиков</b>: раз в {@code SkipEvery} тиков модуль
 *       отменяет позиционные пакеты на {@code SkipFor} тиков подряд. Грим считает
 *       скипы «нетаймовыми» ({@code TimerCheck} их не видит), но предсказание
 *       копит расхождение — смотрите, на каком бурсте приходит откат.</li>
 *   <li><b>Логгер откатов</b>: каждый {@code PlayerPositionLookS2CPacket} логируется
 *       с расстоянием, скоростью и состоянием скипов в момент телепорта.</li>
 *   <li><b>GlideClaim</b> (осторожно, выключен по умолчанию): после отката шлёт
 *       повторный {@code START_FALL_FLYING} — проверка на дубликат из BadPacketsJ.</li>
 * </ul>
 *
 * <p>Модуль телеметрический: сам по себе ничего не «байпасит», его задача —
 * собрать статистику о том, какие из находок живы на конкретном сервере Grim.
 */
public class ElytraProbe extends Module {

    public final BooleanSetting skipTicks = add(new BooleanSetting("SkipTicks", false));
    public final NumberSetting skipEvery = add(new NumberSetting("SkipEvery", 20, 5, 100, 1,
        (v) -> skipTicks.getValue()));
    public final NumberSetting skipFor = add(new NumberSetting("SkipFor", 3, 1, 20, 1,
        (v) -> skipTicks.getValue()));
    public final BooleanSetting reportSetbacks = add(new BooleanSetting("ReportSetbacks", true));
    public final BooleanSetting verbose = add(new BooleanSetting("Verbose", true));
    public final BooleanSetting boatProbe = add(new BooleanSetting("BoatProbe", true));
    public final BooleanSetting glideClaim = add(new BooleanSetting("GlideClaim", false));

    // ── Машина фаз скипов (только главный поток) ──
    private int phaseTick;
    private int bursts;
    private int burstStartSkipped;
    private int setbacks;
    private final AtomicInteger skippedPackets = new AtomicInteger();

    /** Читается в пакетном хендлере (не всегда главный поток) — volatile. */
    private volatile boolean skipActive;

    /** Заявить скольжение повторно после отката (на главный поток из mc.execute). */
    private boolean claimPending;

    // ── Индикаторы (главный поток, читаются getInfoString из GUI) ──
    private boolean boatNear;
    private boolean windowLikely;

    public ElytraProbe() {
        super("ElytraProbe", "Ресёрч Grim на элитре: 0.03-окно, повозочная снисходительность, "
            + "пропуски тиков и разбор откатов.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        this.phaseTick = 0;
        this.bursts = 0;
        this.burstStartSkipped = 0;
        this.setbacks = 0;
        this.skippedPackets.set(0);
        this.skipActive = false;
        this.claimPending = false;
        this.boatNear = false;
        this.windowLikely = false;

        ControlFly fly = ControlFly.INSTANCE;
        if (fly != null && fly.isActive()) {
            ChatHelper.sendMessage("ElytraProbe: ControlFly тоже активен — обе системы режут одни "
                + "и те же move-пакеты. Выключите что-то одно.");
        }

        if (this.skipTicks.getValue()) {
            ChatHelper.sendMessage("ElytraProbe: скип тиков включён. На современном Grim без "
                + "0.03-окна это закончится откатами — именно их и логируем.");
        }
    }

    @Override
    public void onDisable() {
        if (this.bursts > 0 || this.skippedPackets.get() > 0 || this.setbacks > 0) {
            ChatHelper.sendMessage(String.format("ElytraProbe: итог — бурстов %d, отменено пакетов %d, "
                + "откатов %d.", this.bursts, this.skippedPackets.get(), this.setbacks));
        }

        this.skipActive = false;
        this.claimPending = false;
        this.boatNear = false;
        this.windowLikely = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            this.skipActive = false;
            return;
        }

        tickSkipPhase();

        tickClaim();

        // ── Индикаторы ──
        if (!mc.player.isGliding()) {
            this.boatNear = false;
            this.windowLikely = false;
            return;
        }

        Vec3 velocity = mc.player.getVelocity();
        this.windowLikely = velocity.length() < 0.03;

        if (this.boatProbe.getValue()) {
            this.boatNear = isVehicleLenienceOpen(velocity);
        }
    }

    /** Окно скипа: последние SkipFor тиков каждого цикла SkipEvery. */
    private void tickSkipPhase() {
        if (!this.skipTicks.getValue()) {
            if (this.skipActive) this.burstEnded();
            this.skipActive = false;
            return;
        }

        int total = Math.max(2, this.skipEvery.getIntValue());
        int activeFor = Math.min(Math.max(1, this.skipFor.getIntValue()), total - 1);

        this.phaseTick++;

        boolean wasActive = this.skipActive;
        this.skipActive = (this.phaseTick % total) >= (total - activeFor);

        if (this.skipActive && !wasActive) {
            this.bursts++;
            this.burstStartSkipped = this.skippedPackets.get();
            if (this.verbose.getValue()) {
                ChatHelper.sendMessage(String.format("ElytraProbe: бурст #%d — окно скипа %d/%d тиков.",
                    this.bursts, activeFor, total));
            }
        } else if (!this.skipActive && wasActive) {
            this.burstEnded();
        }
    }

    private void burstEnded() {
        int skipped = this.skippedPackets.get() - this.burstStartSkipped;
        if (this.verbose.getValue() && skipped > 0) {
            ChatHelper.sendMessage(String.format("ElytraProbe: бурст #%d отработал — отменено %d пакетов.",
                this.bursts, skipped));
        }
    }

    /** Заявка скольжения после отката (ресёрч на BadPacketsJ). */
    private void tickClaim() {
        if (!this.claimPending) return;

        this.claimPending = false;

        if (!this.glideClaim.getValue()) return;
        if (mc.player == null || !mc.player.isGliding() || mc.getNetworkHandler() == null) return;

        // Сервер только что прислал телепорт и, скорее всего, всё ещё считает нас
        // летящими: дубликат START_FALL_FLYING должен поймать BadPacketsJ — это и есть
        // предмет проверки.
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
            mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        if (this.verbose.getValue()) {
            ChatHelper.sendMessage("ElytraProbe: отослал повторный claim скольжения после отката "
                + "(смотрите на BadPacketsJ / откат).");
        }
    }

    /**
     * Находка A: открыта ли «повозочная» снисходительность.
     *
     * <p>Grim ({@code UncertaintyHandler.checkForHardCollision}) тянет бокс игрока на
     * скорость (эндпоинт) и расширяет на 1.0; попадание в него живой лодки или шалкера
     * держит {@code lastHardCollidingLerpingEntity} три тика. Клиент делает ту же
     * геометрию по своим данным — индикатор приблизителен.
     */
    private boolean isVehicleLenienceOpen(Vec3 velocity) {
        if (mc.world == null || mc.player == null) return false;

        AABB path = mc.player.getBoundingBox().stretch(velocity).expand(1.0);

        for (Entity entity : mc.world.getOtherEntities(mc.player, path.expand(4.0),
            e -> (e instanceof AbstractBoatEntity || e instanceof ShulkerEntity) && e.isAlive())) {
            if (entity.getBoundingBox().intersects(path)) return true;
        }

        return false;
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket move)) return;
        if (!move.changesPosition()) return;
        if (mc.player == null || !mc.player.isGliding() || mc.player.hasVehicle()) return;

        // Не спорим с ControlFly: своего FREEZE-паркинга у него свои отмены.
        ControlFly fly = ControlFly.INSTANCE;
        if (fly != null && fly.isActive()) return;

        if (!this.skipActive) return;

        event.cancel();
        this.skippedPackets.incrementAndGet();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!this.reportSetbacks.getValue() || mc.player == null) return;

        if (!(event.getPacket() instanceof PlayerPositionLookS2CPacket positionLook)) return;

        // Считаем на netty-треде, чат трогаем через mc.execute (как в GrimGlide).
        Set<PositionFlag> relatives = positionLook.relatives();
        Vec3 change = positionLook.change().position();
        Vec3 current = mc.player.getEntityPos();
        Vec3 target = new Vec3(
            relatives.contains(PositionFlag.X) ? current.x + change.x : change.x,
            relatives.contains(PositionFlag.Y) ? current.y + change.y : change.y,
            relatives.contains(PositionFlag.Z) ? current.z + change.z : change.z);

        double distance = target.distanceTo(current);
        Vec3 velocity = mc.player.getVelocity();
        boolean skipNow = this.skipActive;
        int skipped = this.skippedPackets.get();
        boolean glide = mc.player.isGliding();

        mc.execute(() -> {
            this.setbacks++;
            this.claimPending = this.glideClaim.getValue() && glide;

            ChatHelper.sendMessage(String.format(
                "ElytraProbe: откат #%d — %.2f б, скорость %.1f б/с, глид=%b, скип=%s (всего %d).",
                this.setbacks, distance, velocity.horizontalLength() * 20.0, glide,
                skipNow ? "АКТИВЕН" : "нет", skipped));
        });
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;

        return String.format("STR %d | SKIP %d%s%s%s",
            this.setbacks, this.skippedPackets.get(),
            this.skipActive ? " | skip!" : "",
            this.boatNear ? " | LAS" : "",
            this.windowLikely ? " | 0.03?" : "");
    }
}