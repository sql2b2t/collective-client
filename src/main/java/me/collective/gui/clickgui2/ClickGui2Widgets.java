package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;
import static me.collective.gui.GuiGfx.dot;
import static me.collective.gui.GuiGfx.shadow;

/**
 * Отрисовщики элемента ClickGui2 — порт стиля из исходника:
 * скруглённые панели (радиусы 7–10), ховер с альфа-смешением
 * (hover 0.8 / idle 0.55 — как в mod0004), тумблер ACTIVE/DEACTIVE_STATE,
 * слайдер с круглым бегуном CIRCLE, кнопка "< значение >", колор-пикер с
 * полосой hue 6px и hex-подписью "#%02X%02X%02X%02X".
 */
public final class ClickGui2Widgets {
    private ClickGui2Widgets() {
    }

    // ── Базовая кнопка (mod0004): 28px высота, радиус 7, ховер-бленд ──

    public static void button(DrawContext ctx, float x, float y, float w, float h,
                              boolean hovered, ClickGui2Theme.Role fillRole,
                              int activeAlpha, int idleAlpha, int outlineAlpha) {
        int fill = hovered
                ? ClickGui2Theme.alpha(fillRole, activeAlpha)
                : ClickGui2Theme.alpha(fillRole, idleAlpha);
        float radius = Math.min(7f, h / 2f);
        fillRounded(ctx, x, y, w, h, radius, fill);
        outlineRounded(ctx, x, y, w, h, radius, ClickGui2Theme.alpha(ClickGui2Theme.Role.OUTLINE, outlineAlpha));
    }

    public static void buttonLabel(DrawContext ctx, String text, float x, float y, float h,
                                   boolean hovered, int size) {
        FontManager fm = FontManager.INSTANCE;
        fm.drawStringWithShadow(ctx, text, x, y + (h - fm.getFontHeightScaled(size)) / 2f,
                hovered ? ClickGui2Theme.c(ClickGui2Theme.Role.ACTIVE_TEXT) : ClickGui2Theme.c(ClickGui2Theme.Role.DEACTIVE_TEXT));
    }

    // ── Тумблер BooleanSetting ──

    public static void toggle(DrawContext ctx, float x, float y, float progress, boolean focused) {
        float w = 20f, h = 10f;
        float radius = h / 2f;

        int r = lerp(ClickGui2Theme.c(ClickGui2Theme.Role.DEACTIVE_STATE).getRed(), ClickGui2Theme.c(ClickGui2Theme.Role.ACTIVE_STATE).getRed(), progress);
        int g = lerp(ClickGui2Theme.c(ClickGui2Theme.Role.DEACTIVE_STATE).getGreen(), ClickGui2Theme.c(ClickGui2Theme.Role.ACTIVE_STATE).getGreen(), progress);
        int b = lerp(ClickGui2Theme.c(ClickGui2Theme.Role.DEACTIVE_STATE).getBlue(), ClickGui2Theme.c(ClickGui2Theme.Role.ACTIVE_STATE).getBlue(), progress);
        int a = lerp(ClickGui2Theme.c(ClickGui2Theme.Role.DEACTIVE_STATE).getAlpha(), 255, progress);

        fillRounded(ctx, x, y, w, h, radius, r << 16 | g << 8 | b | a << 24);
        outlineRounded(ctx, x, y, w, h, radius,
                focused ? ClickGui2Theme.alpha(ClickGui2Theme.Role.OUTLINE, 90) : ClickGui2Theme.rgb(ClickGui2Theme.Role.LINE));

        float cx = lerp(x + radius + 1f, x + w - radius - 1f, progress);
        dot(ctx, cx, y + h / 2f, 3.2f, ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE));
    }

    // ── Слайдер NumberSetting: тонкий трек LINE + бегунок CIRCLE ──

    public static void slider(DrawContext ctx, float x, float y, float w, float progress, boolean dragging, float size) {
        float trackH = 3f;
        fillRounded(ctx, x, y, w, trackH, trackH / 2f, ClickGui2Theme.rgb(ClickGui2Theme.Role.LINE));
        float knobX = x + w * Math.max(0f, Math.min(1f, progress));
        if (progress > 0.01f) {
            fillRounded(ctx, x, y, knobX - x, trackH, trackH / 2f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.ACTIVE_STATE, 200));
        }
        float knobR = dragging ? 4.2f : 3.4f;
        shadow(ctx, (int) (knobX - knobR - 2), (int) (y + trackH / 2f - knobR - 2), (int) (knobR * 2 + 4), (int) (knobR * 2 + 4), 2);
        dot(ctx, knobX, y + trackH / 2f, knobR, ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE));
    }

    // ── Переключатель режима "< значение >" ──

    public static void modeArrows(DrawContext ctx, float y, float h, float rightEdge, int color, float size) {
        FontManager fm = FontManager.INSTANCE;
        fm.drawStringWithShadow(ctx, "<", rightEdge - 22f, y + (h - fm.getFontHeightScaled(size)) / 2f, color);
        fm.drawStringWithShadow(ctx, ">", rightEdge - 8f, y + (h - fm.getFontHeightScaled(size)) / 2f, color);
    }

    // ── Плашка цвета (свотч) ──

    public static void swatch(DrawContext ctx, float x, float y, Color color, boolean open) {
        fillRounded(ctx, x, y, 14f, 10f, 3f, color.getRGB());
        outlineRounded(ctx, x, y, 14f, 10f, 3f,
                open ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 200) : ClickGui2Theme.rgb(ClickGui2Theme.Role.LINE));
    }

    // ── Полоса скролла ──

    public static void scrollBar(DrawContext ctx, float x, float y, float trackH, float thumbY, float thumbH) {
        fillRounded(ctx, x, y, 2f, trackH, 1f, ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, 60));
        if (thumbH > 4f) {
            fillRounded(ctx, x, y + thumbY, 2f, thumbH, 1f, ClickGui2Theme.rgb(ClickGui2Theme.Role.SCROLL));
        }
    }

    // ── Каретка текстового поля ──

    public static boolean caretNow() {
        return System.currentTimeMillis() / 500L % 2L == 0L;
    }

    /** Идентично исходнику: ENTER — сохранить (257/335), BACKSPACE — стереть (259). */
    public static boolean isEnter(int keyCode) {
        return keyCode == 257 || keyCode == 335;
    }

    public static boolean isBackspace(int keyCode) {
        return keyCode == 259;
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Плавное приближение кадровой анимации: dt в секундах, k — скорость. */
    public static float ease(float current, float target, float dt, float speed) {
        float factor = (float) Math.pow(0.0001, dt * speed);
        return target + (current - target) * factor;
    }
}
