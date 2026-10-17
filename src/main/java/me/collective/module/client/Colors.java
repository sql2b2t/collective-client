package me.collective.module.client;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;

import java.awt.Color;

public class Colors extends Module {
    public static Colors INSTANCE;

    public final ColorSetting friendColor = add(new ColorSetting("FriendColor", new Color(85, 255, 85, 255)));
    public final ColorSetting enemyColor = add(new ColorSetting("EnemyColor", new Color(255, 85, 85, 255)));
    public final ColorSetting themeColor = add(new ColorSetting("ThemeColor", new Color(189, 153, 255, 255)));
    public final EnumSetting<Scheme> scheme = add(new EnumSetting<>("Scheme", Scheme.CUSTOM));

    public enum Scheme {
        CUSTOM, COLLECTIVE, PASTEL, RAINBOW, MONOCHROME, RED, BLUE, GREEN,
        PURPLE, CYAN, GOLD, SUNSET, ROSE, EMERALD, NEON_LIME, MIDNIGHT, CHERRY, LAVENDER
    }

    public Colors() {
        super("Colors", "Manages the client's global color system and theme.", Category.CLIENT);
        INSTANCE = this;
        setToggled(true);

        themeColor.setChangeListener(s -> applyTheme());
        scheme.setChangeListener(s -> applyTheme());
    }

    @Override
    public void onEnable() {
        applyTheme();
    }

    @Override
    public void onUpdate() {
        if (!isToggled()) return;
        if (scheme.getValue() == Scheme.RAINBOW) {
            float hue = (System.currentTimeMillis() % 4000L) / 4000.0f;
            Color rainbow = Color.getHSBColor(hue, 0.7f, 1.0f);
            if (UI.INSTANCE != null) {
                UI.INSTANCE.color.setValue(new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), 180));
                UI.INSTANCE.bgEnabled.setValue(new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), 140));
            }
        }
    }

    private boolean applyingTheme = false;

    public void applyTheme() {
        if (applyingTheme) return;
        applyingTheme = true;
        try {
            Scheme scheme = this.scheme.getValue();
            Color c;
            int uiAccentAlpha;
            switch (scheme) {
                case CUSTOM -> { c = themeColor.getValue(); uiAccentAlpha = c.getAlpha(); }
                case COLLECTIVE -> { c = new Color(189, 153, 255); uiAccentAlpha = 180; }
                case PASTEL -> { c = new Color(255, 179, 186); uiAccentAlpha = 180; }
                case RED -> { c = new Color(230, 40, 40); uiAccentAlpha = 180; }
                case BLUE -> { c = new Color(40, 140, 250); uiAccentAlpha = 180; }
                case GREEN -> { c = new Color(50, 220, 90); uiAccentAlpha = 180; }
                case MONOCHROME -> { c = new Color(200, 200, 200); uiAccentAlpha = 180; }
                case PURPLE -> { c = new Color(168, 85, 247); uiAccentAlpha = 180; }
                case CYAN -> { c = new Color(6, 182, 212); uiAccentAlpha = 180; }
                case GOLD -> { c = new Color(245, 158, 11); uiAccentAlpha = 180; }
                case SUNSET -> { c = new Color(255, 107, 53); uiAccentAlpha = 180; }
                case ROSE -> { c = new Color(244, 63, 94); uiAccentAlpha = 180; }
                case EMERALD -> { c = new Color(16, 185, 129); uiAccentAlpha = 180; }
                case NEON_LIME -> { c = new Color(132, 204, 22); uiAccentAlpha = 180; }
                case MIDNIGHT -> { c = new Color(99, 102, 241); uiAccentAlpha = 180; }
                case CHERRY -> { c = new Color(220, 38, 38); uiAccentAlpha = 180; }
                case LAVENDER -> { c = new Color(192, 132, 252); uiAccentAlpha = 180; }
                default -> { return; }
            }

            if (scheme != Scheme.CUSTOM) themeColor.setValueQuietly(c);

            // ClickGUI
            if (UI.INSTANCE != null) {
                UI.INSTANCE.color.setValue(new Color(c.getRed(), c.getGreen(), c.getBlue(), uiAccentAlpha));
                UI.INSTANCE.bgEnabled.setValue(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                        scheme == Scheme.CUSTOM ? 140 : 120));
            }

            // HUD: текст и градиент следуют теме
            if (HUD.INSTANCE != null) {
                HUD.INSTANCE.color.setValue(new Color(c.getRed(), c.getGreen(), c.getBlue(), 255));
                HUD.INSTANCE.secondColor.setValue(darken(c, 0.55f, 255));
            }
        } finally {
            applyingTheme = false;
        }
    }

    /** Приглушённая версия цвета для второй точки градиента HUD. */
    private static Color darken(Color c, float k, int alpha) {
        return new Color(
            Math.min(255, Math.round(c.getRed() * k)),
            Math.min(255, Math.round(c.getGreen() * k)),
            Math.min(255, Math.round(c.getBlue() * k)),
            alpha);
    }

    public Color getFriendColor() {
        return friendColor.getValue();
    }

    public Color getEnemyColor() {
        return enemyColor.getValue();
    }

    public Color getThemeColor() {
        return themeColor.getValue();
    }
}
