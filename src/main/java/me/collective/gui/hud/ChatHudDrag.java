package me.collective.gui.hud;

import me.collective.module.hud.HudModule;
import me.collective.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Перетаскивание HUD-модулей прямо поверх открытого чата.
 *
 * <p>Экран чата занимает нижнюю часть; клики в зоне чата (низ экрана и
 * поле ввода) продолжают работать как чат, а клик по HUD-модулю выше зоны
 * чата начинает перетаскивание. Позиция каждый кадр берётся из координат
 * курсора (мировые модули рисуются каждый кадр вне зависимости от экрана),
 * поэтому отдельный обработчик mouseDragged не нужен.
 */
public final class ChatHudDrag {
    private ChatHudDrag() {
    }

    /** Насколько высота чата перекрыта для перетаскивания (пиксели от низа). */
    private static final int CHAT_PROTECTED_ZONE = 150;

    private static HudModule dragging;
    private static float offsetX;
    private static float offsetY;
    private static long lastMoveNanos;

    /** Пытается захватить HUD-модуль под курсором. Возвращает true, если захватила. */
    public static boolean tryBeginDrag(Click click) {
        if (click.button() != 0) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.world == null) return false;

        double mx = click.x();
        double my = click.y();

        // Зона чата (история + поле ввода) остаётся чату
        if (my >= mc.getWindow().getScaledHeight() - CHAT_PROTECTED_ZONE) return false;

        HudModule target = findUnderCursor(mx, my);
        if (target == null) return false;

        dragging = target;
        offsetX = (float) (mx - target.getX());
        offsetY = (float) (my - target.getY());
        lastMoveNanos = System.nanoTime();
        return true;
    }

    /** Обновляет позицию модуля по текущему курсору; гасит по отпусканию кнопки. */
    public static void update(double mouseX, double mouseY) {
        if (dragging == null) return;

        long window = Minecraft.getInstance().getWindow().getHandle();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            endDrag();
            return;
        }

        float mw = Math.max(20, dragging.getWidth());
        float mh = Math.max(10, dragging.getHeight());
        float screenW = Minecraft.getInstance().getWindow().getScaledWidth();
        float screenH = Minecraft.getInstance().getWindow().getScaledHeight();

        float nx = Math.max(2, Math.min(screenW - mw - 2, (float) mouseX - offsetX));
        float ny = Math.max(2, Math.min(screenH - mh - 2, (float) mouseY - offsetY));

        dragging.setX(nx);
        dragging.setY(ny);
        lastMoveNanos = System.nanoTime();
    }

    /** Вызывается каждый кадр рендера чата: страховка от «залипшего» драга. */
    public static void tickStale() {
        if (dragging != null && System.nanoTime() - lastMoveNanos > 250_000_000L) {
            endDrag();
        }
    }

    /** Модуль который сейчас перетаскивается (для подсветки рамкой). */
    public static HudModule draggingModule() {
        return dragging;
    }

    /** Вызывается при закрытии чата: снимаем захват. */
    public static void endDrag() {
        dragging = null;
    }

    private static HudModule findUnderCursor(double mx, double my) {
        for (HudModule module : ModuleManager.INSTANCE.getHudModules()) {
            if (!module.isToggled()) continue;
            if (module.isHovered(mx, my)) return module;
        }
        return null;
    }
}