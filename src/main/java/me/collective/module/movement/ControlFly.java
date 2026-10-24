package me.collective.module.movement;

import me.collective.module.movement.RocketBoost;
import me.collective.module.player.SpeedMine;

import me.collective.setting.Setting;
import me.collective.setting.SettingGroup;
import me.collective.setting.BoolSetting;
import me.collective.setting.DoubleSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.IntSetting;

import me.collective.util.ChestSwapGlide;
import me.collective.util.GrimUtils;
import me.collective.util.PorpoiseSolver;
import me.collective.util.RideWindow;
import me.collective.util.RotationUtils;
import me.collective.util.TickPacer;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.render.Freecam;
import me.collective.module.Module;
import me.collective.category.Category;
import me.collective.module.ModuleManager;
import me.collective.util.InvUtils;
import me.collective.event.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Управление полётом на элитре по WASD, совместимое с Grim.
 *
 * <p>Перенос {@code bep.hax.modules.ControlFly} из BepHax Community (GPL-3.0).
 *
 * <p>Что перенесено:
 *
 * <ul>
 *   <li><b>два режима зависания</b>: FREEZE тормозит обычной физикой, а потом
 *       «прибивает» клиента к последней позиции, которую слышал сервер, и держит
 *       её; FLIP_FLOP удерживает высоту раскачкой по yaw;
 *   <li><b>перезапуск фейерверков по окну</b>: время жизни ракеты бросается на
 *       сервере и клиенту не присылается, поэтому следующая запускается, когда
 *       текущая дожила до нижней границы розыгрыша, минус пинг и запас;
 *   <li><b>пике</b> через {@link PorpoiseSolver}: угол наклона ведётся вместо
 *       плоского курса, потому что набор высоты планера платит на любом курсе;
 *   <li><b>обмен с ChestSwapGlide</b> — полёт без траты прочности.
 * </ul>
 *
 * <p>Основано на BepHax Community, авторы BepHax contributors, GPL-3.0.
 */
public class ControlFly extends Module {

    public static ControlFly INSTANCE;

    /** Скорость, на которой сервер перестаёт ускорять. */
    private static final double FIREWORK_TERMINAL_SPEED = 1.7;

    private static final int POSITION_REMINDER_TICKS = 20;
    private static final int ROT_ONLY_GUARD = 18;

    private static final double DEFAULT_GRAVITY = 0.08;
    private static final double GHOST_CLEARANCE = 4.0;

    private static final int WINDOW_GRACE = 10;
    private static final int DEFAULT_PING_MS = 200;
    private static final double LEG_OVERSHOOT_D = 3.0;
    private static final double MIN_LEG_LOOKAHEAD = 12.0;
    private static final double MAX_LEG_LOOKAHEAD = 128.0;

    private final SettingGroup sgGeneral;
    private final SettingGroup sgFirework;
    private final SettingGroup sgChestSwap;

    private final Setting<Boolean> autoFirework;
    private final Setting<HoverMode> hoverMode;
    private final Setting<Double> hoverPitch;
    private final Setting<Double> upAngle;
    private final Setting<Double> downAngle;
    private final Setting<Boolean> smoothCamera;
    private final Setting<Boolean> hideRocketParticles;

    private final Setting<Refire> refire;
    private final Setting<Double> fireworkDelay;
    private final Setting<Boolean> durationScaling;
    private final Setting<Double> safetyMargin;

    private final Setting<Boolean> useBoost;
    private final Setting<Boolean> porpoise;
    private final Setting<Integer> porpoiseHeight;
    private final Setting<Boolean> asymmetricLegs;
    private final Setting<Boolean> pumpLegs;

    private final Setting<Boolean> chestSwap;
    private final Setting<Integer> swapInterval;

    private final ChestSwapGlide glideSwap;

    private int debugTicks;

    private ElytraBounce elytraBounce;

    /** Раскачка курса во FLIP_FLOP: даже или нечётное число переворотов. */
    private boolean flipFlop;

    private double nextFireworkDelay;
    private long lastFireworkTime;
    private int previousSlot;
    private int swapBackTicks;

    // ── Парковка FREEZE ──
    // Часть этих полей читается в пакетных хендлерах (PacketEvent.Send может идти
    // не из игрового потока), поэтому всё, что видно с обеих сторон, volatile.
    private volatile boolean frozen;
    private volatile Vec3 wirePos;
    private volatile int pinArmTick;
    private volatile int keepaliveTicks;
    private volatile int lastWirePosTick;
    private volatile int rotOnlyRun;
    private volatile boolean stepDue;
    private volatile Vec3 pendingStep;
    private volatile boolean parkedWithRocket;
    private String parkBlocked;

    private final AtomicInteger passMovePackets = new AtomicInteger();
    private volatile boolean reanchor;

    private int recentSetbacks;
    private int ticksSinceSetback;
    private int reparkCooldown;

    private boolean boostToggledByUs;

    // ── Пике ──
    private boolean porpoising;
    private boolean porpoiseDescending;
    private int porpoiseFlipTicks;
    private int porpoiseLegTicks;
    private double porpoiseTarget;

    private int windowGraceTicks;
    private int clickBlockedTicks;
    private int ticksSinceFirework;

