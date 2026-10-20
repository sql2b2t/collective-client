package me.collective.gui.clickgui2;

import java.awt.Color;
import java.util.EnumMap;

/**
 * Палитра ClickGui2 — порт ролей из отрисованного исходника
 * (MAIN/OUTLINE/BACKGROUND/SHADOW/LINE/SCROLL/ACTIVE_STATE/DEACTIVE_STATE/
 * CIRCLE/SELECTED/ACTIVE_TEXT/DEACTIVE_TEXT/OPEN_SETTING_TEXT/DEOPEN_SETTING_TEXT/
 * TEXT/SUR_TEXT/CATEGORY_*), с дефолтами sotto стилистику Collective.
 * Как и в источнике, любой цвет можно переопределить (см. {@link #setRole}).
 */
public final class ClickGui2Theme {
    public enum Role {
        MAIN, MAIN_TWO, OUTLINE, BACKGROUND, SHADOW, LINE, SCROLL,
        ACTIVE_STATE, DEACTIVE_STATE, CIRCLE, SELECTED,
        ACTIVE_TEXT, DEACTIVE_TEXT,
        OPEN_SETTING_TEXT, DEOPEN_SETTING_TEXT,
        TEXT, SUR_TEXT,
        CATEGORY_TEXT, CATEGORY_SUR_TEXT, DESELECT_CATEGORY_TEXT
    }

    private static final EnumMap<Role, Color> colors = new EnumMap<>(Role.class);

    static {
        // Дефолты подобраны под стилистику:o тёмное стекло + лавандовый акцент
        colors.put(Role.MAIN, new Color(189, 153, 255));
        colors.put(Role.MAIN_TWO, new Color(150, 125, 205));
        colors.put(Role.OUTLINE, new Color(255, 255, 255, 34));
        colors.put(Role.BACKGROUND, new Color(12, 11, 18, 222));
        colors.put(Role.SHADOW, new Color(0, 0, 0, 70));
        colors.put(Role.LINE, new Color(255, 255, 255, 22));
        colors.put(Role.SCROLL, new Color(255, 255, 255, 55));
        colors.put(Role.ACTIVE_STATE, new Color(189, 153, 255));
        colors.put(Role.DEACTIVE_STATE, new Color(120, 116, 138, 190));
        colors.put(Role.CIRCLE, new Color(250, 248, 255));
        colors.put(Role.SELECTED, new Color(255, 255, 255, 30));
        colors.put(Role.ACTIVE_TEXT, new Color(242, 245, 252));
        colors.put(Role.DEACTIVE_TEXT, new Color(138, 134, 158));
        colors.put(Role.OPEN_SETTING_TEXT, new Color(242, 245, 252));
        colors.put(Role.DEOPEN_SETTING_TEXT, new Color(168, 165, 188));
        colors.put(Role.TEXT, new Color(242, 245, 252));
        colors.put(Role.SUR_TEXT, new Color(150, 154, 166));
        colors.put(Role.CATEGORY_TEXT, new Color(242, 245, 252));
        colors.put(Role.CATEGORY_SUR_TEXT, new Color(138, 134, 158));
        colors.put(Role.DESELECT_CATEGORY_TEXT, new Color(110, 108, 130));
    }

    private ClickGui2Theme() {
    }

    public static Color c(Role role) {
        Color color = colors.get(role);
        return color != null ? color : Role.BACKGROUND != null ? colors.get(Role.BACKGROUND) : Color.GRAY;
    }

    public static int rgb(Role role) {
        return c(role).getRGB();
    }

    /** Цвет роли с изменённой альфой (без мутации исходного). */
    public static int alpha(Role role, int alpha) {
        Color base = c(role);
        return alpha << 24 | (base.getRed() & 0xFF) << 16 | (base.getGreen() & 0xFF) << 8 | (base.getBlue() & 0xFF);
    }

    /** Затемнение уже непрозрачного цвета (для вертикального градиента заголовка). */
    public static Color derive(Color base, float darkFactor, int alpha) {
        int r = Math.round(base.getRed() * darkFactor);
        int g = Math.round(base.getGreen() * darkFactor);
        int b = Math.round(base.getBlue() * darkFactor);
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b), Math.max(0, Math.min(255, alpha)));
    }

    public static void setRole(Role role, Color color) {
        if (role != null && color != null) {
            colors.put(role, color);
        }
    }

    /** «Готовые темы» — пресет №1: Collective по умолчанию. */
    public static void presetDefault() {
        setRole(Role.MAIN, new Color(189, 153, 255));
        setRole(Role.MAIN_TWO, new Color(150, 125, 205));
        setRole(Role.BACKGROUND, new Color(12, 11, 18, 222));
        setRole(Role.SHADOW, new Color(0, 0, 0, 70));
    }

    /** Пресет №2: тёплая вишня. */
    public static void presetCherry() {
        setRole(Role.MAIN, new Color(255, 128, 138));
        setRole(Role.MAIN_TWO, new Color(210, 100, 112));
        setRole(Role.BACKGROUND, new Color(20, 11, 14, 226));
        setRole(Role.SHADOW, new Color(0, 0, 0, 78));
    }

    /** Пресет №3: холодный мятный. */
    public static void presetMint() {
        setRole(Role.MAIN, new Color(120, 230, 200));
        setRole(Role.MAIN_TWO, new Color(95, 190, 168));
        setRole(Role.BACKGROUND, new Color(10, 18, 17, 224));
        setRole(Role.SHADOW, new Color(0, 0, 0, 70));
    }
}
