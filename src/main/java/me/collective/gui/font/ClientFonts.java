package me.collective.gui.font;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Кастомные TTF-шрифты клиента (assets/collective/font/*.json).
 * Display — Space Grotesk, Body — Inter, Labels — JetBrains Mono.
 * Рендерятся обычным TextRenderer через style-font, как ресурспак-шрифты.
 */
public final class ClientFonts {
    public static final Identifier GROTESK_TITLE = Identifier.of("collective", "grotesk_title");
    public static final Identifier INTER_BODY = Identifier.of("collective", "inter_body");
    public static final Identifier JBMONO_LABEL = Identifier.of("collective", "jbmono_label");

    private ClientFonts() {
    }

    public static Component text(String s, Identifier font) {
        return Component.literal(s).setStyle(Style.EMPTY.withFont(new StyleSpriteSource.Font(font)));
    }

    public static int width(TextRenderer tr, String s, Identifier font) {
        return tr.getWidth(text(s, font));
    }

    public static void draw(GuiGraphics ctx, TextRenderer tr, String s, Identifier font,
                           int x, int y, int color, boolean shadow) {
        ctx.drawText(tr, text(s, font), x, y, color, shadow);
    }

    public static void drawCentered(GuiGraphics ctx, TextRenderer tr, String s, Identifier font,
                                    int cx, int y, int color, boolean shadow) {
        draw(ctx, tr, s, font, cx - width(tr, s, font) / 2, y, color, shadow);
    }

    /** Ширина строки с трекингом (доп. пиксели между символами). */
    public static int trackedWidth(TextRenderer tr, String s, Identifier font, float tracking) {
        if (s.isEmpty()) return 0;
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += tr.getWidth(text(s.substring(i, i + 1), font));
            if (i < s.length() - 1) w += tracking;
        }
        return Math.round(w);
    }

    /** Трекинг-текст от левого края. */
    public static void drawTracked(GuiGraphics ctx, TextRenderer tr, String s, Identifier font,
                                   int x, int y, int color, float tracking, boolean shadow) {
        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = s.substring(i, i + 1);
            Component t = text(ch, font);
            ctx.drawText(tr, t, Math.round(cx), y, color, shadow);
            cx += tr.getWidth(t) + tracking;
        }
    }

    /** Трекинг-текст по центру. */
    public static void drawTrackedCentered(GuiGraphics ctx, TextRenderer tr, String s, Identifier font,
                                           int cx, int y, int color, float tracking, boolean shadow) {
        drawTracked(ctx, tr, s, font, cx - trackedWidth(tr, s, font, tracking) / 2,
            y, color, tracking, shadow);
    }
}