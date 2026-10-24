package me.collective.module.render;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Freecam extends Module {
    public static Freecam INSTANCE;

    public final NumberSetting speed = add(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1));

    private net.minecraft.client.multiplayer.ClientLevel cameraWorld;
    private net.minecraft.client.player.LocalPlayer cameraPlayer;
    private Vec3 pos = Vec3.ZERO;
    private Vec3 prevPos = Vec3.ZERO;
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
        if (mc.level != cameraWorld || mc.player != cameraPlayer) {
            cameraWorld = mc.level;
            cameraPlayer = mc.player;
        } else {
            return;
        }
        if (mc.level != null && mc.player != null) {
            Vec3 eyePos = mc.player.getEyePosition();
            pos = eyePos;
            prevPos = eyePos;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            prevYaw = yaw;
            prevPitch = pitch;
        }
    }

    @Override
    public void onDisable() {
        cameraWorld = null;
        cameraPlayer = null;
        pos = Vec3.ZERO;
        prevPos = Vec3.ZERO;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        synchronizeCamera();
        if (mc.player == null || mc.level == null) return;

        prevPos = pos;
        prevYaw = yaw;
        prevPitch = pitch;

        double s = speed.getValue();
        Vec3 forward = Vec3.directionFromRotation(0, yaw).scale(s);
        Vec3 strafe = Vec3.directionFromRotation(0, yaw + 90).scale(s);

        Vec3 move = Vec3.ZERO;
        if (mc.options.keyUp.isDown()) move = move.add(forward);
        if (mc.options.keyDown.isDown()) move = move.subtract(forward);
        if (mc.options.keyLeft.isDown()) move = move.subtract(strafe);
        if (mc.options.keyRight.isDown()) move = move.add(strafe);
        if (mc.options.keyJump.isDown()) move = move.add(0, s, 0);
        if (mc.options.keyShift.isDown()) move = move.subtract(0, s, 0);

        pos = pos.add(move);
    }

    public Vec3 getInterpolatedPos(float tickProgress) {
        synchronizeCamera();
        if (mc.player == null || mc.level == null) {
            return mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO;
        }
        double x = Mth.lerp(tickProgress, prevPos.x, pos.x);
        double y = Mth.lerp(tickProgress, prevPos.y, pos.y);
        double z = Mth.lerp(tickProgress, prevPos.z, pos.z);
        return new Vec3(x, y, z);
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
        if (mc.player == null || mc.level == null) return;
        // Знаки как в ванильном Entity.changeLookDirection, иначе оси
        // мыши оказываются инвертированными.
        yaw += (float) cursorDeltaX * 0.15F;
        pitch = Mth.clamp(pitch + (float) cursorDeltaY * 0.15F, -90f, 90f);
    }

    public float getInterpolatedYaw(float tickProgress) {
        synchronizeCamera();
        return prevYaw + Mth.wrapDegrees(yaw - prevYaw) * tickProgress;
    }

    public float getInterpolatedPitch(float tickProgress) {
        synchronizeCamera();
        return prevPitch + Mth.wrapDegrees(pitch - prevPitch) * tickProgress;
    }
}