    // ── Сглаживание камеры ──
    // idleHovering читается и из пакетных хендлеров (parkPinned) — volatile.
    private volatile boolean idleHovering;
    private double prevActualX;
    private double prevActualZ;
    private double smoothX;
    private double smoothZ;
    private double prevSmoothX;
    private double prevSmoothZ;

    public ControlFly() {
        super("ControlFly", "Управление полётом на элитре по WASD, совместимое с Grim.", Category.MOVEMENT);

        this.sgGeneral = this.settings.getDefaultGroup();
        this.sgFirework = this.settings.createGroup("Firework");
        this.sgChestSwap = this.settings.createGroup("Chest Swap");

        this.autoFirework = this.sgGeneral.add(new BoolSetting.Builder()
            .name("auto-firework")
            .description("Automatically use fireworks to maintain flight.")
            .defaultValue(true)
            .build());

        this.hoverMode = this.sgGeneral.add(new EnumSetting.Builder<HoverMode>()
            .name("hover-mode")
            .description("Idle hover style. FREEZE brakes with vanilla physics and then parks you "
                + "dead still - no drift, no rubberbands - by holding the client on the last "
                + "position the server heard. FLIP_FLOP is the rocket-fed altitude hold with "
                + "alternating yaw.")
            .defaultValue(HoverMode.FREEZE)
            .build());

        this.hoverPitch = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("hover-pitch")
            .description("Pitch angle when idle. Negative values angle upward to maintain altitude.")
            .defaultValue(-1.25)
            .min(-10.0)
            .max(10.0)
            .sliderRange(-10.0, 10.0)
            .visible(() -> this.hoverMode.get() == HoverMode.FLIP_FLOP)
            .build());

