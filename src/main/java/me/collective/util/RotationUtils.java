package me.collective.util;

import me.collective.event.EventBus;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import net.minecraft.client.MinecraftClient;

import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.Set;

/**
 * Тихие повороты серверной стороны: камера не двигается, но сервер «видит» нужный
 * угол.
 *
 * <p>Перенос {@code bep.hax.util.RotationUtils}, GPL-3.0. Ключевые решения,
 * которые перенесены как есть:
 *
 * <ul>
 *   <li>поворот идёт непрерывным потоком в пакетах движения, а рывком;
 *   <li>доворот копится по тикам, но с «квантом» — угол округляется к
 *       случайному шагу, чтобы два пакета подряд не меняли yaw на одинаковую
 *       мелочь и не выглядели как дубль;
 *   <li>после выравнивания держится несколько тиков и плавно возвращается;
 *   <li>модули-заказчики конкурируют за поворот по приоритету;
 *   <li>движение пересчитывается под новый угол, иначе сервер посчитает
 *       перемещение не по тому вектору.
 * </ul>
 */
public class RotationUtils {

    private static RotationUtils INSTANCE;

    public static final double DEFAULT_TURN_SPEED = 45.0;
    public static final double DEFAULT_ALIGN_EPS = 2.0;
    public static final int DEFAULT_DECAY_TICKS = 4;
    public static final double DECAY_TURN_SPEED = 80.0;

    /** Минимальный шаг угла, кратный которому уходят углы в пакеты. */
    private static final float WIRE_NUDGE = 0.01f;

    private float serverYaw;
    private float serverPitch;

    private Float finalYaw;
    private Float finalPitch;

    private float currentYaw;
    private float currentPitch;
    private float prevCurrentYaw;
    private float prevCurrentPitch;

    private double turnSpeed = DEFAULT_TURN_SPEED;
    private boolean syncMovement;

    private int wireFreshTicks;
    private float wireNudge;

    private int keepTicks;
    private boolean decaying;
    private int decayTicks;

    private Integer offsetK;
    private float offsetPitch;
    private int offsetKeepTicks;

    private boolean direct;
    private int cachedInputSteps;
    private boolean inputStepsValid;
    private boolean initialized;

    private Object owner;
    private int ownerPriority;

    private final float quantum;
    private final Random random = new Random();

    /** Направления ввода: [вперёд, назад, влево, вправо]. */
    private static final boolean[][] INPUT_DIRS = {
        {true, false, false, false},
        {true, false, false, true},
        {false, false, false, true},
        {false, true, false, true},
        {false, true, false, false},
        {false, true, true, false},
        {false, false, true, false},
        {true, false, true, false}
    };

    private RotationUtils() {
        // квант зависит от «чувствительности» — как в замере у реального игрока
        float sens = 0.35f + this.random.nextFloat() * 0.3f;
        float f = sens * 0.6f + 0.2f;
        this.quantum = f * f * f * 8.0f * 0.15f;

        EventBus.INSTANCE.subscribe(this);
    }

    public static RotationUtils getInstance() {
        if (INSTANCE == null) INSTANCE = new RotationUtils();
        return INSTANCE;
    }

