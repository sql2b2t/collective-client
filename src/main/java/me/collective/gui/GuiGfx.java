package me.collective.gui;

import net.minecraft.client.gui.DrawContext;

/**
 * Примитивы Minimal Dark: скруглённые прямоугольники, обводки, диски,
 * мягкие тени и свечения. Неосевые кромки сглаживаются аналитически:
 * для каждого пикселя считается знаковое расстояние до фигуры (SDF),
 * а альфа пикселя — его покрытие фигурой (0.5 за вычетом расстояния).
 * Каждый пиксель красится ровно один раз, поэтому полупрозрачные
 * фигуры не темнеют вдвое, а углы не дают «лестницы».
 */
public final class GuiGfx {
    private GuiGfx() {
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Знаковое расстояние до границы скруглённого бокса (p относительно центра, hw/hh — полуразмеры). */
    private static float sdfRoundedBox(float px, float py, float cx, float cy, float hw, float hh, float r) {
        float qx = Math.abs(px - cx) - (hw - r);
        float qy = Math.abs(py - cy) - (hh - r);
        float ox = Math.max(qx, 0f);
        float oy = Math.max(qy, 0f);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
    }

    /** Покрытие пикселя по знаковому расстоянию (1 — полностью внутри). */
    private static float coverage(float d) {
        float c = 0.5f - d;
        return c <= 0f ? 0f : (c >= 1f ? 1f : c);
    }

    /** Цвет с альфой alphaBase, умноженной на покрытие cov (0 — прозрачный). */
    private static int alphaOf(int rgb, int alphaBase, float cov) {
        int a = (int) (alphaBase * cov + 0.5f);
        if (a <= 0) return 0;
        if (a > 255) a = 255;
        return rgb | (a << 24);
    }

    /** Скруглённый прямоугольник. radius ограничивается половиной меньшей стороны. */
    public static void fillRounded(DrawContext ctx, float fx, float fy, float fw, float fh, float radius, int color) {
        if (fw <= 0f || fh <= 0f) return;
        int alphaBase = (color >>> 24) & 0xFF;
        if (alphaBase == 0) return;
        int rgb = color & 0x00FFFFFF;
        float x0 = fx, y0 = fy, x1 = fx + fw, y1 = fy + fh;
        float r = Math.min(radius, Math.min(fw, fh) * 0.5f);
        if (r < 0.5f) {
            fillStraight(ctx, x0, y0, x1, y1, rgb, alphaBase);
            return;
        }
        float midX = (x0 + x1) * 0.5f, midY = (y0 + y1) * 0.5f;
        float hw = fw * 0.5f, hh = fh * 0.5f;
        int px0 = (int) Math.floor(x0), px1 = (int) Math.ceil(x1);
        int accY0 = -1, accY1 = -1;
        for (int y = (int) Math.floor(y0); y < (int) Math.ceil(y1); y++) {
            boolean vFull = y >= y0 && y + 1 <= y1;
            if (!vFull) {
                // Строка пересекает нижнюю/верхнюю прямую грань (дробная координата) — попиксельно.
                if (accY0 >= 0) {
                    fillStraight(ctx, x0, accY0, x1, accY1, rgb, alphaBase);
                    accY0 = -1;
                }
                sweepFill(ctx, px0, px1, y, midX, midY, hw, hh, r, rgb, alphaBase);
            } else if (y < y0 + r || y + 1 > y1 - r) {
                // Строка целиком в зоне дуг: SDF у углов, полный прогон в центре.
                if (accY0 >= 0) {
                    fillStraight(ctx, x0, accY0, x1, accY1, rgb, alphaBase);
                    accY0 = -1;
                }
                arcRow(ctx, px0, px1, x0, x1, y, midX, midY, hw, hh, r, rgb, alphaBase);
            } else {
                // Средняя зона: только прямые грани — копим строки и красим одним квадом.
                if (accY0 < 0) accY0 = y;
                accY1 = y + 1;
            }
        }
        if (accY0 >= 0) fillStraight(ctx, x0, accY0, x1, accY1, rgb, alphaBase);
    }

    /** Скруглённая обводка 1px без заливки. */
    public static void outlineRounded(DrawContext ctx, float fx, float fy, float fw, float fh, float radius, int color) {
        strokeRounded(ctx, fx, fy, fw, fh, radius, 1f, color);
    }

    /** Обводка произвольной толщины по границе скруглённого прямоугольника. */
    public static void strokeRounded(DrawContext ctx, float fx, float fy, float fw, float fh, float radius,
                                     float thickness, int color) {
        if (fw <= 0f || fh <= 0f) return;
        int alphaBase = (color >>> 24) & 0xFF;
        if (alphaBase == 0) return;
        int rgb = color & 0x00FFFFFF;
        if (fw <= 2f || fh <= 2f) {
            fillStraight(ctx, fx, fy, fx + fw, fy + fh, rgb, alphaBase);
            return;
        }
        float x0 = fx, y0 = fy, x1 = fx + fw, y1 = fy + fh;
        float r = Math.max(0f, Math.min(radius, Math.min(fw, fh) * 0.5f));
        float half = Math.max(0.25f, thickness) * 0.5f;
        float midX = (x0 + x1) * 0.5f, midY = (y0 + y1) * 0.5f;
        float hw = fw * 0.5f, hh = fh * 0.5f;
        int px0 = (int) Math.floor(x0 - half - 1f), px1 = (int) Math.ceil(x1 + half + 1f);
        int py0 = (int) Math.floor(y0 - half - 1f), py1 = (int) Math.ceil(y1 + half + 1f);
        float topA = y0 - half - 0.55f, topB = y0 + half + 0.55f;
        float botA = y1 - half - 0.55f, botB = y1 + half + 0.55f;
        for (int y = py0; y < py1; y++) {
            boolean nearStraight = (y + 1f > topA && y < topB) || (y + 1f > botA && y < botB);
            if (nearStraight) {
                // Около прямой горизонтальной грани — красим всю строку.
                sweepStroke(ctx, px0, px1, y, midX, midY, hw, hh, r, half, rgb, alphaBase);
            } else {
                // Около дуг/вертикальных граней — только окна слева и справа.
                int leftEnd = (int) Math.ceil(x0 + r + half + 1.1f);
                int rightStart = (int) Math.floor(x1 - r - half - 1.1f);
                sweepStroke(ctx, px0, Math.min(leftEnd, px1), y, midX, midY, hw, hh, r, half, rgb, alphaBase);
                sweepStroke(ctx, Math.max(rightStart, leftEnd), px1, y, midX, midY, hw, hh, r, half, rgb, alphaBase);
            }
        }
    }

    /** Скруглённая панель: бордер плюс заливка с отступом 1px. */
    public static void roundedBorder(DrawContext ctx, float x, float y, float w, float h, float r,
                                     int borderColor, int fillColor) {
        fillRounded(ctx, x, y, w, h, r, borderColor);
        fillRounded(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0f, r - 1f), fillColor);
    }

