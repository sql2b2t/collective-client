package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * FreeLook: удержание камеры по кругу вокруг игрока мышью.
 *
 * <p>При активации автоматически включается вид от третьего лица, мышь
 * крутит **камеру**, а реальный поворот игрока остаётся нетронутым —
 * никаких ротаций не отправляется, поэтому модуль не флагается GrimAC.
 * При выключении восстанавливается прежний ракурс.
 */
public class FreeLook extends Module {
    public static FreeLook INSTANCE;

    public final NumberSetting distance = add(new NumberSetting("Distance", 6.0, 2.0, 15.0, 0.5));

    private CameraType prevPerspective;
    private float orbitYaw;
    private float orbitPitch;

    public FreeLook() {
        super("FreeLook", "Свободный обзор камеры мышью, не меняя поворот игрока.", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.options == null) {
            setToggled(false);
            return;
        }
        this.prevPerspective = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        this.orbitYaw = mc.player.getYRot();
        this.orbitPitch = mc.player.getXRot();
    }

    @Override
    public void onDisable() {
        if (mc.options != null && this.prevPerspective != null) {
            mc.options.setCameraType(this.prevPerspective);
        }
        this.prevPerspective = null;
    }

    /** Вызывается из MixinEntityLook: мOUTH двигает орбиту, игрок остаётся. */
    public void turn(double cursorDeltaX, double cursorDeltaY) {
        this.orbitYaw += (float) (cursorDeltaX * 0.15);
        this.orbitPitch = Mth.clamp(this.orbitPitch + (float) (cursorDeltaY * 0.15), -90.0f, 90.0f);
    }

    public boolean isActive() {
        return isToggled() && mc.player != null && mc.level != null;
    }

    /** Точка, где должна висеть камера (за спиной по орбите). */
    public Vec3 orbitPos(float tickProgress) {
        float dist = this.distance.getFloatValue();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(this.orbitPitch, this.orbitYaw);
        return eye.add(dir.scale(-dist));
    }

    public float getOrbitYaw() {
        return this.orbitYaw;
    }

    public float getOrbitPitch() {
        return this.orbitPitch;
    }
}