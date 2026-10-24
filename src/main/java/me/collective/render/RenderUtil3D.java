package me.collective.render;

import org.joml.Vector2f;

/**
 * Проецирование мировых координат на экран через кадровый кэш {@link RenderFrame}:
 * используется та же цепочка матриц, что и у отрендеренного мира, поэтому
 * 2D-элементы над сущностями (NameTags, ItemESP, TargetESP) совпадают
 * с картинкой и не дрожат при движении.
 */
public class RenderUtil3D {
    /**
     * Мировая точка → экранные координаты в GUI-пикселях.
     *
     * @return null, если кадр мира ещё не отрисован или точка за камерой
     */
    public static Vector2f worldToScreen(double x, double y, double z, int screenWidth, int screenHeight) {
        return RenderFrame.project(x, y, z, screenWidth, screenHeight);
    }
}