    /** Диск с аналитическим сглаживанием (DrawContext, работает в GUI-пассах). */
    public static void dot(DrawContext ctx, float cx, float cy, float r, int color) {
        if (r <= 0f) return;
        int alphaBase = (color >>> 24) & 0xFF;
        if (alphaBase == 0) return;
        int rgb = color & 0x00FFFFFF;
        int px0 = (int) Math.floor(cx - r - 1f), px1 = (int) Math.ceil(cx + r + 1f);
        int py0 = (int) Math.floor(cy - r - 1f), py1 = (int) Math.ceil(cy + r + 1f);
        for (int y = py0; y < py1; y++) {
            float dy = y + 0.5f - cy;
            int run = -1;
            for (int x = px0; x < px1; x++) {
                float dx = x + 0.5f - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float c = r + 0.5f - dist;
                if (c >= 1f) {
                    if (run < 0) run = x;
                } else if (run >= 0) {
                    ctx.fill(run, y, x, y + 1, rgb | (alphaBase << 24));
                    run = -1;
                    if (c > 0f) ctx.fill(x, y, x + 1, y + 1, alphaOf(rgb, alphaBase, c));
                } else if (c > 0f) {
                    ctx.fill(x, y, x + 1, y + 1, alphaOf(rgb, alphaBase, c));
                }
            }
            if (run >= 0) ctx.fill(run, y, px1, y + 1, rgb | (alphaBase << 24));
        }
    }

