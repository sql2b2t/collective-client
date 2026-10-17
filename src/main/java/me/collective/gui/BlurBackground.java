package me.collective.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Блюр фона для экранов: читает текущий кадр из bound framebuffer
 * через glReadPixels, даунскейлит, блюрит и заливает в динамическую
 * текстуру. Захват — один раз при открытии экрана.
 */
public final class BlurBackground {
    private static final Identifier BLUR_ID = Identifier.of("collective", "dynamic/blur_bg");
    private static final int DOWNSCALE = 6;
    private static final int BLUR_RADIUS = 3;
    private static final int BLUR_PASSES = 2;
    private static final int MAX_UPLOAD_W = 1920;

    public record Captured(int w, int h) {
    }

    private BlurBackground() {
    }

    /** Захват фона. Null при любой ошибке (тогда экран рисует обычный фон). */
    public static Captured capture(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) return null;
        int fw = mc.getWindow().getFramebufferWidth();
        int fh = mc.getWindow().getFramebufferHeight();
        if (fw <= 0 || fh <= 0) return null;
        ByteBuffer pixels = null;
        try {
            pixels = MemoryUtil.memAlloc(fw * fh * 4);
            GL11.glReadPixels(0, 0, fw, fh, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) return null;
            GL30.glGetError();

            int sw = Math.max(2, fw / DOWNSCALE);
            int sh = Math.max(2, fh / DOWNSCALE);
            NativeImage small = new NativeImage(NativeImage.Format.RGBA, sw, sh, false);
            try {
                for (int y = 0; y < sh; y++) {
                    for (int x = 0; x < sw; x++) {
                        long r = 0, g = 0, b = 0;
                        int n = 0;
                        for (int oy = 0; oy < DOWNSCALE; oy++) {
                            for (int ox = 0; ox < DOWNSCALE; ox++) {
                                int sx = Math.min(fw - 1, x * DOWNSCALE + ox);
                                int sy = Math.min(fh - 1, y * DOWNSCALE + oy);
                                int i = ((fh - 1 - sy) * fw + sx) * 4;
                                r += pixels.get(i) & 0xFF;
                                g += pixels.get(i + 1) & 0xFF;
                                b += pixels.get(i + 2) & 0xFF;
                                n++;
                            }
                        }
                        small.setColorArgb(x, y, 0xFF000000
                            | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n));
                    }
                }
                boxBlur(small, sw, sh);

                int tw = Math.min(fw, MAX_UPLOAD_W);
                int th = Math.max(1, (int) ((long) fh * tw / fw));
                NativeImage big = new NativeImage(NativeImage.Format.RGBA, tw, th, false);
                try {
                    upscaleBilinear(small, sw, sh, big, tw, th);
                } finally {
                    small.close();
                }
                NativeImageBackedTexture tex =
                    new NativeImageBackedTexture(() -> "collective-blur", big);
                try {
                    mc.getTextureManager().destroyTexture(BLUR_ID);
                } catch (Throwable ignored) {
                }
                mc.getTextureManager().registerTexture(BLUR_ID, tex);
                return new Captured(tw, th);
            } catch (Throwable t) {
                try {
                    small.close();
                } catch (Throwable ignored) {
                }
                return null;
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (pixels != null) MemoryUtil.memFree(pixels);
        }
    }

    public static Identifier id() {
        return BLUR_ID;
    }

    public static void release(MinecraftClient mc) {
        if (mc == null) return;
        try {
            mc.getTextureManager().destroyTexture(BLUR_ID);
        } catch (Throwable ignored) {
        }
    }

    private static void boxBlur(NativeImage img, int w, int h) {
        int[] tmp = new int[w * h];
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            // Горизонталь
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    long a = 0, r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                        int sx = Math.min(w - 1, Math.max(0, x + k));
                        int c = img.getColorArgb(sx, y);
                        a += (c >>> 24);
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        n++;
                    }
                    tmp[y * w + x] = ((int) (a / n) << 24) | ((int) (r / n) << 16)
                        | ((int) (g / n) << 8) | (int) (b / n);
                }
            }
            // Вертикаль
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    long a = 0, r = 0, g = 0, b = 0;
                    int n = 0;
                    for (int k = -BLUR_RADIUS; k <= BLUR_RADIUS; k++) {
                        int sy = Math.min(h - 1, Math.max(0, y + k));
                        int c = tmp[sy * w + x];
                        a += (c >>> 24);
                        r += (c >> 16) & 0xFF;
                        g += (c >> 8) & 0xFF;
                        b += c & 0xFF;
                        n++;
                    }
                    img.setColorArgb(x, y, ((int) (a / n) << 24) | ((int) (r / n) << 16)
                        | ((int) (g / n) << 8) | (int) (b / n));
                }
            }
        }
    }

    private static void upscaleBilinear(NativeImage src, int sw, int sh,
                                        NativeImage dst, int tw, int th) {
        for (int y = 0; y < th; y++) {
            float sy = sh <= 1 ? 0 : (float) y * (sh - 1) / (th - 1);
            int y0 = (int) sy;
            int y1 = Math.min(sh - 1, y0 + 1);
            float fy = sy - y0;
            for (int x = 0; x < tw; x++) {
                float sx = sw <= 1 ? 0 : (float) x * (sw - 1) / (tw - 1);
                int x0 = (int) sx;
                int x1 = Math.min(sw - 1, x0 + 1);
                float fx = sx - x0;
                int c00 = src.getColorArgb(x0, y0);
                int c10 = src.getColorArgb(x1, y0);
                int c01 = src.getColorArgb(x0, y1);
                int c11 = src.getColorArgb(x1, y1);
                dst.setColorArgb(x, y, bilerp(c00, c10, c01, c11, fx, fy));
            }
        }
    }

    private static int bilerp(int c00, int c10, int c01, int c11, float fx, float fy) {
        float a = lerp(lerp((c00 >>> 24), (c10 >>> 24), fx), lerp((c01 >>> 24), (c11 >>> 24), fx), fy);
        float r = lerp(lerp((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, fx),
            lerp((c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, fx), fy);
        float g = lerp(lerp((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, fx),
            lerp((c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, fx), fy);
        float b = lerp(lerp(c00 & 0xFF, c10 & 0xFF, fx), lerp(c01 & 0xFF, c11 & 0xFF, fx), fy);
        return (Math.round(a) << 24) | (Math.round(r) << 16) | (Math.round(g) << 8) | Math.round(b);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
