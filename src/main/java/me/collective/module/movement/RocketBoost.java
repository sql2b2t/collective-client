package me.collective.module.movement;

import me.collective.category.Category;
import me.collective.command.ChatHelper;
import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.BaritoneHelper;
import me.collective.util.RotationUtils;
import me.collective.util.ViaProtocolUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

public class RocketBoost extends Module {
    public static RocketBoost INSTANCE;

    private static final double GRIM_FIREWORK_LIMIT = 1.7;
    private int pitchFreeTicks;
    private int headingPinnedTicks;

    private static final double ANTI_TICK_SKIPPING = 0.05;
    private static final double MAX_PLAUSIBLE_MOVEMENT = 40.0;

    public final NumberSetting speed = add(new NumberSetting("Speed", 9.5, 0.1, 20.0, 0.1));
    public final NumberSetting amount = add(new NumberSetting("Amount", 1.7, 0.1, 2.0, 0.05));
    public final NumberSetting alignment = add(new NumberSetting("Alignment", 15.0, 0.0, 90.0, 1.0));
    public final BooleanSetting requireInput = add(new BooleanSetting("RequireInput", false));
    public final BooleanSetting debug = add(new BooleanSetting("Debug", false));
    public final BooleanSetting wallCheck = add(new BooleanSetting("WallCheck", true));
    public final NumberSetting lookahead = add(new NumberSetting("Lookahead", 10, 2, 40, 1));
    public final BooleanSetting chunkCheck = add(new BooleanSetting("ChunkCheck", true));
    public final BooleanSetting pauseInFluid = add(new BooleanSetting("PauseInFluid", true));
    public final BooleanSetting baritoneSync = add(new BooleanSetting("BaritoneSync", true));

    private Vec3d lastMovement = Vec3d.ZERO;
    private Vec3d prevPos = null;
    private Vec3d lastGlidePos = null;
    private volatile boolean repositioned = false;
    private boolean windowOpen = false;
    private int latchedRocketId = -1;
    private int latchGraceTicks = 0;
    private int suppressTicks = 0;
    private int travellingTicks = 0;
    private String state = "не планирует";
    private String limiter = "speed";
    private int appliedInWindow = 0;
    private int debugTicks = 0;
    private double appliedSpeed = 0.0;
    private double appliedOffAim = 0.0;
    private boolean syncedToBaritone = false;

