package me.collective.render;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Кадровый кэш матриц рендера мира.
 *
 * <p>Миксин сохраняет ровно те view/projection матрицы, которыми игра рисует
 * мир в текущем кадре: поворот камеры, FOV с множителем спринта/зума,
 * view-bobbing, hurt-tilt и эффект тошноты. Благодаря этому 2D-элементы
 * (NameTags, ItemESP) проецируются пиксель-в-пиксель с картинкой и не
 * дрожат при движении.
 */
public final class RenderFrame {
    private static final Matrix4f VIEW_PROJ = new Matrix4f();
    private static Vec3d cameraPos = Vec3d.ZERO;
    private static net.minecraft.client.render.Frustum frustum;
    private static boolean valid;

    private RenderFrame() {
    }

    /** Вызывается из MixinWorldRenderer в начале каждого рендера мира. */
    public static void capture(Camera camera, Matrix4f positionMatrix, Matrix4f projectionMatrix) {
        if (camera == null || positionMatrix == null || projectionMatrix == null) return;
        VIEW_PROJ.set(projectionMatrix).mul(positionMatrix);
        cameraPos = camera.getCameraPos();
        try {
            frustum = new net.minecraft.client.render.Frustum(positionMatrix, projectionMatrix);
            frustum.setPosition(cameraPos.x, cameraPos.y, cameraPos.z);
        } catch (Throwable ignored) {
            frustum = null;
        }
        valid = true;
    }

    public static boolean isVisible(net.minecraft.util.math.Box box) {
        if (box == null) return false;
        return frustum == null || frustum.isVisible(box);
    }

    public static boolean isValid() {
        return valid;
    }

    public static Matrix4f getViewProj() {
        return VIEW_PROJ;
    }

    public static Vec3d getCameraPos() {
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
