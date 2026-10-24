package me.collective.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Примитивы Minimal Dark: скруглённые прямоугольники, обводки, диски,
 * мягкие тени и свечения.
 *
 * <p>Скруглённые заливки и обводки рисуются текстурными масками: для каждого
 * радиуса однажды генерируется маленькая маска (диск или кольцо), в альфа-канал
 * которой записано аналитическое покрытие SDF. Углы фигуры — четыре квада с
 * этой маской (координаты целые, UV сдвинуты на полтекселя, поэтому каждый
 * фрагмент сэмплирует центр текселя и линейная фильтрация не размывает швы),
 * прямые части — обычные квады. Итог: константное число квадов на фигуру
 * вместо попиксельного перебора SDF.
 *
 * <p>Попиксельный аналитический проход сохранён как фолбэк на случай, если
 * создать маски не удалось.
 */
public final class GuiGfx {
    private GuiGfx() {
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    // ==================== Текстурные маски ====================

    /** Отступ от краёв маски в текселях — исключает сэмплирование за пределы канвы. */
    private static final int MASK_MARGIN = 2;
    /** Максимальный радиус, рисуемый маской; больше — попиксельный фолбэк. */
    private static final int MAX_MASK_RADIUS = 128;
    /** Лимит кэша масок; одноразовое создание текстур должно быть ограниченным. */
    private static final int MASK_CACHE_LIMIT = 64;

    private static volatile boolean masksUnavailable;
    private static final Map<Integer, Identifier> discMasks = new HashMap<>();
    private static final Map<Long, Identifier> ringMasks = new HashMap<>();

    private static void registerMask(Identifier id, String name, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(() -> name, image);
        texture.upload();
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }


    /**
     * Маска диска радиуса r: альфа текселя — аналитическое покрытие круга
     * с центром в середине канвы. null — использовать попиксельный фолбэк.
     */
    private static Identifier discMask(int r) {
        if (masksUnavailable || r <= 0 || r > MAX_MASK_RADIUS) return null;
        Identifier cached = discMasks.get(r);
        if (cached != null) return cached;
        if (discMasks.size() + ringMasks.size() >= MASK_CACHE_LIMIT) return null;
        try (NativeImage img = new NativeImage(2 * r + 2 * MASK_MARGIN, 2 * r + 2 * MASK_MARGIN, false)) {
            int size = 2 * r + 2 * MASK_MARGIN;
            float c = size * 0.5f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = x + 0.5f - c;
                    float dy = y + 0.5f - c;
                    float cov = Math.max(0f, Math.min(1f, r + 0.5f - (float) Math.sqrt(dx * dx + dy * dy)));
                    img.setPixel(x, y, (int) (cov * 255f + 0.5f) << 24 | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("collective", "gui/mask/disc_" + r);
            registerMask(id, "collective_gui_mask_disc_" + r, img);
            discMasks.put(r, id);
            return id;
        } catch (Throwable t) {
            masksUnavailable = true;
            return null;
        }
    }

    /**
     * Маска кольца обводки радиуса r с полушириной half: альфа текселя —
     * покрытие полосы |dist - r| &le; half. Кэшируется по (r, half*2).
     * null — использовать попиксельный фолбэк.
     */
    private static Identifier ringMask(int r, float half) {
        if (masksUnavailable || r <= 0 || r > MAX_MASK_RADIUS) return null;
        int halfHalves = Math.max(1, Math.round(half * 2f));
        Long key = ((long) r << 20) | halfHalves;
        Identifier cached = ringMasks.get(key);
        if (cached != null) return cached;
        if (discMasks.size() + ringMasks.size() >= MASK_CACHE_LIMIT) return null;
        float h = halfHalves * 0.5f;
        int outer = (int) Math.ceil(r + h + 0.5f);
        try (NativeImage img = new NativeImage(2 * outer + 2 * MASK_MARGIN, 2 * outer + 2 * MASK_MARGIN, false)) {
            int size = 2 * outer + 2 * MASK_MARGIN;
            float c = size * 0.5f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = x + 0.5f - c;
                    float dy = y + 0.5f - c;
                    float cov = Math.max(0f, Math.min(1f,
                        h + 0.5f - Math.abs((float) Math.sqrt(dx * dx + dy * dy) - r)));
                    img.setPixel(x, y, (int) (cov * 255f + 0.5f) << 24 | 0x00FFFFFF);
                }
            }
            Identifier id = Identifier.fromNamespaceAndPath("collective", "gui/mask/ring_" + r + "_" + halfHalves);
            registerMask(id, "collective_gui_mask_ring_" + r + "_" + halfHalves, img);
            ringMasks.put(key, id);
            return id;
        } catch (Throwable t) {
            masksUnavailable = true;
            return null;
        }
    }

