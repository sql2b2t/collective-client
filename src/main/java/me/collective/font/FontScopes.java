package me.collective.font;

import me.collective.module.ModuleManager;
import me.collective.module.client.Fonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/**
 * Скоупы кастомного шрифта (модуль Fonts): чат, боссбар, таб, инвентарь.
 * Отрисовка идёт через FontManager §-строками (стили сохраняются).
 */
public final class FontScopes {
    private FontScopes() {
    }

    private static boolean on(java.util.function.Function<Fonts, Boolean> scope) {
        Fonts fonts = ModuleManager.INSTANCE.getModule(Fonts.class);
        if (fonts == null || !fonts.isToggled()) return false;
        try {
            return scope.apply(fonts);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean chat() {
        Fonts fonts = ModuleManager.INSTANCE.getModule(Fonts.class);
        return fonts != null && fonts.isToggled() && fonts.chat.getValue();
    }

    public static boolean bossBar() {
        return on(f -> f.bossBar.getValue());
    }

    public static boolean tab() {
        return on(f -> f.tab.getValue());
    }

    public static boolean inventory() {
        return on(f -> f.inventory.getValue());
    }

    /** Отрисовка Component кастомным шрифтом (тень опционально). */
    public static void draw(GuiGraphics context, Component text, int x, int y, int color, boolean shadow) {
        String s = ChatFontUtil.toSectionString(text);
        if (shadow) FontManager.INSTANCE.drawStringWithShadow(context, s, x, y, color);
        else FontManager.INSTANCE.drawString(context, s, x, y, color);
    }
}