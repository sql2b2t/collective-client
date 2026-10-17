package me.collective.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Примитивы Minimal Dark: скруглённые прямоугольники (построчный
 * круг), мягкие тени и свечения слоями с падающей альфой.
 */
public final class GuiGfx {
    private GuiGfx() {
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Скруглённый прямоугольник. r ограничивается половиной меньшей стороны. */
    public static void fillRounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
        double rr = r;
        for (int i = 0; i < r; i++) {
            double dy = rr - i - 0.5;
            double dx = Math.sqrt(rr * rr - dy * dy);
            int inset = (int) Math.ceil(rr - dx);
            ctx.fill(x + inset, y + i, x + r, y + i + 1, color);
            ctx.fill(x + w - r, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + r, y + h - i, color);
            ctx.fill(x + w - r, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Скруглённая обводка 1px: сначала бордер, поверх заливка с отступом. */
    public static void roundedBorder(DrawContext ctx, int x, int y, int w, int h, int r,
                                     int borderColor, int fillColor) {
        fillRounded(ctx, x, y, w, h, r, borderColor);
        fillRounded(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fillColor);
    }

    /**
     * Мягкая тень/свечение: слои наружу с падающей альфой.
     * rgb — цвет без альфы (0xF59E0B), baseAlpha — альфа внутреннего слоя.
     */
    public static void softGlow(DrawContext ctx, int x, int y, int w, int h, int r,
                                int rgb, int baseAlpha, int layers, int yOff) {
        int pure = rgb & 0x00FFFFFF;
        for (int k = layers; k >= 1; k--) {
            int a = Math.max(1, baseAlpha * (layers - k + 1) / layers / 2);
            fillRounded(ctx, x - k, y - k + yOff, w + 2 * k, h + 2 * k,
                r + k, pure | (a << 24));
        }
    }

    /** Тень вниз для карточек. */
    public static void shadow(DrawContext ctx, int x, int y, int w, int h, int r) {
        softGlow(ctx, x, y, w, h, r, 0x000000, 90, 3, 2);
    }

    /** Янтарное свечение для акцентов. */
    public static void amberGlow(DrawContext ctx, int x, int y, int w, int h, int r) {
        softGlow(ctx, x, y, w, h, r, 0xF59E0B, 70, 4, 0);
    }
}