    /**
     * Квад с наложением региона маски (u/v — старт региона от края канвы).
     * Размер региона в текселях равен размеру квада в пикселях, поэтому
     * фрагменты сэмплируют центры текселей один к одному.
     */
    private static void maskQuad(GuiGraphicsExtractor ctx, Identifier mask, int texSize,
                                 int x, int y, int size, float u, float v, int argb) {
        ctx.blit(RenderPipelines.GUI_TEXTURED, mask, x, y, u, v, size, size, size, size, texSize, texSize, argb);
    }

    /** Заливка угловой четверти (сторона size) маской диска. */
    private static void discCorner(GuiGraphicsExtractor ctx, Identifier mask, int r, int m,
                                   int x, int y, boolean right, boolean bottom, int argb) {
        int texSize = 2 * r + 2 * m;
        float u = (m + (right ? r : 0)) + 0.5f;
        float v = (m + (bottom ? r : 0)) + 0.5f;
        maskQuad(ctx, mask, texSize, x, y, r, u, v, argb);
    }

    /** Угловая заплатка обводки (сторона outer) маской кольца. */
    private static void ringCorner(GuiGraphicsExtractor ctx, Identifier mask, int outer, int m,
                                   int x, int y, boolean right, boolean bottom, int argb) {
        int texSize = 2 * outer + 2 * m;
        float u = (m + (right ? outer : 0)) + 0.5f;
        float v = (m + (bottom ? outer : 0)) + 0.5f;
        maskQuad(ctx, mask, texSize, x, y, outer, u, v, argb);
    }

    // ==================== Фигуры ====================

    /** Знаковое расстояние до границы скруглённого бокса (p относительно центра, hw/hh — полуразмеры). */
    private static float sdfRoundedBox(float px, float py, float cx, float cy, float hw, float hh, float r) {
        float qx = Math.abs(px - cx) - (hw - r);
        float qy = Math.abs(py - cy) - (hh - r);
        float ox = Math.max(qx, 0f);
        float oy = Math.max(qy, 0f);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
    }

    /** Цвет с альфой alphaBase, умноженной на покрытие cov (0 — прозрачный). */
    private static int alphaOf(int rgb, int alphaBase, float cov) {
        int a = (int) (alphaBase * cov + 0.5f);
        if (a <= 0) return 0;
        if (a > 255) a = 255;
        return rgb | (a << 24);
    }

