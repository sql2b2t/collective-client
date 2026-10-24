package me.collective.module.hud;

import me.collective.gui.GuiGfx;
import me.collective.module.client.HUD;
import me.collective.module.client.UI;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

/**
 * Единый визуальный язык HUD Collective: тёмные скруглённые плашки с тенью,
 * белый текст с приглушёнными подписями и акцент клиента (цвет из настройки UI).
 */
public final class HudTheme {
    private HudTheme() {
    }

    /** Фон плашки. */
    public static final Color PLATE = new Color(11, 11, 15, 190);
    /** Рамка плашки. */
    public static final Color BORDER = new Color(255, 255, 255, 26);
    /** Основной текст. */
    public static final Color TEXT = new Color(242, 245, 252);

    /**
     * Главный текст элемента под тему HUD: анимированный градиент/радуга из
     * настроек модуля HUD, как было до унификации. Без HUD — прежний белый.
     */
    public static Color text() {
        return text(0);
    }

    public static Color text(int index) {
        HUD hud = HUD.INSTANCE;
        if (hud != null && hud.isToggled()) {
            return hud.getHudColor(index);
        }
        return TEXT;
    }
    /** Приглушённый/служебный текст. */
    public static final Color MUTED = new Color(150, 154, 166);

    /** Шкала здоровья/прочности: хорошо, середина, опасно. */
    public static final Color GOOD = new Color(90, 230, 110);
    public static final Color MID = new Color(255, 212, 80);
    public static final Color BAD = new Color(255, 95, 95);

    /** Акцент клиента из настроек UI (с фолбэком на дефолтный цвет). */
    public static Color accent() {
        if (UI.INSTANCE != null && UI.INSTANCE.color.getValue() != null) {
            return UI.INSTANCE.color.getValue();
        }
        return new Color(189, 153, 255);
    }

    /** Плашка с тенью, заливкой и тонкой рамкой. */
    public static void plate(GuiGraphicsExtractor context, float x, float y, float w, float h, float r) {
        GuiGfx.shadow(context, (int) x, (int) y, (int) w, (int) h, (int) r);
        GuiGfx.fillRounded(context, x, y, w, h, r, PLATE.getRGB());
        GuiGfx.strokeRounded(context, x, y, w, h, r, 1f, BORDER.getRGB());
    }

    /** Цвет по доле 0..1 (1 = GOOD, к нулю спад к BAD). */
    public static Color ratio(float t) {
        t = Math.max(0f, Math.min(1f, t));
        if (t >= 0.5f) return lerp(MID, GOOD, (t - 0.5f) * 2f);
        return lerp(BAD, MID, t * 2f);
    }

    /** Линейный переход между двумя цветами. */
    public static Color lerp(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}