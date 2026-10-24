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

    private Perspective prevPerspective;
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
        this.prevPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        this.orbitYaw = mc.player.getYaw();
        this.orbitPitch = mc.player.getPitch();
    }

    @Override
    public void onDisable() {
        if (mc.options != null && this.prevPerspective != null) {
            mc.options.setPerspective(this.prevPerspective);
        }
        this.prevPerspective = null;
    }

    /** Вызывается из MixinEntityLook: мOUTH двигает орбиту, игрок остаётся. */
    public void turn(double cursorDeltaX, double cursorDeltaY) {
        this.orbitYaw += (float) (cursorDeltaX * 0.15);
        this.orbitPitch = MathHelper.clamp(this.orbitPitch + (float) (cursorDeltaY * 0.15), -90.0f, 90.0f);
    }

    public boolean isActive() {
        return isToggled() && mc.player != null && mc.world != null;
    }

    /** Точка, где должна висеть камера (за спиной по орбите). */
    public Vec3 orbitPos(float tickProgress) {
        float dist = this.distance.getFloatValue();
        Vec3 eye = mc.player.getCameraPosVec(tickProgress);
        Vec3 dir = Vec3.fromPolar(this.orbitPitch, this.orbitYaw);
        return eye.add(dir.multiply(-dist));
    }

    public float getOrbitYaw() {
        return this.orbitYaw;
    }

    public float getOrbitPitch() {
        return this.orbitPitch;
    }
}