    /** Кольцо толщиной ~1.2px с аналитическим сглаживанием. */
    public static void dotRing(DrawContext ctx, float cx, float cy, float r, int color) {
        if (r <= 1f) {
            dot(ctx, cx, cy, r, color);
            return;
        }
        int alphaBase = (color >>> 24) & 0xFF;
        if (alphaBase == 0) return;
        int rgb = color & 0x00FFFFFF;
        float ri = r - 1.2f;
        int px0 = (int) Math.floor(cx - r - 1f), px1 = (int) Math.ceil(cx + r + 1f);
        int py0 = (int) Math.floor(cy - r - 1f), py1 = (int) Math.ceil(cy + r + 1f);
        for (int y = py0; y < py1; y++) {
            float dy = y + 0.5f - cy;
            int run = -1;
            for (int x = px0; x < px1; x++) {
                float dx = x + 0.5f - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float c = Math.min(r + 0.5f - dist, dist - ri + 0.5f);
                if (c >= 1f) {
                    if (run < 0) run = x;
                } else if (run >= 0) {
                    ctx.fill(run, y, x, y + 1, rgb | (alphaBase << 24));
                    run = -1;
                    if (c > 0f) ctx.fill(x, y, x + 1, y + 1, alphaOf(rgb, alphaBase, c));
                } else if (c > 0f) {
                    ctx.fill(x, y, x + 1, y + 1, alphaOf(rgb, alphaBase, c));
                }
            }
            if (run >= 0) ctx.fill(run, y, px1, y + 1, rgb | (alphaBase << 24));
        }
    }

    /**
     * Строка дуговой зоны: попиксельные окна у обоих углов плюс полный
     * центральный прогон (пиксели в нём гарантированно покрыты целиком).
     */
    private static void arcRow(DrawContext ctx, int px0, int px1, float x0, float x1, int y,
                               float midX, float midY, float hw, float hh, float r, int rgb, int alphaBase) {
        int rs = (int) Math.ceil(x0 + r);
        int re = (int) Math.floor(x1 - r);
        if (rs > re) {
            sweepFill(ctx, px0, px1, y, midX, midY, hw, hh, r, rgb, alphaBase);
            return;
        }
        sweepFill(ctx, px0, Math.min(rs, px1), y, midX, midY, hw, hh, r, rgb, alphaBase);
        ctx.fill(rs, y, re, y + 1, rgb | (alphaBase << 24));
        sweepFill(ctx, Math.max(re, rs), px1, y, midX, midY, hw, hh, r, rgb, alphaBase);
    }

    /** Попиксельный проход заливки по SDF; полностью покрытые пиксели объединяются в прогоны. */
    private static void sweepFill(DrawContext ctx, int from, int to, int y,
                                  float midX, float midY, float hw, float hh, float r, int rgb, int alphaBase) {
        int run = -1;
        for (int x = from; x < to; x++) {
            float d = sdfRoundedBox(x + 0.5f, y + 0.5f, midX, midY, hw, hh, r);
            float c = coverage(d);
            if (c >= 1f) {
                if (run < 0) run = x;
            } else {
                if (run >= 0) {
                    ctx.fill(run, y, x, y + 1, rgb | (alphaBase << 24));
                    run = -1;
                }
                if (c > 0f) ctx.fill(x, y, x + 1, y + 1, alphaOf(rgb, alphaBase, c));
            }
        }
        if (run >= 0) ctx.fill(run, y, to, y + 1, rgb | (alphaBase << 24));
    }

