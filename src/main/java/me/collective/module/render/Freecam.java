package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Freecam extends Module {
    public static Freecam INSTANCE;

    public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1));

    private net.minecraft.client.world.ClientWorld cameraWorld;
    private net.minecraft.client.network.ClientPlayerEntity cameraPlayer;
    private Vec3d pos = Vec3d.ZERO;
    private Vec3d prevPos = Vec3d.ZERO;
    private float yaw = 0f;
    private float pitch = 0f;
    private float prevYaw = 0f;
    private float prevPitch = 0f;

    public Freecam() {
        super("Freecam", "Отсоединяет камеру от игрока для свободного полёта сквозь стены.", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        synchronizeCamera();
    }

    @Override
    public void onWorldChange() {
        cameraWorld = null;
        cameraPlayer = null;
        synchronizeCamera();
    }

    private void synchronizeCamera() {
        if (mc.world != cameraWorld || mc.player != cameraPlayer) {
            cameraWorld = mc.world;
            cameraPlayer = mc.player;
        } else {
            return;
        }
        if (mc.world != null && mc.player != null) {
            Vec3d eyePos = mc.player.getEyePos();
            pos = eyePos;
            prevPos = eyePos;
            yaw = mc.player.getYaw();
            pitch = mc.player.getPitch();
            prevYaw = yaw;
            prevPitch = pitch;
        }
    }

    @Override
    public void onDisable() {
        cameraWorld = null;
        cameraPlayer = null;
        pos = Vec3d.ZERO;
        prevPos = Vec3d.ZERO;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        synchronizeCamera();
        if (mc.player == null || mc.world == null) return;

        prevPos = pos;
        prevYaw = yaw;
        prevPitch = pitch;

        double s = speed.getValue();
        Vec3d forward = Vec3d.fromPolar(0, yaw).multiply(s);
        Vec3d strafe = Vec3d.fromPolar(0, yaw + 90).multiply(s);

        Vec3d move = Vec3d.ZERO;
        if (mc.options.forwardKey.isPressed()) move = move.add(forward);
        if (mc.options.backKey.isPressed()) move = move.subtract(forward);
        if (mc.options.leftKey.isPressed()) move = move.subtract(strafe);
        if (mc.options.rightKey.isPressed()) move = move.add(strafe);
        if (mc.options.jumpKey.isPressed()) move = move.add(0, s, 0);
        if (mc.options.sneakKey.isPressed()) move = move.subtract(0, s, 0);

        pos = pos.add(move);
    }

    public Vec3d getInterpolatedPos(float tickProgress) {
        synchronizeCamera();
        if (mc.player == null || mc.world == null) {
            return mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO;
        }
        double x = MathHelper.lerp(tickProgress, prevPos.x, pos.x);
        double y = MathHelper.lerp(tickProgress, prevPos.y, pos.y);
        double z = MathHelper.lerp(tickProgress, prevPos.z, pos.z);
        return new Vec3d(x, y, z);
    }

    public float getYaw() {
        synchronizeCamera();
        return yaw;
    }

    public float getPitch() {
        synchronizeCamera();
        return pitch;
    }

    /** Поворот от мыши (вызывается из миксина вместо игрока). */
    public void turn(double cursorDeltaX, double cursorDeltaY) {
        synchronizeCamera();
        if (mc.player == null || mc.world == null) return;
        // Знаки как в ванильном Entity.changeLookDirection, иначе оси
        // мыши оказываются инвертированными.
        yaw += (float) cursorDeltaX * 0.15F;
        pitch = MathHelper.clamp(pitch + (float) cursorDeltaY * 0.15F, -90f, 90f);
    }

    public float getInterpolatedYaw(float tickProgress) {
        synchronizeCamera();
        return prevYaw + MathHelper.wrapDegrees(yaw - prevYaw) * tickProgress;
    }

    public float getInterpolatedPitch(float tickProgress) {
        synchronizeCamera();
        return prevPitch + MathHelper.wrapDegrees(pitch - prevPitch) * tickProgress;
    }
}
