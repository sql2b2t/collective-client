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
    public static void fillRounded(DrawContext ctx, float fx, float fy, float fw, float fh, int r, int color) {
        int x = (int) Math.round(fx);
        int y = (int) Math.round(fy);
        int w = (int) Math.round(fw);
        int h = (int) Math.round(fh);
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
    public static void roundedBorder(DrawContext ctx, float x, float y, float w, float h, int r,
                                     int borderColor, int fillColor) {
        fillRounded(ctx, x, y, w, h, r, borderColor);
        fillRounded(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fillColor);
    }

    /** Сплошной диск по сканлайнам (DrawContext, гарантированно работает в GUI-пассах). */
    public static void dot(DrawContext ctx, float cx, float cy, float r, int color) {
        if (r <= 0) return;
        int top = (int) Math.floor(cy - r);
        int bottom = Math.max(top, (int) Math.ceil(cy + r));
        for (int yy = top; yy <= bottom; yy++) {
            double dy = yy + 0.5 - cy;
            double span2 = r * r - dy * dy;
            if (span2 <= 0) continue;
            double dx = Math.sqrt(span2);
            ctx.fill((int) Math.round(cx - dx), yy, (int) Math.round(cx + dx) + 1, yy + 1, color);
        }
    }

    /** Кольцо толщиной ~1.2px по сканлайнам (внешняя и внутренняя дуги). */
    public static void dotRing(DrawContext ctx, float cx, float cy, float r, int color) {
        if (r <= 1f) {
            dot(ctx, cx, cy, r, color);
            return;
        }
        double ri = r - 1.2;
        int top = (int) Math.floor(cy - r);
        int bottom = (int) Math.ceil(cy + r);
        for (int yy = top; yy <= bottom; yy++) {
            double dy = yy + 0.5 - cy;
            double outer2 = r * r - dy * dy;
            if (outer2 <= 0) continue;
            double dxo = Math.sqrt(outer2);
            double inner2 = ri * ri - dy * dy;
            if (inner2 <= 0) {
                ctx.fill((int) Math.round(cx - dxo), yy, (int) Math.round(cx + dxo) + 1, yy + 1, color);
            } else {
                double dxi = Math.sqrt(inner2);
                ctx.fill((int) Math.round(cx - dxo), yy, (int) Math.round(cx - dxi) + 1, yy + 1, color);
                ctx.fill((int) Math.round(cx + dxi) + 1, yy, (int) Math.round(cx + dxo) + 1, yy + 1, color);
            }
        }
    }

    /** Скруглённая обводка 1px без заливки (линии + угловые сканпиксели). */
    public static void outlineRounded(DrawContext ctx, float fx, float fy, float fw, float fh, int r, int color) {
        int x = (int) Math.round(fx);
        int y = (int) Math.round(fy);
        int w = (int) Math.round(fw);
        int h = (int) Math.round(fh);
        if (w <= 2 || h <= 2) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fill(x, y, x + w, y, color);
            ctx.fill(x, y + h - 1, x + w, y + h, color);
            ctx.fill(x, y, x + 1, y + h, color);
            ctx.fill(x + w - 1, y, x + w, y + h, color);
            return;
        }
        ctx.fill(x + r, y, x + w - r, y + 1, color);
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + 1, y + h - r, color);
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            double dx = Math.sqrt(r * r - dy * dy);
            int inset = (int) Math.ceil(r - dx);
            ctx.fill(x + inset, y + i, x + inset + 1, y + i + 1, color);
            ctx.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, color);
            ctx.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
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
