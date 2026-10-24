package me.collective.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Кадровый кэш матриц рендера мира.
 */
public final class RenderFrame {
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static Vec3 cameraPos = Vec3.ZERO;
    private static Frustum frustum;
    private static boolean valid;

    private RenderFrame() {
    }

    /** Вызывается из MixinWorldRenderer в начале каждого рендера мира. */
    public static void capture(CameraRenderState state) {
        if (state == null || state.viewRotationMatrix == null || state.projectionMatrix == null) return;
        VIEW_PROJ.set(state.projectionMatrix).mul(state.viewRotationMatrix);
        cameraPos = state.pos != null ? state.pos : Vec3.ZERO;
        frustum = state.cullFrustum;
        valid = true;
    }

    public static boolean isVisible(AABB box) {
        if (box == null) return false;
        return frustum == null || frustum.isVisible(box);
    }

    public static boolean isValid() {
        return valid;
    }

    public static Matrix4f getViewProj() {
        return VIEW_PROJ;
    }

    public static Vec3 getCameraPos() {
        return cameraPos;
    }

    /**
     * Мировая точка → экранные координаты в GUI-пикселях.
     *
     * @return null, если кадр ещё не захвачен или точка за камерой
     */
    public static Vector2f project(double x, double y, double z, int screenWidth, int screenHeight) {
        if (!valid) return null;
        Vector4f point = new Vector4f(
                (float) (x - cameraPos.x),
                (float) (y - cameraPos.y),
                (float) (z - cameraPos.z),
                1.0f).mul(VIEW_PROJ);
        if (point.w <= 0.05f) return null;
        return new Vector2f(
                (point.x / point.w + 1.0f) * 0.5f * screenWidth,
                (1.0f - point.y / point.w) * 0.5f * screenHeight);
    }
}