    public RocketBoost() {
        super("RocketBoost", "Увеличивает скорость планирования на элитрах до максимальных лимитов фейерверков GrimAC.", Category.MOVEMENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        lastMovement = mc.player == null ? Vec3d.ZERO : mc.player.getVelocity();
        prevPos = mc.player == null ? null : mc.player.getEntityPos();
        lastGlidePos = null;
        repositioned = false;
        windowOpen = false;
        latchedRocketId = -1;
        latchGraceTicks = 0;
        suppressTicks = 0;
        travellingTicks = 0;
        state = "не планирует";
        appliedInWindow = 0;
        debugTicks = 0;
    }

    @Override
    public void onDisable() {
        windowOpen = false;
        latchedRocketId = -1;
        latchGraceTicks = 0;
        prevPos = null;
        lastGlidePos = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) {
            repositioned = true;
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        if (!mc.player.isGliding()) {
            lastGlidePos = null;
        }
        trackWindow();
        if (suppressTicks > 0) {
            suppressTicks--;
        }
        if (travellingTicks > 0) {
            travellingTicks--;
        }
        keepAimFresh();

        // Tick post debug logging
        Vec3d pos = mc.player.getEntityPos();
        Vec3d travelled = prevPos == null ? Vec3d.ZERO : pos.subtract(prevPos);
        if (travelled.length() > MAX_PLAUSIBLE_MOVEMENT) {
            travelled = Vec3d.ZERO;
        }
        prevPos = pos;
        if (debug.getValue() && ++debugTicks >= 20) {
            double bpt = travelled.length();
            if (appliedInWindow > 0) {
                String past = amount.getValue() > GRIM_FIREWORK_LIMIT ? " - ВНЕ окна, ждите откатов" : "";
                ChatHelper.sendMessage(String.format("%.0f км/ч / %.0f б/с (%.2f б/т) - буст %d/%d тиков на %.2f, %.1f° от прицела, лимит %s%s%s",
                    bpt * 72.0, bpt * 20.0, bpt, appliedInWindow, debugTicks, appliedSpeed, appliedOffAim, limiter, past,
                    syncedToBaritone ? " - выпрямлено на линию Baritone" : ""));
            } else {
                ChatHelper.sendMessage(String.format("%.0f км/ч / %.0f б/с (%.2f б/т) - простой: %s", bpt * 72.0, bpt * 20.0, bpt, state));
            }
            debugTicks = 0;
            appliedInWindow = 0;
        }
    }

    private void keepAimFresh() {
        if (!(mc.player.isGliding() && windowOpen && allowed())) {
            return;
        }
        RotationUtils.getInstance().keepWireFresh();
    }

    private void trackWindow() {
        if (!mc.player.isGliding()) {
            windowOpen = false;
            latchedRocketId = -1;
            latchGraceTicks = 0;
            return;
        }
        FireworkRocketEntity attached = findAttachedRocket();
        if (attached != null) {
            latchedRocketId = attached.getId();
            latchGraceTicks = 1;
            windowOpen = true;
            return;
        }
        Entity latched = latchedRocketId == -1 ? null : mc.world.getEntityById(latchedRocketId);
        if (latched instanceof FireworkRocketEntity firework && firework.isAlive()) {
            windowOpen = true;
        } else if (latchGraceTicks > 0) {
            latchGraceTicks--;
            windowOpen = true;
        } else {
            windowOpen = false;
            latchedRocketId = -1;
        }
    }

    public Vec3d glideVelocity(Vec3d oldVelocity, Vec3d vanilla) {
        if (mc.player == null || mc.world == null) {
            return null;
        }
        Vec3d pos = mc.player.getEntityPos();
        Vec3d start = lastGlidePos == null ? oldVelocity : pos.subtract(lastGlidePos);
        boolean desynced = lastGlidePos != null && (repositioned || start.length() > MAX_PLAUSIBLE_MOVEMENT);
        repositioned = false;
        lastGlidePos = pos;
        lastMovement = start;
        if (desynced) {
            return note("ресинхрон после телепорта");
        }
        if (!mc.player.isGliding()) {
            return note("не планирует");
        }
        if (!allowed()) {
            return note("стоим на месте - нажмите клавишу движения");
        }
        if (!windowOpen) {
            return note("нет живого фейерверка");
        }
        if (pauseInFluid.getValue() && (mc.player.isTouchingWater() || mc.player.isInLava())) {
            return note("в жидкости");
        }
        RotationUtils rotations = RotationUtils.getInstance();
        float pitch = rotations.getSentPitch();
        Vec3d look = Vec3d.fromPolar(pitch, rotations.getSentYaw());
        if (look.lengthSquared() < 1.0E-9) {
            return note("нет направления взгляда");
        }
        Vec3d velocity = rideBox(start, look, pitch, throttledSpeed(look));
        if (velocity == null) {
            return note("нет места в окне");
        }
        if (velocity.dotProduct(look) <= vanilla.dotProduct(look)) {
            return note("ванила быстрее на этом курсе");
        }
        state = "буст";
        syncedToBaritone = baritoneSync.getValue() && baritoneFlying();
        appliedSpeed = velocity.length();
        appliedOffAim = Math.toDegrees(Math.acos(MathHelper.clamp(velocity.dotProduct(look) / Math.max(appliedSpeed, 1.0E-9), -1.0, 1.0)));
        appliedInWindow++;
        return velocity;
    }

    private Vec3d note(String why) {
        state = why;
        return null;
    }

    private Vec3d rideBox(Vec3d start, Vec3d look, float pitch, double cap) {
        RotationUtils rotations = RotationUtils.getInstance();
        Vec3d lastLook = Vec3d.fromPolar(rotations.getServerPitch(), rotations.getServerYaw());
        return rideWindow(start, look, lastLook, pitch, effectiveGravity(start.y), amount.getValue(), antiTickSkipping(), effectiveAlignment(), cap);
    }

    public static Vec3d rideWindow(Vec3d start, Vec3d look, Vec3d lastLook, float pitch, double gravity, double threshold, double anti, double alignmentDeg, double cap) {
        double[] box = axisBox(look, lastLook, threshold, anti);
        Vec3d predicted = predictGliding(start, look, pitch, gravity);
        double[] w = new double[]{
            predicted.x + Math.min(0.0, box[0] - start.x), predicted.y + Math.min(0.0, box[1] - start.y), predicted.z + Math.min(0.0, box[2] - start.z),
            predicted.x + Math.max(0.0, box[3] - start.x), predicted.y + Math.max(0.0, box[4] - start.y), predicted.z + Math.max(0.0, box[5] - start.z)
        };
        Vec3d heading = heading(look, w, alignmentDeg, anti);
        double reach = reach(heading, w, anti);
        if (reach <= 1.0E-9) {
            return null;
        }
        Vec3d ride = heading.multiply(Math.min(reach, cap));
        return new Vec3d(
            MathHelper.clamp(ride.x, w[0], w[3]),
            MathHelper.clamp(ride.y, w[1], w[4]),
            MathHelper.clamp(ride.z, w[2], w[5])
        );
    }

    private static Vec3d heading(Vec3d look, double[] w, double alignmentDeg, double anti) {
        double slack = Math.toRadians(alignmentDeg);
        if (slack <= 1.0E-6) {
            return look;
        }
        Vec3d corner = new Vec3d(axisEdge(look.x, w[0], w[3], anti), axisEdge(look.y, w[1], w[4], anti), axisEdge(look.z, w[2], w[5], anti));
        double len = corner.length();
        if (len <= 1.0E-9) {
            return look;
        }
        Vec3d target = corner.multiply(1.0 / len);
        double angle = Math.acos(MathHelper.clamp(target.dotProduct(look), -1.0, 1.0));
        if (angle <= slack) {
            return target;
        }
        if (angle >= Math.PI - 1.0E-6) {
            return look;
        }
        double t = slack / angle;
        double sin = Math.sin(angle);
        return look.multiply(Math.sin((1.0 - t) * angle) / sin).add(target.multiply(Math.sin(t * angle) / sin));
    }

    private static double reach(Vec3d dir, double[] w, double anti) {
        double reach = Double.MAX_VALUE;
        reach = Math.min(reach, axisReach(dir.x, w[0], w[3], anti));
        reach = Math.min(reach, axisReach(dir.y, w[1], w[4], anti));
        reach = Math.min(reach, axisReach(dir.z, w[2], w[5], anti));
        return reach == Double.MAX_VALUE ? 0.0 : Math.max(0.0, reach);
    }

    private static double axisReach(double dir, double lo, double hi, double anti) {
        if (dir > anti) {
            return hi <= 0.0 ? 0.0 : hi / dir;
        }
        if (dir < -anti) {
            return lo >= 0.0 ? 0.0 : lo / dir;
        }
        return Double.MAX_VALUE;
    }

    private static double axisEdge(double dir, double lo, double hi, double anti) {
        if (dir > anti) {
            return hi;
        }
        if (dir < -anti) {
            return lo;
        }
        return MathHelper.clamp(0.0, lo, hi);
    }

    public static double antiTickSkipping() {
        return ViaProtocolUtil.isLegacyBand(ViaProtocolUtil.targetProtocol()) ? 0.0 : ANTI_TICK_SKIPPING;
    }

    private static double[] axisBox(Vec3d look, Vec3d lastLook, double threshold, double a) {
        double minX = Math.min(-a, look.x) + Math.min(-a, lastLook.x);
        double minY = Math.min(-a, look.y) + Math.min(-a, lastLook.y);
        double minZ = Math.min(-a, look.z) + Math.min(-a, lastLook.z);
        double maxX = Math.max(a, look.x) + Math.max(a, lastLook.x);
        double maxY = Math.max(a, look.y) + Math.max(a, lastLook.y);
        double maxZ = Math.max(a, look.z) + Math.max(a, lastLook.z);
        return new double[]{
            Math.max(-threshold, minX * threshold), Math.max(-threshold, minY * threshold), Math.max(-threshold, minZ * threshold),
            Math.min(threshold, maxX * threshold), Math.min(threshold, maxY * threshold), Math.min(threshold, maxZ * threshold)
        };
    }

    public static Vec3d predictGliding(Vec3d old, Vec3d look, float pitchDeg, double gravity) {
        float pitchRad = pitchDeg * ((float) Math.PI / 180);
        double horizSqrt = Math.sqrt(look.x * look.x + look.z * look.z);
        double horizLen = old.horizontalLength();
        double vertCos = Math.cos(pitchRad);
        vertCos = vertCos * vertCos * Math.min(1.0, look.length() / 0.4);
        Vec3d v = old.add(0.0, gravity * (-1.0 + vertCos * 0.75), 0.0);
        if (v.y < 0.0 && horizSqrt > 0.0) {
            double d = v.y * -0.1 * vertCos;
            v = v.add(look.x * d / horizSqrt, d, look.z * d / horizSqrt);
        }
        if (pitchRad < 0.0f && horizSqrt > 0.0) {
            double d = horizLen * (double) (-MathHelper.sin(pitchRad)) * 0.04;
            v = v.add(-look.x * d / horizSqrt, d * 3.2, -look.z * d / horizSqrt);
        }
        if (horizSqrt > 0.0) {
            v = v.add((look.x / horizSqrt * horizLen - v.x) * 0.1, 0.0, (look.z / horizSqrt * horizLen - v.z) * 0.1);
        }
        return v.multiply(0.99f, 0.98f, 0.99f);
    }

    private double effectiveGravity(double velocityY) {
        double gravity = mc.player.getAttributeValue(EntityAttributes.GRAVITY);
        if (velocityY <= 0.0 && mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            return Math.min(gravity, 0.01);
        }
        return gravity;
    }

    private double throttledSpeed(Vec3d look) {
        double capped = speed.getValue();
        int ticks = lookahead.getValue().intValue();
        double reach = Math.max(4.0, capped * ticks);
        Vec3d eye = mc.player.getEyePos();
        limiter = "speed";
        if (wallCheck.getValue()) {
            Vec3d end = eye.add(look.multiply(reach));
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS) {
                double limit = hit.getBlockPos().toCenterPos().distanceTo(eye) / ticks;
                if (limit < capped) {
                    capped = limit;
                    limiter = "впереди стена";
                }
            }
        }
        if (chunkCheck.getValue()) {
            for (double d = 16.0; d <= reach; d += 16.0) {
                Vec3d at = eye.add(look.multiply(d));
                BlockPos atPos = BlockPos.ofFloored(at);
                if (mc.world.isChunkLoaded(atPos.getX() >> 4, atPos.getZ() >> 4)) continue;
                if (!(d / ticks < capped)) break;
                capped = d / ticks;
                limiter = "незагруженные чанки";
                break;
            }
        }
        return capped;
    }