    /** Скруглённый прямоугольник. radius ограничивается половиной меньшей стороны. */
    public static void fillRounded(GuiGraphicsExtractor ctx, float fx, float fy, float fw, float fh, float radius, int color) {
        if (fw <= 0f || fh <= 0f) return;
        int alphaBase = (color >>> 24) & 0xFF;
        if (alphaBase == 0) return;
        int rgb = color & 0x00FFFFFF;
        float rClamped = Math.min(radius, Math.min(fw, fh) * 0.5f);
        if (rClamped < 1f) {
            fillStraight(ctx, fx, fy, fx + fw, fy + fh, rgb, alphaBase);
            return;
        }
        // Целая геометрия: маски сэмплируются по центрам текселей и требуют целых квадов.
        int x0 = Math.round(fx);
        int y0 = Math.round(fy);
        int x1 = Math.round(fx + fw);
        int y1 = Math.round(fy + fh);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) {
            fillStraight(ctx, fx, fy, fx + fw, fy + fh, rgb, alphaBase);
            return;
        }
        int r = Math.min(Math.round(rClamped), Math.min(w, h) / 2);
        Identifier mask = r >= 1 && r <= MAX_MASK_RADIUS ? discMask(r) : null;
        if (mask == null) {
            coarseFillRounded(ctx, fx, fy, fw, fh, rClamped, rgb, alphaBase);
            return;
        }
        int m = MASK_MARGIN;
        // Прямые части: центральная колонна во всю высоту плюс боковые пояса.
        fillStraight(ctx, x0 + r, y0, x1 - r, y1, rgb, alphaBase);
        fillStraight(ctx, x0, y0 + r, x0 + r, y1 - r, rgb, alphaBase);
        fillStraight(ctx, x1 - r, y0 + r, x1, y1 - r, rgb, alphaBase);
        // Четыре угла маской диска.
        discCorner(ctx, mask, r, m, x0, y0, false, false, rgb | (alphaBase << 24));
        discCorner(ctx, mask, r, m, x1 - r, y0, true, false, rgb | (alphaBase << 24));
        discCorner(ctx, mask, r, m, x0, y1 - r, false, true, rgb | (alphaBase << 24));
        discCorner(ctx, mask, r, m, x1 - r, y1 - r, true, true, rgb | (alphaBase << 24));
    }

    /** Скруглённая обводка 1px без заливки. */
    public static void outlineRounded(GuiGraphicsExtractor ctx, float fx, float fy, float fw, float fh, float radius, int color) {
        strokeRounded(ctx, fx, fy, fw, fh, radius, 1f, color);
    }

    /** Обводка произвольной толщины по границе скруглённого прямоугольника. */
    public static void strokeRounded(GuiGraphicsExtractor ctx, float fx, float fy, float fw, float fh, float radius,
                                     float thickness, int color) {
        if (fw <= 0f || fh <= 0f) return;
        int alphaBase = (color >>> 24) & 0xFF;
        if (alphaBase == 0) return;
        int rgb = color & 0x00FFFFFF;
        if (fw <= 2f || fh <= 2f) {
            fillStraight(ctx, fx, fy, fx + fw, fy + fh, rgb, alphaBase);
            return;
        }
        float half = Math.max(0.25f, thickness) * 0.5f;
        // Целая геометрия под маски.
        int x0 = Math.round(fx);
        int y0 = Math.round(fy);
        int x1 = Math.round(fx + fw);
        int y1 = Math.round(fy + fh);
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) {
            fillStraight(ctx, fx, fy, fx + fw, fy + fh, rgb, alphaBase);
            return;
        }
        int r = Math.min(Math.round(Math.max(0f, radius)), Math.min(w, h) / 2);
        int outer = (int) Math.ceil(r + half + 0.5f);
        Identifier mask = r >= 1 && outer <= MAX_MASK_RADIUS ? ringMask(r, half) : null;
        if (mask == null) {
            // Фолбэк обводки только для умеренных радиусов (мелкая область);
            // очень большие радиусы зажимаем — крупные обводки нигде не нужны.
            strokeRoundedLegacy(ctx, fx, fy, fw, fh, Math.min(radius, MAX_MASK_RADIUS), half, rgb, alphaBase);
            return;
        }
        int m = MASK_MARGIN;
        // Прямые части кольца: четыре полосы по толщине.
        fillStraight(ctx, x0 + r, y0 - half, x1 - r, y0 + half, rgb, alphaBase);
        fillStraight(ctx, x0 + r, y1 - half, x1 - r, y1 + half, rgb, alphaBase);
        fillStraight(ctx, x0 - half, y0 + r, x0 + half, y1 - r, rgb, alphaBase);
        fillStraight(ctx, x1 - half, y0 + r, x1 + half, y1 - r, rgb, alphaBase);
        // Углы — квадратные заплатки маской кольца вокруг центров дуг.
        ringCorner(ctx, mask, outer, m, x0 + r - outer, y0 + r - outer, false, false, rgb | (alphaBase << 24));
        ringCorner(ctx, mask, outer, m, x1 - r, y0 + r - outer, true, false, rgb | (alphaBase << 24));
        ringCorner(ctx, mask, outer, m, x0 + r - outer, y1 - r, false, true, rgb | (alphaBase << 24));
        ringCorner(ctx, mask, outer, m, x1 - r, y1 - r, true, true, rgb | (alphaBase << 24));
    }

    /** Скруглённая панель: бордер плюс заливка с отступом 1px. */
    public static void roundedBorder(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r,
                                     int borderColor, int fillColor) {
        fillRounded(ctx, x, y, w, h, r, borderColor);
        fillRounded(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0f, r - 1f), fillColor);
    }

    /** Диск с аналитическим сглаживанием (GuiGraphicsExtractor, работает в GUI-пассах). */
    public static void dot(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color) {
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
    public static void dotRing(GuiGraphicsExtractor ctx, float cx, float cy, float r, int color) {
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

    // ==================== Попиксельный фолбэк ====================

    /** Фолбэк обводки: аналитический SDF-проход кольца вокруг границы. */
    private static void strokeRoundedLegacy(GuiGraphicsExtractor ctx, float fx, float fy, float fw, float fh,
                                            float radius, float half, int rgb, int alphaBase) {
        float x0 = fx, y0 = fy, x1 = fx + fw, y1 = fy + fh;
        float r = Math.max(0f, Math.min(radius, Math.min(fw, fh) * 0.5f));
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

    /** Попиксельный проход обводки по SDF (кольцо вокруг границы). */
    private static void sweepStroke(GuiGraphicsExtractor ctx, int from, int to, int y,
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
     * Геометрический фолбэк без попиксельных SDF-проходов: прямые пояса —
     * точные квад-пояса, углы — стопки полос, вычисленных из уравнения
     * окружности (консервативно по дальнему краю полосы). Радиус ≤16 рисуется
     * полосами по пикселю (визуально совпадает с кругом), крупные —
     * до 20 полос на угол; шаг >1px незаметен на мягких заливках.
     */
    private static void coarseFillRounded(GuiGraphicsExtractor ctx, float fx, float fy, float fw, float fh,
                                          float radius, int rgb, int alphaBase) {
        float x0 = fx, y0 = fy, x1 = fx + fw, y1 = fy + fh;
        float r = Math.max(0f, Math.min(radius, Math.min(fw, fh) * 0.5f));
        if (r < 0.5f) {
            fillStraight(ctx, x0, y0, x1, y1, rgb, alphaBase);
            return;
        }
        // прямые пояса: центральная колонна во всю высоту плюс боковые
        fillStraight(ctx, x0 + r, y0, x1 - r, y1, rgb, alphaBase);
        fillStraight(ctx, x0, y0 + r, x0 + r, y1 - r, rgb, alphaBase);
        fillStraight(ctx, x1 - r, y0 + r, x1, y1 - r, rgb, alphaBase);
        // центры четырёх дуг
        int k = r <= 16f ? (int) Math.ceil(r) : Math.max(8, Math.min(20, (int) Math.ceil(r / 12f)));
        cornerStrips(ctx, x0 + r, y0 + r, r, -1, -1, k, rgb, alphaBase);
        cornerStrips(ctx, x1 - r, y0 + r, r, 1, -1, k, rgb, alphaBase);
        cornerStrips(ctx, x0 + r, y1 - r, r, -1, 1, k, rgb, alphaBase);
        cornerStrips(ctx, x1 - r, y1 - r, r, 1, 1, k, rgb, alphaBase);
    }

    /**
     * Полосы одной четверти: центр дуги (cx, cy), dirX/dirY — направления
     * наружу (к углу коробки). Хорда берётся на дальнем крае полосы —
     * заливка слегка внутрь диска, без наружного выпирания.
     */
    private static void cornerStrips(GuiGraphicsExtractor ctx, float cx, float cy, float r,
                                     float dirX, float dirY, int k, int rgb, int alphaBase) {
        float step = r / k;
        for (int i = 0; i < k; i++) {
            float dFar = (i + 1) * step;
            float half = (float) Math.sqrt(Math.max(0f, r * r - dFar * dFar));
            float y0, y1;
            if (dirY < 0f) {
                y0 = Math.round(cy - dFar);
                y1 = Math.round(cy - i * step);
            } else {
                y0 = Math.round(cy + i * step);
                y1 = Math.round(cy + dFar);
            }
            if (y1 <= y0) continue;
            float x0, x1;
            if (dirX < 0f) {
                x0 = Math.round(cx - half);
                x1 = Math.round(cx);
            } else {
                x0 = Math.round(cx);
                x1 = Math.round(cx + half);
            }
            if (x1 <= x0) continue;
            ctx.fill((int) x0, (int) y0, (int) x1, (int) y1, rgb | (alphaBase << 24));
        }
    }

    /**
     * Прямоугольник с покрытием дробных граней: до 9 квадов (частичная колонка
     * слева, полный прогон, частичная колонка справа × три полосы по высоте).
     */
    private static void fillStraight(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1, int rgb, int alphaBase) {
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
    private static void quad(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int rgb, float alpha) {
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
    public static void softGlow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r,
                                int rgb, int baseAlpha, int layers, int yOff) {
        int pure = rgb & 0x00FFFFFF;
        for (int k = layers; k >= 1; k--) {
            int a = Math.max(1, baseAlpha * (layers - k + 1) / layers / 2);
            fillRounded(ctx, x - k, y - k + yOff, w + 2 * k, h + 2 * k,
                r + k, pure | (a << 24));
        }
    }

    /** Тень вниз для карточек. */
    public static void shadow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r) {
        softGlow(ctx, x, y, w, h, r, 0x000000, 90, 3, 2);
    }

    /** Янтарное свечение для акцентов. */
    public static void amberGlow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r) {
        softGlow(ctx, x, y, w, h, r, 0xF59E0B, 70, 4, 0);
    }
}