    /** Попиксельный проход обводки по SDF (кольцо вокруг границы). */
    private static void sweepStroke(DrawContext ctx, int from, int to, int y,
                                    float midX, float midY, float hw, float hh, float r, float half,
                                    int rgb, int alphaBase) {
        int run = -1;
        for (int x = from; x < to; x++) {
            float d = sdfRoundedBox(x + 0.5f, y + 0.5f, midX, midY, hw, hh, r);
            float c = half + 0.5f - Math.abs(d);
            if (c >= 1f) {
                if (run < 0) run = x;
            } else {
                if (run >= 0) {
                    ctx.fill(run, y, x, y + 1, rgb | (alphaBase << 24));
                    run = -1;
                }
                if (c > 0f) ctx.fill(x, y, x + 1, y + 1, alphaOf(rgb, alphaBase, c));
            }
        }
        if (run >= 0) ctx.fill(run, y, to, y + 1, rgb | (alphaBase << 24));
    }

    /**
     * Прямоугольник с покрытием дробных граней: до 9 квадов (частичная колонка
     * слева, полный прогон, частичная колонка справа × три полосы по высоте).
     */
    private static void fillStraight(DrawContext ctx, float x0, float y0, float x1, float y1, int rgb, int alphaBase) {
        if (x1 <= x0 || y1 <= y0) return;
        int cx0 = (int) Math.floor(x0), cx1 = (int) Math.ceil(x1) - 1;
        int cy0 = (int) Math.floor(y0), cy1 = (int) Math.ceil(y1) - 1;
        float covL = Math.min(1f, cx0 + 1f - x0);
        float covR = Math.min(1f, x1 - cx1);
        int runL = covL >= 1f ? cx0 : cx0 + 1;
        int runR = covR >= 1f ? cx1 + 1 : cx1;
        if (cx0 == cx1) {
            covL = Math.min(1f, x1 - x0);
            covR = 0f;
            runL = cx0 + 1;
            runR = cx0 + 1;
        }
        float covT = Math.min(1f, cy0 + 1f - y0);
        float covB = Math.min(1f, y1 - cy1);
        int runT = covT >= 1f ? cy0 : cy0 + 1;
        int runB = covB >= 1f ? cy1 + 1 : cy1;
        if (cy0 == cy1) {
            covT = Math.min(1f, y1 - y0);
            covB = 0f;
            runT = cy0 + 1;
            runB = cy0 + 1;
        }
        if (covL < 1f) {
            quad(ctx, cx0, cy0, cx0 + 1, runT, rgb, alphaBase * covL * covT);
            quad(ctx, cx0, runT, cx0 + 1, runB, rgb, alphaBase * covL);
            quad(ctx, cx0, runB, cx0 + 1, cy1 + 1, rgb, alphaBase * covL * covB);
        }
        quad(ctx, runL, cy0, runR, runT, rgb, alphaBase * covT);
        quad(ctx, runL, runT, runR, runB, rgb, (float) alphaBase);
        quad(ctx, runL, runB, runR, cy1 + 1, rgb, alphaBase * covB);
        if (covR < 1f && covR > 0f) {
            quad(ctx, cx1, cy0, cx1 + 1, runT, rgb, alphaBase * covR * covT);
            quad(ctx, cx1, runT, cx1 + 1, runB, rgb, alphaBase * covR);
            quad(ctx, cx1, runB, cx1 + 1, cy1 + 1, rgb, alphaBase * covR * covB);
        }
    }

    /** Квад с умножением альфы на покрытие; пустые и прозрачные не рисуются. */
    private static void quad(DrawContext ctx, int x0, int y0, int x1, int y1, int rgb, float alpha) {
        if (x1 <= x0 || y1 <= y0) return;
        int a = (int) (alpha + 0.5f);
        if (a <= 0) return;
        if (a > 255) a = 255;
        ctx.fill(x0, y0, x1, y1, rgb | (a << 24));
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