    private boolean allowed() {
        if (suppressTicks > 0) {
            return false;
        }
        if (!requireInput.getValue()) {
            return true;
        }
        if (travellingTicks > 0) {
            return true;
        }
        if (baritoneSync.getValue() && baritoneFlying()) {
            return true;
        }
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed() || mc.options.jumpKey.isPressed() || mc.options.sneakKey.isPressed();
    }

    public void suppress() {
        this.suppressTicks = 3;
    }

    public void clearSuppression() {
        this.suppressTicks = 0;
    }

    public void declareTravelling() {
        this.travellingTicks = 3;
    }

    private boolean baritoneFlying() {
        return BaritoneHelper.isElytraProcessActive();
    }

    private double effectiveAlignment() {
        return baritoneSync.getValue() && baritoneFlying() ? 0.0 : alignment.getValue();
    }

    public double alignmentDegrees() {
        return this.effectiveAlignment();
    }

    public double windowThreshold() {
        return amount.getValue();
    }

    public boolean hasWindow() {
        return this.isToggled() && windowOpen;
    }

    private FireworkRocketEntity findAttachedRocket() {
        FireworkRocketEntity best = null;
        int bestRemaining = Integer.MIN_VALUE;
        Box box = new Box(mc.player.getEntityPos().subtract(64, 64, 64), mc.player.getEntityPos().add(64, 64, 64));
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box, e -> e instanceof FireworkRocketEntity);
        for (Entity entity : entities) {
            FireworkRocketEntity firework = (FireworkRocketEntity) entity;
            if (!firework.isAlive()) continue;
            if (firework.shooter != mc.player) continue;
            int remaining = remainingLife(firework);
            if (remaining <= bestRemaining) continue;
            bestRemaining = remaining;
            best = firework;
        }
        return best;
    }

    private int remainingLife(FireworkRocketEntity firework) {
        return firework.lifeTime - firework.life;
    }

    public static int earliestDeath(FireworkRocketEntity firework) {
        FireworksComponent fireworks = firework.getStack().get(DataComponentTypes.FIREWORKS);
        int duration = fireworks != null ? fireworks.flightDuration() : 1;
        return 10 * duration + 1 - firework.life;
    }

    public void declarePitchFree() {
        this.pitchFreeTicks = 3;
    }

    public void declareHeadingPinned() {
        this.headingPinnedTicks = 3;
    }

    public double pitchAlignmentDegrees() {
        return this.pitchFreeTicks > 0 ? 90.0 : this.alignment.getValue();
    }
}