    /** Угол, который сервер видел последним. */
    @EventHandler(priority = -200)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().world == null) return;

        Packet<?> packet = event.packet;
        if (packet instanceof PlayerMoveC2SPacket move && move.changesLook()) {
            this.serverYaw = move.getYaw(this.serverYaw);
            this.serverPitch = move.getPitch(this.serverPitch);
        }
    }

    public void onDisconnect() {
        clearAll();
        this.initialized = false;
        this.wireFreshTicks = 0;
        this.wireNudge = 0.0f;
        this.serverYaw = 0.0f;
        this.serverPitch = 0.0f;
        this.currentYaw = 0.0f;
        this.currentPitch = 0.0f;
        this.prevCurrentYaw = 0.0f;
        this.prevCurrentPitch = 0.0f;
    }

    /** Телепорт обнуляет серверные углы — всё накопленное сбрасываем. */
    @EventHandler(priority = 200)
    public void onPacketReceive(PacketEvent.Receive event) {
        Packet<?> packet = event.packet;
        if (!(packet instanceof PlayerPositionLookS2CPacket teleport)) return;

        EntityPosition change = teleport.change();
        Set<PositionFlag> relatives = teleport.relatives();

        this.serverYaw = relatives.contains(PositionFlag.Y_ROT) ? this.serverYaw + change.yaw() : change.yaw();
        this.serverPitch = relatives.contains(PositionFlag.X_ROT) ? this.serverPitch + change.pitch() : change.pitch();

        this.currentYaw = this.serverYaw;
        this.currentPitch = this.serverPitch;
        this.prevCurrentYaw = this.currentYaw;
        this.prevCurrentPitch = this.currentPitch;

        clearAll();
    }

    @EventHandler(priority = 200)
    public void onTickPre(TickEvent.Pre event) {
        this.inputStepsValid = false;

        if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().world == null) return;

        if (this.wireFreshTicks > 0) {
            this.wireFreshTicks--;
        }
        this.wireNudge = 0.0f;

        if (!this.initialized) {
            this.serverYaw = MinecraftClient.getInstance().player.getYaw();
            this.serverPitch = MinecraftClient.getInstance().player.getPitch();
            this.currentYaw = this.serverYaw;
            this.currentPitch = this.serverPitch;
            this.prevCurrentYaw = this.currentYaw;
            this.prevCurrentPitch = this.currentPitch;
            this.initialized = true;
        }

        this.prevCurrentYaw = this.currentYaw;
        this.prevCurrentPitch = this.currentPitch;

        if (this.finalYaw == null && this.finalPitch == null) return;

        // в этих состояниях поворот нельзя удерживать: сервер всё равно пришлёт
        // свою позицию и углы
        if (!this.syncMovement && (MinecraftClient.getInstance().player.isGliding()
            || MinecraftClient.getInstance().player.isSwimming()
            || MinecraftClient.getInstance().player.hasVehicle())) {
            clearAll();
            return;
        }

        // на возврате цель — это текущий угол игрока, а не заказанный
        if (this.decaying) {
            this.finalYaw = MinecraftClient.getInstance().player.getYaw();
            this.finalPitch = MinecraftClient.getInstance().player.getPitch();
        }

        double yawRemaining = MathHelper.wrapDegrees(this.finalYaw - this.currentYaw);
        double pitchRemaining = this.finalPitch - this.currentPitch;
        double totalDist = Math.hypot(yawRemaining, pitchRemaining);

        if (totalDist < 0.0001) {
            this.currentYaw = this.finalYaw;
            this.currentPitch = this.finalPitch;
            return;
        }

        double maxStep = this.decaying ? Math.min(this.turnSpeed, DECAY_TURN_SPEED) : this.turnSpeed;
        double step;
        if (this.direct) {
            step = Math.min(totalDist, maxStep);
        } else {
            // Плавное торможение вблизи цели (ease-out), сохраняя максимальную скорость на пути
            double decelRange = Math.max(16.0, maxStep * 0.40);
            double speedFactor = totalDist < decelRange ? Math.pow(totalDist / decelRange, 0.65) : 1.0;
            double desiredSpeed = Math.max(maxStep * speedFactor, 3.0);
            step = Math.min(totalDist, Math.min(desiredSpeed, maxStep));
        }

        double dirY = yawRemaining / totalDist;
        double dirP = pitchRemaining / totalDist;

        double yawStep = dirY * step;
        double pitchStep = dirP * step;

        float q = getQuantum();
        int yawDots = (int) Math.round(yawStep / q);
        int pitchDots = (int) Math.round(pitchStep / q);

        if (yawDots == 0 && Math.abs(yawRemaining) > q * 0.5) {
            yawDots = (int) Math.signum(yawRemaining);
        }
        if (pitchDots == 0 && Math.abs(pitchRemaining) > q * 0.5) {
            pitchDots = (int) Math.signum(pitchRemaining);
        }

        this.currentYaw += yawDots * q;
        this.currentPitch = MathHelper.clamp(this.currentPitch + pitchDots * q, -90.0f, 90.0f);
    }

    private float getQuantum() {
        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().options != null) {
            double sens = MinecraftClient.getInstance().options.getMouseSensitivity().getValue();
            double f = sens * 0.6 + 0.2;
            double q = f * f * f * 8.0 * 0.15;
            if (q > 0.0001) return (float) q;
        }
        return this.quantum;
    }

    @EventHandler(priority = -200)
    public void onTickPost(TickEvent.Post event) {
        if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().world == null) return;

        if (this.offsetK != null && this.offsetKeepTicks-- <= 0) this.offsetK = null;

        if (this.finalYaw == null && this.finalPitch == null) {
            if (this.offsetK == null) {
                this.owner = null;
                this.ownerPriority = 0;
            }
            return;
        }

        if (this.decaying) {
            if (this.decayTicks-- <= 0 || alignedNow(DEFAULT_ALIGN_EPS)) clearAll();
            return;
        }

        if (!alignedNow(DEFAULT_ALIGN_EPS)) return;

        // угол достигнут — держим его ещё несколько тиков перед возвратом
        if (this.keepTicks > 0) {
            this.keepTicks--;
            return;
        }

        this.decaying = true;
        this.decayTicks = DEFAULT_DECAY_TICKS;
        this.owner = null;
        this.ownerPriority = 0;
    }

    /** Захват поворота модулем. */
    private boolean claim(Object owner, int priority) {
        if (this.owner != null && this.owner != owner && !this.decaying && priority <= this.ownerPriority) {
            // Координация боевых аур: победитель тика перехватывает «провод» —
            // сервер видит в этом тике только его угол. Не-участники координации
            // ведут себя как раньше.
            if (!AuraCoordination.isCurrentWinner(owner)) return false;
        }

        this.owner = owner;
        this.ownerPriority = owner == null ? 0 : priority;
        this.inputStepsValid = false;
        this.direct = false;
        return true;
    }

    public boolean isOwner(Object owner) {
        return this.owner == owner;
    }

    public void release(Object owner) {
        if (this.owner != owner) return;

        this.owner = null;
        this.ownerPriority = 0;
        if (this.finalYaw != null || this.finalPitch != null) {
            this.decaying = true;
            this.decayTicks = DEFAULT_DECAY_TICKS;
        }
    }

    public boolean setRotationSilent(float yaw, float pitch) {
        return setRotationSmooth(yaw, pitch, DEFAULT_TURN_SPEED, false);
    }

    public boolean setRotationSilent(float yaw, float pitch, double turnSpeed) {
        return setRotationSmooth(yaw, pitch, turnSpeed, false);
    }

    public boolean setRotationSilent(Object owner, int priority, float yaw, float pitch) {
        return setRotationSmooth(owner, priority, yaw, pitch, DEFAULT_TURN_SPEED, false);
    }

    public boolean setRotationSilent(Object owner, int priority, float yaw, float pitch, double turnSpeed) {
        return setRotationSmooth(owner, priority, yaw, pitch, turnSpeed, false);
    }

    /** Без сглаживания: сразу к целевому углу. */
    public boolean setRotationSilentDirect(Object owner, int priority, float yaw, float pitch, double turnSpeed) {
        if (!setRotationSmooth(owner, priority, yaw, pitch, turnSpeed, false)) return false;

        this.direct = true;
        return true;
    }

    public boolean setRotationFull(float yaw, float pitch) {
        return setRotationSmooth(yaw, pitch, DEFAULT_TURN_SPEED, true);
    }

    public boolean setRotationFull(float yaw, float pitch, double turnSpeed) {
        return setRotationSmooth(yaw, pitch, turnSpeed, true);
    }

    public boolean setRotationFull(Object owner, int priority, float yaw, float pitch, double turnSpeed) {
        return setRotationSmooth(owner, priority, yaw, pitch, turnSpeed, true);
    }

    public boolean setRotationSilentInstant(Object owner, int priority, float yaw, float pitch) {
        if (!claim(owner, priority)) return false;

        yaw = shortestYaw(yaw);

        this.finalYaw = yaw;
        this.finalPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        this.currentYaw = yaw;
        this.currentPitch = this.finalPitch;
        this.turnSpeed = DEFAULT_TURN_SPEED;
        this.syncMovement = false;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    /** Мгновенный поворот с захватом у модуля-заказчика. */
    public boolean setRotationFullInstant(Object owner, int priority, float yaw, float pitch) {
        if (!claim(owner, priority)) return false;

        yaw = shortestYaw(yaw);

        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.currentYaw = yaw;
        this.currentPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        this.turnSpeed = DEFAULT_TURN_SPEED;
        this.syncMovement = true;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    public boolean setRotationInstant(Object owner, int priority, float yaw, float pitch, boolean syncMovement) {
        if (syncMovement) {
            return setRotationFullInstant(owner, priority, yaw, pitch);
        } else {
            return setRotationSilentInstant(owner, priority, yaw, pitch);
        }
    }

    public boolean setRotationFullInstant(float yaw, float pitch) {
        if (!claim(null, 0)) return false;

        yaw = shortestYaw(yaw);

        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.currentYaw = yaw;
        this.currentPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        this.turnSpeed = DEFAULT_TURN_SPEED;
        this.syncMovement = true;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    /** Ближайший эквивалент угла к серверному, чтобы не уехать на пол-оборота. */
    private float shortestYaw(float yaw) {
        return this.serverYaw + MathHelper.wrapDegrees(yaw - this.serverYaw);
    }

    public boolean setRotationSmooth(float yaw, float pitch, double turnSpeed, boolean sync) {
        return setRotationSmooth(null, 0, yaw, pitch, turnSpeed, sync);
    }

    public boolean setRotationSmooth(Object owner, int priority, float yaw, float pitch, double turnSpeed,
                                     boolean sync) {
        if (!claim(owner, priority)) return false;

        if (this.finalYaw == null && this.finalPitch == null) {
            this.currentYaw = this.serverYaw;
            this.currentPitch = this.serverPitch;
        }

        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.turnSpeed = turnSpeed;
        this.syncMovement = sync;
        this.keepTicks = 2;
        this.decaying = false;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        return true;
    }

    /** Фиксированное смещение на кратное 45 градусов — для непрерывных кругов. */
    public boolean setRotationOffset(int k45, float pitch) {
        return setRotationOffset(null, 0, k45, pitch);
    }

    public boolean setRotationOffset(Object owner, int priority, int k45, float pitch) {
        if (!claim(owner, priority)) return false;

        this.offsetK = k45;
        this.offsetPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        this.offsetKeepTicks = 2;
        this.finalYaw = null;
        this.finalPitch = null;
        this.syncMovement = false;
        this.keepTicks = 0;
        this.decaying = false;
        this.decayTicks = 0;
        return true;
    }

    public boolean isOffsetRotation() {
        return this.offsetK != null;
    }

    public void setRotationSilentSync() {
        if (MinecraftClient.getInstance().player == null) return;

        setRotationSilent(MinecraftClient.getInstance().player.getYaw(), MinecraftClient.getInstance().player.getPitch());
    }

    public void clearRotations() {
        clearRotations(null);
    }

    public void clearRotations(Object owner) {
        if (this.owner != null && this.owner != owner) return;

        clearAll();
    }

    private void clearAll() {
        this.finalYaw = null;
        this.finalPitch = null;
        this.syncMovement = false;
        this.keepTicks = 0;
        this.decaying = false;
        this.decayTicks = 0;
        this.offsetK = null;
        this.offsetKeepTicks = 0;
        this.owner = null;
        this.ownerPriority = 0;
        this.inputStepsValid = false;
        this.direct = false;
    }

    public boolean isRotating() {
        return this.finalYaw != null || this.finalPitch != null || this.offsetK != null;
    }

    public float getRotationYaw() {
        if (this.offsetK != null && MinecraftClient.getInstance().player != null) {
            return MinecraftClient.getInstance().player.getYaw() + this.offsetK * 45.0f;
        }

        if (this.finalYaw != null || this.finalPitch != null) return this.currentYaw;

        return MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getYaw() : 0.0f;
    }

    public float getRotationPitch() {
        if (this.offsetK != null) return this.offsetPitch;
        if (this.finalYaw != null || this.finalPitch != null) return this.currentPitch;

        return MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPitch() : 0.0f;
    }

    /** Угол, который реально уходит в пакет. */
    public float getSentYaw() {
        return shortestYaw(getRotationYaw());
    }

    public float getSentPitch() {
        return getRotationPitch();
    }

    public float getPrevSentYaw() {
        if (this.offsetK != null && MinecraftClient.getInstance().player != null) {
            return shortestYaw(MinecraftClient.getInstance().player.getYaw() + this.offsetK * 45.0f);
        }
        if (this.finalYaw != null || this.finalPitch != null) {
            return shortestYaw(this.prevCurrentYaw);
        }
        return MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getYaw() : 0.0f;
    }

    public float getPrevSentPitch() {
        if (this.offsetK != null) return this.offsetPitch;
        if (this.finalYaw != null || this.finalPitch != null) return this.prevCurrentPitch;
        return MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getPitch() : 0.0f;
    }

    public void keepWireFresh() {
        this.wireFreshTicks = 2;
    }

    public boolean isWireFresh() {
        return this.wireFreshTicks > 0;
    }

    /**
     * Немедленно досылает look-пакет с текущим серверным углом.
     *
     * <p>Углы обычно уходят только в пакетах движения (раз в тик, в
     * {@code ClientPlayerEntity.sendMovementPackets()}), а боевые модули
     * бьют раньше — в {@code TickEvent.Pre}. Так сервер оценивал удар по
     * углу прошлого тика, и проверки «взгляд — удар» это видят. Боевой
     * модуль вызывает метод прямо перед ударом, чтобы порядок пакетов был
     * «поворот → удар».
     */
    public void flushServerRotation() {
        // No-op: не отсылаем внеочередные пакеты поворота перед действиями.
        // Серверный угол уже уходит в sendMovementPackets() текущего/прошлого тика;
        // досылка LookAndOnGround перед атакой/интерактом триггерит у GrimAC PacketOrderO,
        // TickTimer (packets=2) и AimDuplicateLook.
    }

    public boolean isAligned(double eps) {
        if (this.owner != null) return false;

        return alignedNow(eps);
    }

    public boolean isAligned() {
        return isAligned(DEFAULT_ALIGN_EPS);
    }

    public boolean isAlignedFor(Object owner, double eps) {
        if (this.owner != owner) return false;

        return alignedNow(eps);
    }

    public boolean isAlignedFor(Object owner) {
        return isAlignedFor(owner, DEFAULT_ALIGN_EPS);
    }

    private boolean alignedNow(double eps) {
        if (this.offsetK != null) return true;
        if (!isRotating()) return true;

        double dy = this.finalYaw == null ? 0.0 : Math.abs(MathHelper.wrapDegrees(this.finalYaw - this.currentYaw));
        double dp = this.finalPitch == null ? 0.0 : Math.abs(this.finalPitch - this.currentPitch);

        return dy <= eps && dp <= eps;
    }

    public Float getMovementPitch() {
        return this.syncMovement && isRotating() ? this.currentPitch : null;
    }

    public Float getMovementYaw() {
        return this.syncMovement && isRotating() ? this.currentYaw : null;
    }

    /** На сколько шагов по 45 градусов сместился ввод относительно угла. */
    public int getInputSteps() {
        if (!this.inputStepsValid) {
            this.cachedInputSteps = computeInputSteps();
            this.inputStepsValid = true;
        }

        return this.cachedInputSteps;
    }

    private int computeInputSteps() {
        if (MinecraftClient.getInstance().player == null) return 0;
        if (this.offsetK != null) return -this.offsetK;
        if (this.finalYaw == null && this.finalPitch == null) return 0;
        if (this.syncMovement) return 0;

        float delta = MathHelper.wrapDegrees(MinecraftClient.getInstance().player.getYaw() - this.currentYaw);

        // сидя или с предметом в руке порог смещения другой
        boolean scaled = MinecraftClient.getInstance().player.isUsingItem()
            || MinecraftClient.getInstance().player.isInSneakingPose()
            || MinecraftClient.getInstance().player.isCrawling();

        return scaled ? 2 * Math.round(delta / 90.0f) : Math.round(delta / 45.0f);
    }

    public boolean isSyncMovement() {
        return this.syncMovement;
    }

    public Float getMoveYaw() {
        if (MinecraftClient.getInstance().player == null || !isRotating()) return null;
        if (this.offsetK != null) return null;
        return this.currentYaw;
    }

    /** Пересобирает ввод под серверный угол, чтобы движение считалось точно и без флагов. */
    public PlayerInput rotateDeclaredInput(PlayerInput in) {
        if (MinecraftClient.getInstance().player == null || !isRotating()) return in;
        if (this.syncMovement) return in;

        int s = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
        int f = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        if (s == 0 && f == 0) return in;

        float cameraYaw = MinecraftClient.getInstance().player.getYaw();
        float serverYaw = this.currentYaw;
        float delta = MathHelper.wrapDegrees(cameraYaw - serverYaw);
        double phi = Math.toRadians(delta);

        double sIdeal = s * Math.cos(phi) - f * Math.sin(phi);
        double fIdeal = s * Math.sin(phi) + f * Math.cos(phi);

        int bestS = 0;
        int bestF = 0;
        double bestScore = -Double.MAX_VALUE;

        for (int ds = -1; ds <= 1; ds++) {
            for (int df = -1; df <= 1; df++) {
                if (ds == 0 && df == 0) continue;
                double len = (ds != 0 && df != 0) ? 1.4142135623730951 : 1.0;
                double score = (ds * sIdeal + df * fIdeal) / len;
                if (score > bestScore) {
                    bestScore = score;
                    bestS = ds;
                    bestF = df;
                }
            }
        }

        boolean canSprint = in.sprint() && bestF > 0;
        return new PlayerInput(bestF > 0, bestF < 0, bestS > 0, bestS < 0, in.jump(), in.sneak(), canSprint);
    }

    public float getMoveSpeedScale() {
        return 1.0f;
    }

    public boolean isSprintBlocked() {
        if (MinecraftClient.getInstance().player == null || this.offsetK != null || this.syncMovement) return false;
        if (this.finalYaw == null && this.finalPitch == null) return false;

        return !rotateDeclaredInput(MinecraftClient.getInstance().player.input.playerInput).forward();
    }

    public float getServerYaw() {
        return this.serverYaw;
    }

    public void setServerYaw(float yaw) {
        this.serverYaw = yaw;
    }

    public float getServerPitch() {
        return this.serverPitch;
    }

    public float getWrappedYaw() {
        return MathHelper.wrapDegrees(this.serverYaw);
    }

    public static float[] getRotationsTo(Vec3d src, Vec3d dest) {
        double diffX = dest.x - src.x;
        double diffY = dest.y - src.y;
        double diffZ = dest.z - src.z;

        double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));

        return new float[]{MathHelper.wrapDegrees((float) yaw), MathHelper.wrapDegrees((float) pitch)};
    }

    public static float[] getLegitRotations(Vec3d dest) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new float[]{0, 0};
        Vec3d eyes = mc.player.getEyePos();
        return getRotationsTo(eyes, dest);
    }

    public static void setServerRotation(float yaw, float pitch) {
        getInstance().setRotationSilent(yaw, pitch);
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        float pitchRad = pitch * ((float) Math.PI / 180);
        float yawRad = -yaw * ((float) Math.PI / 180);

        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);

        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    /** Режим поворота. */
    public enum RotationMode {
        SILENT,
        SYNC,
        LOCK
    }
}