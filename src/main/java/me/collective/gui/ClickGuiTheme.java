package me.collective.gui;

import me.collective.font.CFont;
import me.collective.font.ModernFonts;
import me.collective.module.client.Colors;
import me.collective.module.client.UI;

import java.awt.Color;

/**
 * Design system tokens and dynamic color synchronization for ClickGUI.
 * Fully synchronized in real-time with Colors module and UI module settings.
 */
public final class ClickGuiTheme {

    private ClickGuiTheme() {}

    // Panel Geometry
    public static final float PANEL_WIDTH = 114f;
    public static final float HEADER_HEIGHT = 22f;
    public static final float BUTTON_HEIGHT = 18f;
    public static final float SETTING_HEIGHT = 16f;
    public static final float SLIDER_HEIGHT = 24f;
    public static final float CORNER_RADIUS = 6f;

    // Static Base Colors
    public static final int BORDER_PANEL = 0x24FFFFFF;
    public static final int BORDER_PANEL_HOVER = 0x3EFFFFFF;
    public static final int SHADOW_COLOR = 0x44000000;
    public static final int TEXT_PRIMARY = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFF8898A8;
    public static final int TEXT_DARK = 0xFF141822;

    // Dynamic Theme Colors (Synchronized with Colors & UI modules in real-time)
    public static int getAccentColor() {
        if (Colors.INSTANCE != null && Colors.INSTANCE.isToggled()) {
            return Colors.INSTANCE.getThemeColor().getRGB();
        }
        if (UI.INSTANCE != null) {
            Color c = UI.INSTANCE.color.getValue();
            return (0xFF << 24) | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
        }
        return 0xFFBD99FF;
    }

    public static int getAccentBg() {
        int c = getAccentColor();
        return (c & 0x00FFFFFF) | 0x2A000000;
    }

    public static int getAccentStroke() {
        int c = getAccentColor();
        return (c & 0x00FFFFFF) | 0x66000000;
    }

    public static int getPanelHeaderBg() {
        return 0xE2141C26;
    }

    public static int getPanelBodyBg() {
        if (UI.INSTANCE != null) {
            Color bg = UI.INSTANCE.bgColor.getValue();
            int a = Math.max(90, Math.min(145, bg.getAlpha()));
            return (a << 24) | (bg.getRed() << 16) | (bg.getGreen() << 8) | bg.getBlue();
        }
        return 0x88050912;
    }

    public static int getButtonIdleBg() {
        if (UI.INSTANCE != null) {
            return UI.INSTANCE.bgButton.getValue().getRGB();
        }
        return 0x0EFFFFFF;
    }

    public static int getButtonHoverBg() {
        return 0x22FFFFFF;
    }

    public static int getButtonActiveBg() {
        if (UI.INSTANCE != null) {
            return UI.INSTANCE.bgEnabled.getValue().getRGB();
        }
        int c = getAccentColor();
        return (c & 0x00FFFFFF) | 0x36000000;
    }

    public static int getButtonActiveBorder() {
        int c = getAccentColor();
        return (c & 0x00FFFFFF) | 0x77000000;
    }

    public static int getTextActive() {
        if (UI.INSTANCE != null) {
            return UI.INSTANCE.enabledColor.getValue().getRGB();
        }
        return 0xFFFFFFFF;
    }

    public static int getTextIdle() {
        if (UI.INSTANCE != null) {
            return UI.INSTANCE.textColor.getValue().getRGB();
        }
        return 0xFFB4C2D0;
    }

    // Typography
    public static CFont title() {
        ModernFonts.ensureInitialized();
        return ModernFonts.fontTitle;
    }

    public static CFont tabs() {
        ModernFonts.ensureInitialized();
        return ModernFonts.fontTabs;
    }

    public static CFont name() {
        ModernFonts.ensureInitialized();
        return ModernFonts.fontName;
    }

    public static CFont badge() {
        ModernFonts.ensureInitialized();
        return ModernFonts.fontBadge;
    }

    public static CFont btn() {
        ModernFonts.ensureInitialized();
        return ModernFonts.fontBtn;
    }

    public static CFont search() {
        ModernFonts.ensureInitialized();
        return ModernFonts.fontSearch;
    }
}