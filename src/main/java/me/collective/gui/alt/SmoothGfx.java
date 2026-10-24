package me.collective.gui.alt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import me.collective.mixin.AbstractTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * High-DPI anti-aliased vector rendering designed specifically for Minecraft's GuiGraphics pipeline.
 * Uses calibrated texture dimensions (64x64 for rounded boxes/strokes/shadows, 32x32 for circles/icons)
 * with continuous subpixel SDF coverage (2.2 texels transition) and hardware bilinear filtering.
 * Eliminates downscaling undersampling aliasing (pixels), edge cutoffs, and seam gaps.
 */
public final class SmoothGfx {

    private static final int BOX_TEX_SIZE = 64;
    private static final int ICON_TEX_SIZE = 32;

    private static Identifier CIRCLE_ID;
    private static Identifier ROUND_BOX_ID;
    private static Identifier ROUND_STROKE_ID;
    private static Identifier SHADOW_BOX_ID;
    private static Identifier SEARCH_ICON_ID;

    private static NativeImageBackedTexture circleTexture;
    private static NativeImageBackedTexture roundBoxTexture;
    private static NativeImageBackedTexture roundStrokeTexture;
    private static NativeImageBackedTexture shadowBoxTexture;
    private static NativeImageBackedTexture searchIconTexture;

    private static boolean initialized = false;

    private SmoothGfx() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;