        this.upAngle = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("up-angle")
            .description("Climb angle in degrees while holding jump with a direction key. 45 is the "
                + "robust middle.")
            .defaultValue(45.0)
            .min(0.0)
            .max(90.0)
            .sliderRange(0.0, 90.0)
            .build());

        this.downAngle = this.sgGeneral.add(new DoubleSetting.Builder()
            .name("down-angle")
            .description("Dive angle in degrees while holding sneak with a direction key. Shallower "
                + "than the climb by default.")
            .defaultValue(20.0)
            .min(0.0)
            .max(90.0)
            .sliderRange(0.0, 90.0)
            .build());

        this.smoothCamera = this.sgGeneral.add(new BoolSetting.Builder()
            .name("smooth-camera")
            .description("Smooth out camera jitter when hovering idle.")
            .defaultValue(true)
            .build());

        this.hideRocketParticles = this.sgGeneral.add(new BoolSetting.Builder()
            .name("hide-rocket-particles")
            .description("Hide firework rocket particles including launch and trail effects.")
            .defaultValue(true)
            .build());

        this.refire = this.sgFirework.add(new EnumSetting.Builder<Refire>()
            .name("refire")
            .description("When the next rocket goes up. CONTINUOUS keeps the server's firework "
                + "window open with no gap. TIMED is the old wall-clock cadence and closes it on "
                + "most rockets for a few ticks each.")
            .defaultValue(Refire.CONTINUOUS)
            .visible(this.autoFirework::get)
            .build());

        this.fireworkDelay = this.sgFirework.add(new DoubleSetting.Builder()
            .name("firework-delay")
            .description("Base delay in seconds between firework uses.")
            .defaultValue(1.5)
            .min(0.5)
            .sliderRange(0.5, 5.0)
            .visible(() -> this.autoFirework.get() && this.refire.get() == Refire.TIMED)
            .build());

        this.durationScaling = this.sgFirework.add(new BoolSetting.Builder()
            .name("duration-scaling")
            .description("Scale firework delay based on firework flight duration.")
            .defaultValue(true)
            .visible(() -> this.autoFirework.get() && this.refire.get() == Refire.TIMED)
            .build());

        this.safetyMargin = this.sgFirework.add(new DoubleSetting.Builder()
            .name("safety-margin")
            .description("Lights the next rocket this many seconds earlier still - on top of the "
                + "measured round trip - so thrust never gaps to ping variation.")
            .defaultValue(0.2)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.0, 0.5)
            .visible(() -> this.autoFirework.get()
                && (this.refire.get() == Refire.CONTINUOUS || this.durationScaling.get()))
            .build());

        this.useBoost = this.sgFirework.add(new BoolSetting.Builder()
            .name("firework-boost")
            .description("Ride the firework window while flying. The rockets stay ControlFly's own - "
                + "the boost only rides whatever is lit, so it costs nothing extra to run and "
                + "stands aside wherever it cannot pay.")
            .defaultValue(true)
            .onChanged(val -> {
                if (!this.isActive()) return;

                if (val) enableBoost();
                else disableBoost();
            })
            .build());

        this.porpoise = this.sgFirework.add(new BoolSetting.Builder()
            .name("porpoise")
            .description("Steer the pitch for you while travelling, alternating climb and dive legs, "
                + "instead of flying the flat heading your keys describe. Jump and sneak bias the "
                + "legs up or down; releasing them hands the swing back.")
            .defaultValue(true)
            .visible(this.useBoost::get)
            .build());

        this.porpoiseHeight = this.sgFirework.add(new IntSetting.Builder()
            .name("porpoise-height")
            .description("Total height of the climb-and-dive swing, in blocks. Longer legs are "
                + "faster and are bounded by your terrain and your nerve.")
            .defaultValue(24)
            .min(4)
            .max(120)
            .sliderRange(8, 60)
            .visible(() -> this.useBoost.get() && this.porpoise.get())
            .build());

        this.asymmetricLegs = this.sgFirework.add(new BoolSetting.Builder()
            .name("asymmetric-legs")
            .description("Solve the climb and the dive as separate angles instead of one magnitude "
                + "flown both ways - they are not the same manoeuvre.")
            .defaultValue(true)
            .visible(() -> this.useBoost.get() && this.porpoise.get())
            .build());

        this.pumpLegs = this.sgFirework.add(new BoolSetting.Builder()
            .name("pump-legs")
            .description("Solve the full leg shape instead of one angle held per leg.")
            .defaultValue(true)
            .visible(() -> this.useBoost.get() && this.porpoise.get() && this.asymmetricLegs.get())
            .build());

        this.chestSwap = this.sgChestSwap.add(new BoolSetting.Builder()
            .name("chest-swap")
            .description("Fly without spending elytra durability: keep a chestplate in the off hand "
                + "with the elytra worn, so the server never sees the glide flag up across a tick "
                + "boundary. The off hand matters - the main hand is where the rockets are held.")
            .defaultValue(false)
            .build());

        this.swapInterval = this.sgChestSwap.add(new IntSetting.Builder()
            .name("swap-interval")
            .description("Ticks between glide restarts. Longer means fewer packets, but Grim's "
                + "transactions are held that much longer.")
            .defaultValue(8)
            .min(4)
            .max(20)
            .sliderRange(4, 16)
            .visible(this.chestSwap::get)
            .build());

        this.glideSwap = new ChestSwapGlide(this, () -> this.swapInterval.get(), this::flightContext);

        this.nextFireworkDelay = 1.5;
        this.lastFireworkTime = 0L;
        this.previousSlot = -1;
        this.swapBackTicks = 0;
        this.pinArmTick = -1;
        this.lastWirePosTick = -1;
        this.ticksSinceFirework = 0;

        INSTANCE = this;
    }

    public boolean shouldHideRocketParticles() {
        return this.isActive() && this.hideRocketParticles.get();
    }

    public boolean isIdleHovering() {
        return this.isActive() && this.idleHovering && this.smoothCamera.get()
            && this.hoverMode.get() == HoverMode.FLIP_FLOP;
    }

    public double getSmoothCamX(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevSmoothX, this.smoothX);
    }

    public double getSmoothCamZ(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.prevSmoothZ, this.smoothZ);
    }

    @Override
    public void onActivate() {
        this.flipFlop = false;
        this.idleHovering = false;

        this.nextFireworkDelay = this.fireworkDelay.get();
        this.lastFireworkTime = System.currentTimeMillis();
        this.previousSlot = -1;
        this.swapBackTicks = 0;

        clearPark();

        this.recentSetbacks = 0;
        this.reparkCooldown = 0;
        this.parkBlocked = null;

        this.porpoising = false;
        this.porpoiseFlipTicks = 0;
        this.porpoiseLegTicks = 0;

        this.windowGraceTicks = 0;
        this.clickBlockedTicks = 0;
        this.ticksSinceFirework = 100;

        if (this.useBoost.get()) enableBoost();
    }

    @Override
    public void onDeactivate() {
        restorePreviousSlot();
        this.idleHovering = false;

        clearPark();
        this.recentSetbacks = 0;

        RotationUtils.getInstance().clearRotations();
        this.glideSwap.reset();
        disableBoost();
    }

    

    private void restorePreviousSlot() {
        if (this.previousSlot >= 0 && this.previousSlot < 9 && this.mc.player != null) {
            InvUtils.swap(this.previousSlot, false);
        }
        this.previousSlot = -1;
        this.swapBackTicks = 0;
    }

    @Override
    public void onWorldChange() {
        // Never apply an old player's temporary slot to a new connection.
        this.previousSlot = -1;
        this.swapBackTicks = 0;
        this.idleHovering = false;
        this.glideSwap.reset();
        clearPark();
        this.parkBlocked = null;
        this.recentSetbacks = 0;
        this.lastWirePosTick = -1;
        RotationUtils.getInstance().clearRotations();
    }

    /** ElytraBounce тоже умеет вести полёт — тогда он владеет флагом. */
    private boolean bounceOwnsGlide() {
        if (this.elytraBounce == null) this.elytraBounce = ModuleManager.INSTANCE.getModule(ElytraBounce.class);

        return this.elytraBounce != null
            && this.elytraBounce.isActive()
            && this.elytraBounce.enabled()
            && this.elytraBounce.shouldAutoJump();
    }

    private void enableBoost() {
        RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
        if (boost == null || boost.isActive()) return;

        boost.toggle();
        this.boostToggledByUs = true;
    }

    /** Гасим только если включали мы — чужое состояние не трогаем. */
    private void disableBoost() {
        if (!this.boostToggledByUs) return;

        this.boostToggledByUs = false;

        RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
        if (boost != null && boost.isActive()) boost.toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null) {
            this.glideSwap.tick(false);
            return;
        }

        if (!this.mc.player.isGliding()) {
            restorePreviousSlot();
            this.idleHovering = false;
            clearPark();
            this.glideSwap.tick(false);
            return;
        }

        this.ticksSinceFirework++;

        TickPacer.arm();

        if (this.swapBackTicks > 0) {
            this.swapBackTicks--;

            if (this.swapBackTicks == 0 && this.previousSlot != -1) {
                InvUtils.swap(this.previousSlot, false);
                this.previousSlot = -1;
            }
        }

        handleMovement();

        if (!(hasAnyFirework() || this.idleHovering && this.hoverMode.get() == HoverMode.FREEZE)) {
            info("Out of firework rockets, disabling.");
            toggle();
            return;
        }

        boolean freezeParking = this.idleHovering && this.hoverMode.get() == HoverMode.FREEZE;

        if (!freezeParking) {
            clearPark();
            this.recentSetbacks = 0;
        } else if (this.frozen) {
            if (this.reanchor) {
                // сервер телепортинул: переносим точку парковки на новое место
                this.reanchor = false;
                this.wirePos = this.mc.player.getEntityPos();
                this.pendingStep = null;
                this.ticksSinceSetback = 0;

                if (fireworkAttached() && ++this.recentSetbacks >= 3) {
                    clearPark();
                    this.recentSetbacks = 0;
                    this.reparkCooldown = 40;
                }
            } else if (this.recentSetbacks > 0 && ++this.ticksSinceSetback > 100) {
                this.recentSetbacks = 0;
            }

            if (this.frozen) {
                this.parkedWithRocket = fireworkAttached();

                if (TickPacer.clientTick() > this.pinArmTick) {
                    this.parkBlocked = parkBlockedBy();

                    if (this.parkBlocked != null && TickPacer.clientTick() > this.lastWirePosTick + 1) {
                        clearPark();
                    } else {
                        this.mc.player.setVelocity(Vec3.ZERO);
                    }
                }
            }
        }

        if (this.idleHovering) suppressBoost();

        boolean freezeHolding = freezeParking && (this.frozen || this.parkBlocked == null);
        boolean rocketDue = !freezeHolding && this.autoFirework.get() && fireworkDue();

        this.glideSwap.tick(
            this.chestSwap.get() && !this.mc.player.isOnGround() && !bounceOwnsGlide(), rocketDue);

        if (rocketDue && this.glideSwap.isServerGliding()) handleFirework();

        debugLine();
    }

    /**
     * Парковка держит клиент на месте: пакеты движения гасятся, пока сервер
     * принимает лишь по одному тику элитры за раз.
     */
    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        Packet<?> packet = event.packet;
        if (!(packet instanceof PlayerMoveC2SPacket move)) return;

        if (this.mc.player == null || !this.mc.player.isGliding()) return;

        if (move.changesPosition()) {
            this.keepaliveTicks = 0;

            // сколько-то пакетов пропускаем: сервер уже ответил телепортом
            if (this.passMovePackets.get() > 0) {
                this.passMovePackets.decrementAndGet();
                return;
            }
        } else if (++this.rotOnlyRun >= ROT_ONLY_GUARD) {
            this.stepDue = true;
        }

        if (!parkPinned()) return;
        if (!move.changesPosition()) return;
        if (this.pendingStep != null || this.parkedWithRocket) return;

        // угол в пакете всё равно нужен: иначе сервер решит, что мы смотрим вниз
        if (move.changesLook()) {
            this.mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                move.getYaw(this.mc.player.getYaw()), move.getPitch(this.mc.player.getPitch()),
                move.isOnGround(), move.horizontalCollision()));
        }

        event.cancel();
    }

    @EventHandler
    private void onMoveDispatched(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket move)) return;
        if (this.mc.player == null) return;

        this.glideSwap.onPacketSend(event);

        if (!move.changesPosition()) return;

        this.pendingStep = null;

        this.wirePos = new Vec3(
            move.getX(this.mc.player.getX()),
            move.getY(this.mc.player.getY()),
            move.getZ(this.mc.player.getZ()));

        this.lastWirePosTick = TickPacer.clientTick();
        this.rotOnlyRun = 0;
        this.stepDue = false;
    }

    /** Вызывается перед пакетом движения: держит парковку на месте. */
    public static void beforeSendPosition() {
        if (INSTANCE != null && INSTANCE.isActive()) INSTANCE.holdPark();
    }

    private void holdPark() {
        if (this.mc.player == null || this.mc.world == null || !this.mc.player.isGliding()) return;

        if (this.pendingStep != null) {
            if (this.wirePos != null) this.wirePos = this.wirePos.subtract(this.pendingStep);

            this.pendingStep = null;
        }

        if (!parkPinned()) return;

        if (this.mc.getCameraEntity() != this.mc.player) {
            this.mc.player.setPosition(this.wirePos);
            this.mc.player.setVelocity(Vec3.ZERO);
            return;
        }

        this.keepaliveTicks++;

        // раз в секунду сервер ждёт хоть какое-то движение планера
        if ((this.keepaliveTicks >= POSITION_REMINDER_TICKS || this.stepDue)
            && TickPacer.clientTick() > this.lastWirePosTick + 1) {
            Vec3 step = restingGlideStep();

            this.wirePos = this.wirePos.add(step);
            this.pendingStep = step;
        }

        this.mc.player.setPosition(this.wirePos);
        this.mc.player.setVelocity(Vec3.ZERO);
    }

    private boolean parkPinned() {
        return this.frozen
            && this.idleHovering
            && this.hoverMode.get() == HoverMode.FREEZE
            && this.wirePos != null
            && TickPacer.clientTick() > this.pinArmTick
            && !this.reanchor
            && this.passMovePackets.get() == 0;
    }

    /** Один тик планера на месте — ровно столько сервер принимает от покоящего. */
    private Vec3 restingGlideStep() {
        RotationUtils rotations = RotationUtils.getInstance();

        float pitch = rotations.getSentPitch();
        Vec3 look = Vec3.fromPolar(pitch, rotations.getSentYaw());

        return RideWindow.predictGliding(Vec3.ZERO, look, pitch,
            this.mc.player.getAttributeValue(EntityAttributes.GRAVITY));
    }

    /** Причины, по которым парковку надо снять. */
    private String parkBlockedBy() {
        if (this.wirePos == null) return "no wire position yet";
        if (this.reanchor || this.passMovePackets.get() > 0) return "teleport in flight";
        if (this.mc.player.hasVehicle()) return "riding";
        if (this.mc.player.isOnGround() || this.mc.player.horizontalCollision) return "collided";
        if (this.mc.player.isTouchingWater() || this.mc.player.isInLava()) return "in fluid";
        if (this.mc.player.isClimbing()) return "on a climbable";
        if (this.mc.player.hasStatusEffect(StatusEffects.LEVITATION)) return "levitating";
        if (this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) return "slow falling";

        if (Math.abs(this.mc.player.getAttributeValue(EntityAttributes.GRAVITY) - DEFAULT_GRAVITY) > 1.0E-9) {
            return "gravity changed";
        }

        if (this.mc.player.hurtTime > 0) return "taking damage";
        if (gliderNearlySpent()) return "elytra nearly spent";

        SpeedMine speedMine = ModuleManager.INSTANCE.getModule(SpeedMine.class);
        if (speedMine != null && speedMine.needsMiningTickPacket()) return "mining";

        if (this.mc.world.getStatesInBox(this.mc.player.getBoundingBox().expand(0.05))
            .anyMatch(state -> !state.isAir())) {
            return "in a block";
        }

        if (!clearOfTerrain()) return "near terrain";

        return null;
    }

    /** Сервер ведёт свою копию игрока и она упрётся в стену. */
    private boolean clearOfTerrain() {
        return this.mc.world.isBlockSpaceEmpty(
            this.mc.player, this.mc.player.getBoundingBox().expand(GHOST_CLEARANCE));
    }

    private boolean gliderNearlySpent() {
        ItemStack glider = ChestSwapGlide.gliderStack();

        return glider.isDamageable() && glider.getMaxDamage() - glider.getDamage() <= 2;
    }

    private void clearPark() {
        this.frozen = false;
        this.wirePos = null;
        this.pinArmTick = -1;
        this.keepaliveTicks = 0;
        this.lastWirePosTick = -1;
        this.rotOnlyRun = 0;
        this.stepDue = false;
        this.pendingStep = null;
        this.parkedWithRocket = false;
        this.reanchor = false;
        this.passMovePackets.set(0);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        this.glideSwap.onPacketReceive(event);

        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        if (!this.frozen) return;

        // во время парковки телепорт — это откат: надо перестать держать позицию
        this.passMovePackets.incrementAndGet();
        this.reanchor = true;
    }

    private RocketBoost activeBoost() {
        if (!this.useBoost.get()) return null;

        RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
        return boost != null && boost.isActive() ? boost : null;
    }

    private void suppressBoost() {
        RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
        if (boost != null && boost.isActive()) boost.suppress();
    }

    private boolean fireworkAttached() {
        for (Entity entity : this.mc.world.getEntities()) {
            if (!(entity instanceof FireworkRocketEntity firework)) continue;
            if (!firework.isAlive()) continue;
            if (firework.shooter != this.mc.player) continue;

            return true;
        }

        return false;
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (this.mc.player == null || !this.mc.player.isGliding() || !this.idleHovering) return;
        if (this.hoverMode.get() != HoverMode.FLIP_FLOP) return;

        this.prevSmoothX = this.smoothX;
        this.prevSmoothZ = this.smoothZ;

        double currentX = this.mc.player.getX();
        double currentZ = this.mc.player.getZ();

        // усреднение убирает дрожание камеры при зависании
        this.smoothX = (currentX + this.prevActualX) * 0.5;
        this.smoothZ = (currentZ + this.prevActualZ) * 0.5;

        this.prevActualX = currentX;
        this.prevActualZ = currentZ;
    }

    /** Пора ли запускать следующую ракету. */
    private boolean fireworkDue() {
        if (this.refire.get() == Refire.TIMED) {
            double delayMs = this.nextFireworkDelay * 1000.0;
            if (System.currentTimeMillis() - this.lastFireworkTime < delayMs) return false;
        } else {
            int remaining = windowRemaining();
            int lead = pingTicks() + (int) Math.ceil(this.safetyMargin.get() * 20.0);

            // окно ещё живо: запускать рано
            if (remaining > lead) return false;
            if (this.ticksSinceFirework < pingTicks() + 2) return false;
        }

        // игрок сам жмёт — не мешаем
        return !this.mc.options.useKey.isPressed();
    }

    private void handleFirework() {
        int fireworkSlot = findFireworkHotbar();
        if (fireworkSlot == -1) fireworkSlot = moveFireworkToHotbar();
        if (fireworkSlot == -1) return;

        if (this.durationScaling.get()) {
            ItemStack stack = this.mc.player.getInventory().getStack(fireworkSlot);
            FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);

            int flightDuration = component != null ? component.flightDuration() : 1;

            this.nextFireworkDelay = Math.max(0.3,
                flightDuration * 0.5 + 0.5 - this.safetyMargin.get());
        } else {
            this.nextFireworkDelay = this.fireworkDelay.get();
        }

        int currentSlot = this.mc.player.getInventory().selectedSlot;

        if (currentSlot != fireworkSlot) {
            this.previousSlot = currentSlot;
            InvUtils.swap(fireworkSlot, false);
            this.swapBackTicks = 2;
        }

        this.mc.interactionManager.interactItem(this.mc.player, InteractionHand.MAIN_HAND);
        this.mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(InteractionHand.MAIN_HAND));

        this.lastFireworkTime = System.currentTimeMillis();
        this.ticksSinceFirework = 0;
    }

    /** Через сколько тиков исчезнет самая «долгая» наша ракета. */
    private int windowRemaining() {
        int best = -1;

        for (Entity entity : this.mc.world.getEntities()) {
            if (!(entity instanceof FireworkRocketEntity firework)) continue;
            if (!firework.isAlive()) continue;
            if (firework.shooter != this.mc.player) continue;

            best = Math.max(best, RocketBoost.earliestDeath(firework));
        }

        return best;
    }

    /** Пинг в тиках: ракету надо запускать с запасом на круговой путь. */
    private int pingTicks() {
        PlayerListEntry info = this.mc.getNetworkHandler() == null
            ? null
            : this.mc.getNetworkHandler().getPlayerListEntry(this.mc.player.getUuid());

        int ms = info == null || info.getLatency() <= 0 ? DEFAULT_PING_MS : info.getLatency();

        return (int) Math.ceil(ms / 100.0) + 1;
    }

    private int findFireworkHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.FIREWORK_ROCKET)) continue;

            return i;
        }

        return -1;
    }

    /** Ракеты в инвентаре перекладываются в хотбар невидимым кликом. */
    private int moveFireworkToHotbar() {
        int invSlot = -1;

        for (int i = 9; i < 36; i++) {
            ItemStack stack = this.mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.FIREWORK_ROCKET)) continue;

            invSlot = i;
            break;
        }

        if (invSlot == -1) return -1;

        int targetHotbar = findEmptyHotbarSlot();
        if (targetHotbar == -1) targetHotbar = 8;

        // сервер отменяет клики по инвентарю, пока игрок движется
        if (!GrimUtils.inventoryClickClean()) {
            if (++this.clickBlockedTicks == 40) {
                warning("Rockets are in the inventory, not the hotbar, and a held movement or sneak "
                    + "key blocks the restock (the server cancels inventory clicks sent while moving) "
                    + "- release the keys for a tick.");
            }

            return -1;
        }

        this.clickBlockedTicks = 0;

        InvUtils.move().from(invSlot).toHotbar(targetHotbar);
        return targetHotbar;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (!this.mc.player.getInventory().getStack(i).isEmpty()) continue;

            return i;
        }

        return -1;
    }

    /** Клавиши → курс и угол. */
    private void handleMovement() {
        Vec3 vec = Vec3.ZERO;

        float yaw = this.mc.player.getYaw();
        boolean jumpHeld = false;
        boolean sneakHeld = false;

        Freecam freecam = ModuleManager.INSTANCE.getModule(Freecam.class);

        if (freecam == null || !freecam.isActive()) {
            if (this.mc.options.forwardKey.isPressed()) vec = vec.add(Vec3.fromPolar(0.0f, yaw));
            if (this.mc.options.backKey.isPressed()) vec = vec.add(Vec3.fromPolar(0.0f, yaw + 180.0f));
            if (this.mc.options.leftKey.isPressed()) vec = vec.add(Vec3.fromPolar(0.0f, yaw - 90.0f));
            if (this.mc.options.rightKey.isPressed()) vec = vec.add(Vec3.fromPolar(0.0f, yaw + 90.0f));

            jumpHeld = ChestSwapGlide.rawJumpDown();
            sneakHeld = this.mc.options.sneakKey.isPressed();
        }

        boolean hasFirework = findFireworkHotbar() != -1 || hasFireworkInInventory();
        boolean horizontal = vec.lengthSquared() >= 1.0E-4;

        // ── Ничего не нажато: зависание ──
        if (!(horizontal || jumpHeld || sneakHeld)) {
            this.porpoising = false;

            boolean freeze = this.hoverMode.get() == HoverMode.FREEZE;

            if (freeze || hasFirework) {
                if (!this.idleHovering) {
                    this.idleHovering = true;

                    double x = this.mc.player.getX();
                    double z = this.mc.player.getZ();

                    this.prevActualX = x;
                    this.prevActualZ = z;
                    this.smoothX = x;
                    this.smoothZ = z;
                    this.prevSmoothX = x;
                    this.prevSmoothZ = z;
                }

                if (freeze) {
                    if (this.reparkCooldown > 0) this.reparkCooldown--;

                    if (this.frozen) return;

                    this.parkBlocked = parkBlockedBy();

                    if (this.parkBlocked == null) {
                        if (this.reparkCooldown > 0
                            || this.mc.player.getVelocity().horizontalLengthSquared() > 0.25) {
                            // ещё не остановились: раскачиваем курс, чтобы гасить
                            if (this.flipFlop) {
                                this.flipFlop = false;
                                RotationUtils.getInstance().setRotationFullInstant(0.0f, 0.0f);
                            } else {
                                this.flipFlop = true;
                                RotationUtils.getInstance().setRotationFullInstant(180.0f, 0.0f);
                            }
                        } else {
                            // стоим: можно прибить клиент к точке
                            this.frozen = true;
                            this.pinArmTick = TickPacer.clientTick();
                            this.keepaliveTicks = 0;
                            this.rotOnlyRun = 0;
                            this.stepDue = false;
                            this.pendingStep = null;

                            RotationUtils.getInstance().clearRotations();
                            RotationUtils.getInstance().keepWireFresh();
                        }

                        return;
                    }

                    if (!hasFirework) return;
                }

                if (this.mc.options.useKey.isPressed()) {
                    RotationUtils.getInstance().setRotationFullInstant(
                        this.mc.player.getYaw(), this.mc.player.getPitch());
                } else {
                    float idlePitch = this.hoverPitch.get().floatValue();

                    if (this.flipFlop) {
                        this.flipFlop = false;
                        RotationUtils.getInstance().setRotationFullInstant(0.0f, idlePitch);
                    } else {
                        this.flipFlop = true;
                        RotationUtils.getInstance().setRotationFullInstant(180.0f, idlePitch);
                    }
                }
            }

            return;
        }

        // только что отправили пакет парковки: не дёргаем позицию в этом тике
        if (this.frozen && TickPacer.clientTick() <= this.lastWirePosTick + 1) return;

        boolean wasIdle = this.idleHovering;
        this.idleHovering = false;

        RocketBoost driven = ModuleManager.INSTANCE.getModule(RocketBoost.class);
        if (driven != null && driven.isActive()) driven.declarePitchFree();

        float[] rot;
        float pitch;

        if (!horizontal) {
            rot = new float[]{yaw, sneakHeld ? 90.0f : -90.0f};
            this.porpoising = false;
            pitch = rot[1];
        } else {
            rot = getYawPitch(vec);

            if (sneakHeld) {
                rot[1] = this.downAngle.get().floatValue();
            } else if (jumpHeld) {
                rot[1] = -this.upAngle.get().floatValue();
            }

            pitch = porpoisePitch(rot[0], rot[1]);
        }

        RotationUtils.getInstance().setRotationFullInstant(rot[0], pitch);

        // после зависания разгоняемся на скорости терминальной
        if (wasIdle && fireworkAttached()) {
            this.mc.player.setVelocity(Vec3.fromPolar(pitch, rot[0]).multiply(FIREWORK_TERMINAL_SPEED));

            RocketBoost boost = ModuleManager.INSTANCE.getModule(RocketBoost.class);
            if (boost != null && boost.isActive()) boost.clearSuppression();
        }
    }

    /** Угол пике, если режим включён и окно фейерверка живо. */
    private float porpoisePitch(float yaw, float keyPitch) {
        RocketBoost boost = activeBoost();

        if (!this.porpoise.get() || boost == null) {
            this.porpoising = false;
            return keyPitch;
        }

        // окно только что закрылось — даём запас, иначе пике будет биться о провал
        if (boost.hasWindow()) {
            this.windowGraceTicks = WINDOW_GRACE;
        } else if (this.windowGraceTicks > 0) {
            this.windowGraceTicks--;
        } else {
            this.porpoising = false;
            return keyPitch;
        }

        double y = this.mc.player.getY();
        double half = this.porpoiseHeight.get() / 2.0;

        if (!this.porpoising) {
            this.porpoising = true;
            this.porpoiseTarget = y;
            this.porpoiseDescending = true;
            this.porpoiseFlipTicks = 0;
            this.porpoiseLegTicks = 0;
        }

        if (this.porpoiseFlipTicks > 0) this.porpoiseFlipTicks--;

        boolean manualUp = ChestSwapGlide.rawJumpDown();
        boolean manualDown = this.mc.options.sneakKey.isPressed();

        // игрок берёт управление на себя
        if (manualUp || manualDown) {
            this.porpoiseDescending = manualDown;
            this.porpoiseTarget = y;
            this.porpoiseLegTicks = 0;

            return manualDown ? this.downAngle.get().floatValue() : -this.upAngle.get().floatValue();
        }

        if (this.porpoiseFlipTicks == 0) {
            if (this.porpoiseDescending && y <= this.porpoiseTarget - half) {
                this.porpoiseDescending = false;
                this.porpoiseLegTicks = 0;
            } else if (!this.porpoiseDescending && y >= this.porpoiseTarget + half) {
                this.porpoiseDescending = true;
                this.porpoiseLegTicks = 0;
            }
        }

        PorpoiseSolver.Legs legs = PorpoiseSolver.solve(
            this.porpoiseHeight.get(), yaw,
            boost.alignmentDegrees(), boost.pitchAlignmentDegrees(),
            boost.windowThreshold(), RocketBoost.antiTickSkipping(),
            this.asymmetricLegs.get(), this.pumpLegs.get(),
            this.mc.player.getAttributeValue(EntityAttributes.GRAVITY),
            this.mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING));

        // нога упирается в скалу: переворачиваем раньше
        if (this.porpoiseFlipTicks == 0
            && legObstructed(yaw, this.porpoiseDescending, y, half, legs)) {
            if (legObstructed(yaw, !this.porpoiseDescending, y, half, legs)) {
                // обе ноги упираются: сдаёмся и летим по клавишам
                this.porpoiseTarget = y;
                this.porpoiseLegTicks = 0;
                return keyPitch;
            }

            this.porpoiseDescending = !this.porpoiseDescending;
            this.porpoiseLegTicks = 0;
            this.porpoiseFlipTicks = 10;
        }

        float pitch = legs.pitch(this.porpoiseDescending, this.porpoiseLegTicks,
            y - this.porpoiseTarget, half);

        this.porpoiseLegTicks++;

        return pitch;
    }

    /** Проверка скалы по склону, который игрок реально пролетит. */
    private boolean legObstructed(float yaw, boolean descending, double y, double half,
                                 PorpoiseSolver.Legs legs) {
        double end = descending
            ? this.porpoiseTarget - half - LEG_OVERSHOOT_D
            : this.porpoiseTarget + half + LEG_OVERSHOOT_D;

        double rise = end - y;
        if (descending ? rise >= 0.0 : rise <= 0.0) return false;

        double ground = Math.abs(rise) / (2.0 * half) * legs.ground(descending);
        ground = MathHelper.clamp(ground, MIN_LEG_LOOKAHEAD, MAX_LEG_LOOKAHEAD);

        Vec3 eye = this.mc.player.getEyePos();
        Vec3 along = Vec3.fromPolar(0.0f, yaw).multiply(ground);
        Vec3 end3 = eye.add(along.x, rise, along.z);

        return this.mc.world.raycast(new RaycastContext(eye, end3,
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this.mc.player))
            .getType() != HitResult.Type.MISS;
    }

    private boolean hasFireworkInInventory() {
        for (int i = 9; i < 36; i++) {
            if (!this.mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) continue;

            return true;
        }

        return false;
    }

    private boolean hasAnyFirework() {
        return findFireworkHotbar() != -1 || hasFireworkInInventory();
    }

    private float[] getYawPitch(Vec3 vec) {
        if (vec.lengthSquared() < 1.0E-8) {
            return new float[]{this.mc.player.getYaw(), this.mc.player.getPitch()};
        }

        vec = vec.normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-vec.x, vec.z));

        double horizontalLength = Math.sqrt(vec.x * vec.x + vec.z * vec.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(vec.y, horizontalLength));
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);

        return new float[]{yaw, pitch};
    }

    @Override
    public String getInfoString() {
        if (this.mc.player == null || !this.mc.player.isGliding()) return null;

        String state;
        if (this.idleHovering && this.hoverMode.get() == HoverMode.FREEZE) {
            state = this.frozen ? "Parked"
                : (this.parkBlocked != null ? "Hover: " + this.parkBlocked : "Braking");
        } else {
            state = "Flying";
        }

        return this.glideSwap.isEngaged() ? state + " | swap " + this.glideSwap.bursts() : state;
    }

    private String flightState() {
        if (this.idleHovering && this.hoverMode.get() == HoverMode.FREEZE) {
            if (this.frozen) return "parked";

            return this.parkBlocked != null ? "hover (" + this.parkBlocked + ")" : "braking";
        }

        return this.idleHovering ? "flip-flop hover" : "flying";
    }

    private String flightContext() {
        if (this.mc.player == null || this.mc.world == null) return "no player";

        Vec3 velocity = this.mc.player.getVelocity();

        return String.format(
            "%s at %.0f b/s (%.1f vertical), pitch %.0f, Y %.0f, %s, last rocket %d ticks ago",
            flightState(), velocity.horizontalLength() * 20.0, velocity.y * 20.0,
            this.mc.player.getPitch(), this.mc.player.getY(),
            fireworkAttached() ? "a rocket attached" : "no rocket", this.ticksSinceFirework);
    }

    private void debugLine() {
        if (!false || ++this.debugTicks < 40) return;

        this.debugTicks = 0;

        Vec3 velocity = this.mc.player.getVelocity();

        info("%.1f b/s | %.1f b/s vertical | Y %.0f | pitch %.0f | %s%s | %s, %d catch-up ticks paced",
            velocity.horizontalLength() * 20.0, velocity.y * 20.0, this.mc.player.getY(),
            this.mc.player.getPitch(), flightState(),
            this.chestSwap.get() && this.glideSwap.isEngaged()
                ? " | chest swap " + this.glideSwap.stats()
                : "",
            this.glideSwap.telemetry(), TickPacer.takeDeferred());
    }

    /** Способ удержания высоты без нажатых клавиш. */
    public enum HoverMode {
        FREEZE,
        FLIP_FLOP
    }

    /** Когда запускать следующую ракету. */
    public enum Refire {
        CONTINUOUS,
        TIMED
    }
}