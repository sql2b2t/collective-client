package me.collective.gui.clickgui2;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ролевая палитра второго стиля — перенос ролей исходника (mod0021):
 * каждая роль задаёт смысл (选中/hover/текст и т.д.), а не конкретный цвет,
 * поэтому любую роль можно переопределить через {@link #setRole}.
 * Значения по умолчанию — прошлельная лаванда на глубоком тёмном стекле,
 * совместимая с подписью классического стиля (UI.Color 189,153,255).
 */
public final class ClickGui2Theme {
    public enum Role {
        MAIN("Основной акцент"),
        MAIN_TWO("Второй акцент"),
        OUTLINE("Обводка панелей"),
        BACKGROUND("Задний фон карточек"),
        SHADOW("Свечение/тень"),
        LINE("Разделители"),
        SCROLL("Полоса скролла"),
        ACTIVE_STATE("Включённый тумблер"),
        DEACTIVE_STATE("Выключенный тумблер"),
        CIRCLE("Круг слайдера"),
        SELECTED("Подсветка выбранного"),
        ACTIVE_TEXT("Активный текст"),
        DEACTIVE_TEXT("Неактивный текст"),
        OPEN_SETTING_TEXT("Открытая настройка"),
        DEOPEN_SETTING_TEXT("Закрытая настройка"),
        TEXT("Текст"),
        SUR_TEXT("Под-текст"),
        CATEGORY_TEXT("Текст выбранной категории"),
        CATEGORY_SUR_TEXT("Под-текст категорий"),
        DESELECT_CATEGORY_TEXT("Текст невыбранной категории");

        public final String label;

        Role(String label) {
            this.label = label;
        }
    }

    private static final Map<Role, Color> colors = new EnumMap<>(Role.class);

    static {
        colors.put(Role.MAIN, new Color(189, 153, 255));
        colors.put(Role.MAIN_TWO, new Color(150, 125, 205));
        colors.put(Role.OUTLINE, new Color(255, 255, 255, 34));
        colors.put(Role.BACKGROUND, new Color(16, 15, 22, 216));
        colors.put(Role.SHADOW, new Color(0, 0, 0, 70));
        colors.put(Role.LINE, new Color(255, 255, 255, 22));
        colors.put(Role.SCROLL, new Color(255, 255, 255, 55));
        colors.put(Role.ACTIVE_STATE, new Color(189, 153, 255));
        colors.put(Role.DEACTIVE_STATE, new Color(120, 116, 138, 190));
        colors.put(Role.CIRCLE, new Color(250, 248, 255));
        colors.put(Role.SELECTED, new Color(255, 255, 255, 30));
        colors.put(Role.ACTIVE_TEXT, new Color(238, 240, 250));
        colors.put(Role.DEACTIVE_TEXT, new Color(134, 132, 152));
        colors.put(Role.OPEN_SETTING_TEXT, new Color(226, 228, 244));
        colors.put(Role.DEOPEN_SETTING_TEXT, new Color(158, 156, 178));
        colors.put(Role.TEXT, new Color(236, 238, 250));
        colors.put(Role.SUR_TEXT, new Color(148, 152, 168));
        colors.put(Role.CATEGORY_TEXT, new Color(242, 245, 252));
        colors.put(Role.CATEGORY_SUR_TEXT, new Color(138, 134, 158));
        colors.put(Role.DESELECT_CATEGORY_TEXT, new Color(112, 110, 132));
    }

    private ClickGui2Theme() {
    }

    /** Текущий цвет роли (ARGB). */
    public static Color c(Role role) {
        return colors.getOrDefault(role, Color.GRAY);
    }

    /** Цвет роли как непрозрачный int для обычных отрисовщиков. */
    public static int rgb(Role role) {
        return c(role).getRGB();
    }

    /** Цвет роли, переопределённый альфой (0..255). */
    public static int alpha(Role role, int alpha) {
        Color base = c(role);
        return (clamp8(alpha) << 24) | (base.getRGB() & 0x00FFFFFF);
    }

    /** Прямая интерполяция между двумя int-цветами по t (0..1) — как в исходнике. */
    public static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t);
        int r = lerp((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = lerp((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = lerp(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Тональное ослабление цвета: умножает альфу на множитель (как mod0692.西). */
    public static int fade(int argb, float mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * mul);
        return (clamp8(a) << 24) | (argb & 0x00FFFFFF);
    }

    /** Цвет роли с умноженной альфой — ховер/ввод панелей. */
    public static int fade(Role role, float mul) {
        return fade(c(role).getRGB(), mul);
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