        try {
            // 1. Calibrated Anti-Aliased Circle Mask (32x32)
            NativeImage circleImg = new NativeImage(ICON_TEX_SIZE, ICON_TEX_SIZE, false);
            float cMid = ICON_TEX_SIZE / 2.0f; // 16.0
            float maxR = 13.5f;
            for (int y = 0; y < ICON_TEX_SIZE; y++) {
                for (int x = 0; x < ICON_TEX_SIZE; x++) {
                    float dx = x + 0.5f - cMid;
                    float dy = y + 0.5f - cMid;
                    float dist = (float) Math.hypot(dx, dy) - maxR;
                    float cov = Math.max(0.0f, Math.min(1.0f, 0.5f - dist / 2.0f));
                    int a = Math.round(cov * 255.0f);
                    circleImg.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            circleTexture = new NativeImageBackedTexture(() -> "collective_smooth_circle", circleImg);
            circleTexture.upload();
            ((AbstractTextureAccessor) (Object) circleTexture).collective$setSampler(
                    RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR));
            CIRCLE_ID = Identifier.of("collective", "textures/gui/smooth_circle.png");
            mc.getTextureManager().registerTexture(CIRCLE_ID, circleTexture);

            // 2. Calibrated Anti-Aliased Rounded AABB Mask (64x64)
            NativeImage boxImg = new NativeImage(BOX_TEX_SIZE, BOX_TEX_SIZE, false);
            float bMid = BOX_TEX_SIZE / 2.0f; // 32.0
            float half = 30.0f;
            float cr = 14.0f;
            for (int y = 0; y < BOX_TEX_SIZE; y++) {
                for (int x = 0; x < BOX_TEX_SIZE; x++) {
                    float px = Math.abs(x + 0.5f - bMid);
                    float py = Math.abs(y + 0.5f - bMid);
                    float qx = px - (half - cr);
                    float qy = py - (half - cr);
                    float dist = Math.min(Math.max(qx, qy), 0.0f) + (float) Math.hypot(Math.max(qx, 0.0f), Math.max(qy, 0.0f)) - cr;
                    float cov = Math.max(0.0f, Math.min(1.0f, 0.5f - dist / 2.2f));
                    int a = Math.round(cov * 255.0f);
                    boxImg.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            roundBoxTexture = new NativeImageBackedTexture(() -> "collective_smooth_round_box", boxImg);
            roundBoxTexture.upload();
            ((AbstractTextureAccessor) (Object) roundBoxTexture).collective$setSampler(
                    RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR));
            ROUND_BOX_ID = Identifier.of("collective", "textures/gui/smooth_round_box.png");
            mc.getTextureManager().registerTexture(ROUND_BOX_ID, roundBoxTexture);

            // 3. Calibrated Anti-Aliased Rounded Stroke Mask (64x64)
            NativeImage strokeImg = new NativeImage(BOX_TEX_SIZE, BOX_TEX_SIZE, false);
            float strokeW = 2.0f;
            for (int y = 0; y < BOX_TEX_SIZE; y++) {
                for (int x = 0; x < BOX_TEX_SIZE; x++) {
                    float px = Math.abs(x + 0.5f - bMid);
                    float py = Math.abs(y + 0.5f - bMid);
                    float qx = px - (half - cr);
                    float qy = py - (half - cr);
                    float dist = Math.min(Math.max(qx, qy), 0.0f) + (float) Math.hypot(Math.max(qx, 0.0f), Math.max(qy, 0.0f)) - cr;
                    float edgeDist = Math.abs(dist + strokeW * 0.5f);
                    float cov = Math.max(0.0f, Math.min(1.0f, 0.5f - (edgeDist - strokeW * 0.5f) / 1.8f));
                    int a = Math.round(cov * 255.0f);
                    strokeImg.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            roundStrokeTexture = new NativeImageBackedTexture(() -> "collective_smooth_round_stroke", strokeImg);
            roundStrokeTexture.upload();
            ((AbstractTextureAccessor) (Object) roundStrokeTexture).collective$setSampler(
                    RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR));
            ROUND_STROKE_ID = Identifier.of("collective", "textures/gui/smooth_round_stroke.png");
            mc.getTextureManager().registerTexture(ROUND_STROKE_ID, roundStrokeTexture);

            // 4. Seamless Soft Shadow AABB (64x64, Guaranteed 0 Alpha at Boundary)
            NativeImage shadowImg = new NativeImage(BOX_TEX_SIZE, BOX_TEX_SIZE, false);
            float sHalf = 20.0f;
            float sCr = 8.0f;
            float blurDist = 11.0f;
            for (int y = 0; y < BOX_TEX_SIZE; y++) {
                for (int x = 0; x < BOX_TEX_SIZE; x++) {
                    float px = Math.abs(x + 0.5f - bMid);
                    float py = Math.abs(y + 0.5f - bMid);
                    float qx = px - (sHalf - sCr);
                    float qy = py - (sHalf - sCr);
                    float dist = Math.min(Math.max(qx, qy), 0.0f) + (float) Math.hypot(Math.max(qx, 0.0f), Math.max(qy, 0.0f)) - sCr;
                    float factor;
                    if (dist <= 0.0f) {
                        factor = 1.0f;
                    } else if (dist >= blurDist) {
                        factor = 0.0f;
                    } else {
                        float t = dist / blurDist;
                        factor = (1.0f - t) * (1.0f - t) * (1.0f - t);
                    }
                    int a = Math.round(Math.max(0.0f, Math.min(1.0f, factor)) * 255.0f);
                    shadowImg.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            shadowBoxTexture = new NativeImageBackedTexture(() -> "collective_smooth_shadow_box", shadowImg);
            shadowBoxTexture.upload();
            ((AbstractTextureAccessor) (Object) shadowBoxTexture).collective$setSampler(
                    RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR));
            SHADOW_BOX_ID = Identifier.of("collective", "textures/gui/smooth_shadow_box.png");
            mc.getTextureManager().registerTexture(SHADOW_BOX_ID, shadowBoxTexture);

            // 5. Anti-Aliased Vector Search Icon (32x32)
            NativeImage searchImg = new NativeImage(ICON_TEX_SIZE, ICON_TEX_SIZE, false);
            float sCx = 13.0f;
            float sCy = 13.0f;
            float sRMid = 7.5f;
            float sThick = 1.4f; // ring stroke width ~2.8
            float p1X = 18.3f;
            float p1Y = 18.3f;
            float p2X = 26.5f;
            float p2Y = 26.5f;
            float vX = p2X - p1X;
            float vY = p2Y - p1Y;
            float vLenSq = vX * vX + vY * vY;

            for (int y = 0; y < ICON_TEX_SIZE; y++) {
                for (int x = 0; x < ICON_TEX_SIZE; x++) {
                    float curX = x + 0.5f;
                    float curY = y + 0.5f;

                    // Ring lens SDF
                    float distLens = Math.abs((float) Math.hypot(curX - sCx, curY - sCy) - sRMid) - sThick;

                    // Handle line segment capsule SDF
                    float dot = (curX - p1X) * vX + (curY - p1Y) * vY;
                    float t = Math.max(0.0f, Math.min(1.0f, dot / vLenSq));
                    float projX = p1X + t * vX;
                    float projY = p1Y + t * vY;
                    float distHandle = (float) Math.hypot(curX - projX, curY - projY) - sThick;

                    float dist = Math.min(distLens, distHandle);
                    float cov = Math.max(0.0f, Math.min(1.0f, 0.5f - dist / 1.5f));
                    int a = Math.round(cov * 255.0f);
                    searchImg.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }
            searchIconTexture = new NativeImageBackedTexture(() -> "collective_smooth_search_icon", searchImg);
            searchIconTexture.upload();
            ((AbstractTextureAccessor) (Object) searchIconTexture).collective$setSampler(
                    RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR));
            SEARCH_ICON_ID = Identifier.of("collective", "textures/gui/smooth_search_icon.png");
            mc.getTextureManager().registerTexture(SEARCH_ICON_ID, searchIconTexture);

            initialized = true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Draw an anti-aliased subpixel circle.
     */
    public static void circle(GuiGraphics ctx, float cx, float cy, float radius, int color) {
        if (radius <= 0f) return;
        ensureInitialized();
        if (CIRCLE_ID == null) {
            ctx.fill((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius), color);
            return;
        }

        int x = Math.round(cx - radius);
        int y = Math.round(cy - radius);
        int d = Math.max(1, Math.round(radius * 2f));

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                CIRCLE_ID,
                x, y,
                0f, 0f,
                d, d,
                ICON_TEX_SIZE, ICON_TEX_SIZE,
                ICON_TEX_SIZE, ICON_TEX_SIZE,
                color
        );
    }

    /**
     * Draw a smooth rounded rectangle with bilinear anti-aliased 9-slice sampling.
     */
    public static void fillRounded(GuiGraphics ctx, float x, float y, float w, float h, float radius, int color) {
        draw9Slice(ctx, ROUND_BOX_ID, x, y, w, h, radius, 16f, 32f, BOX_TEX_SIZE, color);
    }

    /**
     * Draw a smooth stroked outline with bilinear anti-aliased 9-slice sampling.
     */
    public static void strokeRounded(GuiGraphics ctx, float x, float y, float w, float h, float radius, float thickness, int color) {
        draw9Slice(ctx, ROUND_STROKE_ID, x, y, w, h, radius, 16f, 32f, BOX_TEX_SIZE, color);
    }

    /**
     * Draw a rounded rectangle with both fill and stroke border in seamless order.
     */
    public static void fillAndStroke(GuiGraphics ctx, float x, float y, float w, float h, float radius, int fillColor, int borderColor, float strokeThickness) {
        fillRounded(ctx, x, y, w, h, radius, fillColor);
        strokeRounded(ctx, x, y, w, h, radius, strokeThickness, borderColor);
    }

    /**
     * Multi-layer smooth Gaussian soft outer glow / drop shadow without edge cutoffs or banding.
     */
    public static void softGlow(GuiGraphics ctx, float x, float y, float w, float h, float radius, int color, float spread, float yOff) {
        if (w <= 0f || h <= 0f) return;
        ensureInitialized();
        if (SHADOW_BOX_ID == null) return;

        float sp = Math.max(1f, spread);
        float corner = radius + sp;
        draw9Slice(ctx, SHADOW_BOX_ID, x - sp, y + yOff - sp, w + sp * 2f, h + sp * 2f, corner, 20f, 24f, BOX_TEX_SIZE, color);
    }

    /**
     * Draw vector search / magnifying glass icon with high-DPI bilinear anti-aliasing.
     */
    public static void drawSearchIcon(GuiGraphics ctx, float x, float y, float size, int color) {
        if (size <= 0f) return;
        ensureInitialized();
        if (SEARCH_ICON_ID == null) return;

        int ix = Math.round(x);
        int iy = Math.round(y);
        int is = Math.max(1, Math.round(size));
        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SEARCH_ICON_ID,
                ix, iy,
                0f, 0f,
                is, is,
                ICON_TEX_SIZE, ICON_TEX_SIZE,
                ICON_TEX_SIZE, ICON_TEX_SIZE,
                color
        );
    }

    private static void draw9Slice(GuiGraphics ctx, Identifier texId, float x, float y, float w, float h, float r,
                                   float cTex, float midTex, float texSize, int color) {
        if (w <= 0f || h <= 0f) return;
        ensureInitialized();
        if (texId == null) {
            ctx.fill((int) x, (int) y, (int) (x + w), (int) (y + h), color);
            return;
        }

        float corner = Math.max(1f, Math.min(r, Math.min(w, h) * 0.5f));

        int x0 = Math.round(x);
        int x1 = Math.round(x + corner);
        int x2 = Math.round(x + w - corner);
        int x3 = Math.round(x + w);

        int y0 = Math.round(y);
        int y1 = Math.round(y + corner);
        int y2 = Math.round(y + h - corner);
        int y3 = Math.round(y + h);

        int wLeft = Math.max(0, x1 - x0);
        int wMid = Math.max(0, x2 - x1);
        int wRight = Math.max(0, x3 - x2);

        int hTop = Math.max(0, y1 - y0);
        int hMid = Math.max(0, y2 - y1);
        int hBot = Math.max(0, y3 - y2);

        // Top Row
        if (hTop > 0) {
            if (wLeft > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x0, y0, 0f, 0f, wLeft, hTop, (int) cTex, (int) cTex, (int) texSize, (int) texSize, color);
            }
            if (wMid > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x1, y0, cTex, 0f, wMid, hTop, (int) midTex, (int) cTex, (int) texSize, (int) texSize, color);
            }
            if (wRight > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x2, y0, texSize - cTex, 0f, wRight, hTop, (int) cTex, (int) cTex, (int) texSize, (int) texSize, color);
            }
        }

        // Mid Row
        if (hMid > 0) {
            if (wLeft > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x0, y1, 0f, cTex, wLeft, hMid, (int) cTex, (int) midTex, (int) texSize, (int) texSize, color);
            }
            if (wMid > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x1, y1, cTex, cTex, wMid, hMid, (int) midTex, (int) midTex, (int) texSize, (int) texSize, color);
            }
            if (wRight > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x2, y1, texSize - cTex, cTex, wRight, hMid, (int) cTex, (int) midTex, (int) texSize, (int) texSize, color);
            }
        }

        // Bot Row
        if (hBot > 0) {
            if (wLeft > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x0, y2, 0f, texSize - cTex, wLeft, hBot, (int) cTex, (int) cTex, (int) texSize, (int) texSize, color);
            }
            if (wMid > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x1, y2, cTex, texSize - cTex, wMid, hBot, (int) midTex, (int) cTex, (int) texSize, (int) texSize, color);
            }
            if (wRight > 0) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texId, x2, y2, texSize - cTex, texSize - cTex, wRight, hBot, (int) cTex, (int) cTex, (int) texSize, (int) texSize, color);
            }
        }
    }
}