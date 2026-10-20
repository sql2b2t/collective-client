package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

import static me.collective.gui.GuiGfx.dot;
import static me.collective.gui.GuiGfx.dotRing;
import static me.collective.gui.GuiGfx.fillRounded;
import static me.collective.gui.GuiGfx.outlineRounded;

/**
 * Кирпичи второго стиля (направление «premium glass»): стеклянные
 * подложки с верхним бликом, тумблер 32×18 r9 с аккуратным бегунком,
 * слайдер с мягким свечением, шевроны, каретка текстовых полей.
 */
public final class ClickGui2Widgets {
    private ClickGui2Widgets() {
    }

    public static boolean inRect(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Внутренний блик по верхней кромке стеклянной плоскости. */
    public static void edgeHighlight(DrawContext ctx, float x, float y, float w, float alphaMul) {
        int a = Math.round(26f * Math.max(0f, Math.min(1f, alphaMul)));
        RenderUtil.drawRect(ctx, x + 8f, y + 1f, x + w - 8f, y + 2f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.TEXT, a));
    }

    /** Тумблер-пилюля 32×18 r9: бегунок белый, включён — акцентная дорожка. */
    public static void togglePill(DrawContext ctx, float x, float y, float t, boolean hovered) {
        fillRounded(ctx, x, y, 32f, 18f, 9f,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, 120));
        if (t > 0.01f) {
            fillRounded(ctx, x + 2f - 1f * (1f - t), y + 2f, 28f + 2f * (1f - t), 14f, 7f,
                    ClickGui2Theme.fade(ClickGui2Theme.Role.ACTIVE_STATE, 0.92f * t));
        }
        int knob = ClickGui2Theme.blend(0xFFB9B4C6, 0xFFFFFFFF, t);
        fillRounded(ctx, x + 2.5f + 15f * t, y + 2.5f, 13f, 13f, 6.5f, knob);
        if (t > 0.05f) {
            dotRing(ctx, x + 2.5f + 15f * t + 6.5f, y + 2.5f + 6.5f, 4.4f,
                    ClickGui2Theme.fade(ClickGui2Theme.Role.ACTIVE_STATE, 0.5f * t));
        }
        if (hovered) {
            outlineRounded(ctx, x - 1f, y - 1f, 34f, 20f, 10f,
                    ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 100));
        }
    }

    /** Ромб сканлайнами — используется в иконке избранного. */
    public static void diamond(DrawContext ctx, float cx, float cy, float hw, float hh, int color) {
        for (float y0 = cy - hh; y0 < cy + hh; y0 += 1f) {
            float dist = Math.abs(y0 + 0.5f - cy);
            float half = Math.max(0f, hw * (1f - dist / hh));
            if (half <= 0f) continue;
            RenderUtil.drawRect(ctx, cx - half, y0, cx + half, y0 + 1f, color);
        }
    }

    /** Звезда/искры избранного: ромб + маленький ромб поперёк — восьмиконечная искра. */
    public static void starIcon(DrawContext ctx, float cx, float cy, float size, int color) {
        float half = size / 2f;
        diamond(ctx, cx, cy, half, half, color);
        diamond(ctx, cx, cy, half * 0.62f, half * 0.62f, color);
    }

    /**
     * Слайдер числа: тонкий трек 5px r2.5 + заполнение + круг радиусом 4.5
     * (+1.5 на драге/ховере) со свечением.
     */
    public static void sliderTrack(DrawContext ctx, float x, float y, float w, float t,
                                   boolean hovered, boolean dragging) {
        float trackH = 5f;
        fillRounded(ctx, x, y, w, trackH, trackH / 2f, ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, 80));
        float fillW = Math.max(trackH, w * Math.max(0f, Math.min(1f, t)));
        fillRounded(ctx, x, y, fillW, trackH, trackH / 2f,
                ClickGui2Theme.fade(ClickGui2Theme.Role.ACTIVE_STATE, 0.95f));
        float knobR = dragging ? 6f : hovered ? 5.2f : 4.5f;
        if (dragging || hovered) {
            GuiGfx.softGlow(ctx, (int) (x + fillW - knobR), (int) (y - (knobR - trackH / 2f)),
                    (int) (knobR * 2f), (int) (knobR * 2f), (int) knobR,
                    ClickGui2Theme.rgb(ClickGui2Theme.Role.ACTIVE_STATE) & 0xFFFFFF, 40, 2, 0);
        }
        dot(ctx, x + fillW, y + trackH / 2f, knobR, ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE));
    }

    /** Стрелочка-шеврон вниз (для «Выбрать»): построчный треугольник 8×4. */
    public static void chevronDown(DrawContext ctx, float cx, float y, float scale, int color) {
        int rows = Math.max(2, Math.round(4f * scale));
        for (int i = 0; i < rows; i++) {
            float half = rows - 1 - i;
            RenderUtil.drawRect(ctx, cx - half, y + i * scale, cx + half, y + (i + 1) * scale, color);
        }
    }

    /** Боковой шеврон (вправо сложен, влево раскрыт): индикатор раскрытия карточки. */
    public static void chevronSide(DrawContext ctx, float cx, float cy, boolean right, int color, float strength) {
        if (strength <= 0.02f) return;
        color = ClickGui2Theme.fade(color, Math.min(1f, strength));
        int cols = 3;
        for (int i = 0; i < cols; i++) {
            float half = Math.max(0f, (cols - i) * 0.55f);
            float x = right ? cx + i - cols / 2f : cx - i + cols / 2f - 1f;
            RenderUtil.drawRect(ctx, x, cy - half, x + 1f, cy + half, color);
        }
    }

    /** Каретка текстового поля: 1px вертикаль цветом MAIN. */
    public static void caret(DrawContext ctx, float x, float yTop, float height) {
        RenderUtil.drawRect(ctx, x, yTop, x + 1f, yTop + height,
                ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 220));
    }

    public static float textWidth(String value, float sizePx) {
        return FontManager.INSTANCE.getStringWidthScaled(value, sizePx - 2f);
    }

    /** Тонкая линия-разделитель. */
    public static void hairline(DrawContext ctx, float x, float y, float w) {
        RenderUtil.drawRect(ctx, x, y, x + w, y + 1f, ClickGui2Theme.rgb(ClickGui2Theme.Role.LINE));
    }

    /** Круглый чип-подложка (для шеврона/звезды ховера). */
    public static void circleChip(DrawContext ctx, float cx, float cy, float r, int color) {
        fillRounded(ctx, cx - r, cy - r, r * 2f, r * 2f, r, color);
    }

    /** Свотч цвета 14×14 с мягким кольцом при открытии. */
    public static void colorSwatch(DrawContext ctx, float x, float y, Color color, boolean open) {
        int argb = open ? ClickGui2Theme.alpha(ClickGui2Theme.Role.MAIN, 200)
                : ClickGui2Theme.rgb(ClickGui2Theme.Role.LINE);
        int a = color.getAlpha();
        // шахматная подложка 3×3 делает прозрачность видимой
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                boolean light = ((i + j) & 1) == 0;
                int bg = light ? (30 << 24) | 0xFFFFFF : 0;
                RenderUtil.drawRect(ctx, x + i * 3.5f, y + j * 3.5f, x + (i + 1) * 3.5f, y + (j + 1) * 3.5f, bg);
            }
        }
        RenderUtil.drawRect(ctx, x, y, x + 14f, y + 14f, color.getRGB());
        outlineRounded(ctx, x - 0.5f, y - 0.5f, 15f, 15f, 3.5f, argb);
        if (a < 250) {
            dotRing(ctx, x + 7f, y + 7f, 3.2f, ClickGui2Theme.alpha(ClickGui2Theme.Role.TEXT, 140));
        }
    }

    /** Кнопка-«чип» с текстом (используется пикапом бинда). */
    public static void chip(DrawContext ctx, float x, float y, float w, float h, boolean hover, String label) {
        int bg = hover ? ClickGui2Theme.fade(ClickGui2Theme.Role.SELECTED, 2.2f)
                : ClickGui2Theme.alpha(ClickGui2Theme.Role.LINE, 90);
        fillRounded(ctx, x, y, w, h, Math.min(6f, h / 2f), bg);
        outlineRounded(ctx, x, y, w, h, Math.min(6f, h / 2f), ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));
        float tw = textWidth(label, 9f);
        FontManager.INSTANCE.drawStringWithShadow(ctx, label, x + (w - tw) / 2f, y + h / 2f - 3f,
                hover ? ClickGui2Theme.rgb(ClickGui2Theme.Role.TEXT) : ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT));
    }
}
