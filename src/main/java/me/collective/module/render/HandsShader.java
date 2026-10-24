package me.collective.module.render;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.module.client.Colors;
import me.collective.render.HandShaderBuffer;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ColorSetting;
import me.collective.setting.EnumSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;

public class HandsShader extends Module {
    public static HandsShader INSTANCE;

    /** Флаг активного окна рендеринга рук и предметов от первого лица. */
    private static boolean handPass;

    public enum Mode {
        COLLECTIVE,
        RAINBOW,
        ASTOLFO,
        GRADIENT,
        PULSE,
        NEBULA,
        FIRE,
        CYBERPUNK,
        MATRIX,
        AURORA,
        HOLOGRAPHIC,
        GLITCH,
        ELECTRIC,
        SUNSET,
        AMETHYST,
        STATIC
    }

    public enum Target {
        BOTH,
        HANDS,
        ITEMS
    }

    public enum Style {
        TINT,
        GLOW,
        FLAT
    }

    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Режим", Mode.COLLECTIVE));
    public final EnumSetting<Target> target = add(new EnumSetting<>("Применять к", Target.BOTH));
    public final EnumSetting<Style> style = add(new EnumSetting<>("Стиль", Style.TINT));
    public final BooleanSetting syncTheme = add(new BooleanSetting("Цвет из темы", true));

    public final ColorSetting firstColor = add(new ColorSetting("Основной цвет", new Color(189, 153, 255, 255)));
    public final ColorSetting secondColor = add(new ColorSetting("Второй цвет", new Color(110, 220, 255, 255)));
    public final ColorSetting thirdColor = add(new ColorSetting("Третий цвет", new Color(255, 105, 180, 255)));

    public final NumberSetting speed = add(new NumberSetting("Скорость", 1.0, 0.1, 5.0, 0.1));
    public final NumberSetting scale = add(new NumberSetting("Масштаб волн", 1.0, 0.2, 4.0, 0.1));
    public final NumberSetting opacity = add(new NumberSetting("Непрозрачность", 1.0, 0.1, 1.0, 0.05));

    public HandsShader() {
        super("HandsShader", "Продвинутый анимированный шейдер на руки и предметы от первого лица", Category.RENDER);
        INSTANCE = this;
    }

    public static void beginHandPass() {
        handPass = true;
    }

    public static void endHandPass() {
        handPass = false;
    }

    public static boolean isShading() {
        return handPass && INSTANCE != null && INSTANCE.isToggled();
    }

    public static VertexConsumer decorateArm(VertexConsumer consumer) {
        if (!isShading()) return consumer;
        Target t = INSTANCE.target.getValue();
        if (t == Target.ITEMS) return consumer;
        return new HandShaderBuffer(consumer);
    }

    public static VertexConsumer decorateItem(VertexConsumer consumer) {
        if (!isShading()) return consumer;
        Target t = INSTANCE.target.getValue();
        if (t == Target.HANDS) return consumer;
        return new HandShaderBuffer(consumer);
    }

    public static VertexConsumer decorate(VertexConsumer consumer) {
        return decorateItem(consumer);
    }

    /**
     * Вычисляет ARGB цвет вершины с учётом режима анимации и исходного цвета.
     */
    public static int shade(float x, float y, float z, float u, float v, int originalColor, float nx, float ny, float nz) {
        HandsShader module = INSTANCE;
        if (module == null || !handPass) return originalColor;

        float time = Util.getMeasuringTimeMs() * 0.001f * module.speed.getFloatValue();
        float sc = module.scale.getFloatValue();
        int alpha = Math.round(255.0f * module.opacity.getFloatValue());

        Color activeTheme = Colors.INSTANCE != null ? Colors.INSTANCE.getThemeColor() : new Color(189, 153, 255);
        int themeRgb = activeTheme.getRGB() & 0xFFFFFF;

        int c1 = module.syncTheme.getValue() ? themeRgb : (module.firstColor.getColor().getRGB() & 0xFFFFFF);
        int c2 = module.secondColor.getColor().getRGB() & 0xFFFFFF;
        int c3 = module.thirdColor.getColor().getRGB() & 0xFFFFFF;

        int rgb;

        switch (module.mode.getValue()) {
            case COLLECTIVE -> {
                float wave = 0.5f + 0.5f * MathHelper.sin((u * 4.0f + y * 1.2f + x * 0.6f) * sc - time * 2.2f);
                rgb = threeGradient(themeRgb, 0x6EDCFF, 0x9D65FF, wave);
            }

            case STATIC -> rgb = c1;

            case RAINBOW -> {
                float hue = (time * 0.15f + (x * 0.4f + y * 0.3f + z * 0.2f) * sc + u * 0.5f) % 1.0f;
                if (hue < 0) hue += 1.0f;
                rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0xFFFFFF;
            }

            case ASTOLFO -> {
                float phase = (time * 0.25f + (y * 0.4f + x * 0.2f) * sc) % 1.0f;
                if (phase < 0) phase += 1.0f;
                float hue = 0.54f + 0.38f * MathHelper.abs(MathHelper.sin(phase * 3.14159f));
                rgb = Color.HSBtoRGB(hue, 0.75f, 1.0f) & 0xFFFFFF;
            }

            case GRADIENT -> {
                float wave = 0.5f + 0.5f * MathHelper.sin((u * 4.0f + y * 1.2f + x * 0.5f) * sc - time * 2.2f);
                rgb = threeGradient(c1, c2, c3, wave);
            }

            case PULSE -> {
                float pulse = 0.5f + 0.5f * MathHelper.sin(time * 2.8f);
                rgb = lerpRgb(c1, c2, pulse);
            }

            case NEBULA -> {
                float w1 = MathHelper.sin((x * 1.6f + z * 0.8f) * sc - time * 1.5f);
                float w2 = MathHelper.cos((y * 2.0f + u * 3.0f) * sc + time * 1.1f);
                float phase = 0.5f + 0.25f * (w1 + w2);
                rgb = threeGradient(0x4A0E4E, 0xD80064, 0x00F5D4, phase);
            }

            case FIRE -> {
                float rise = (y * 3.0f + u * 2.0f) * sc - time * 4.0f;
                float flicker = MathHelper.sin(rise) * 0.35f + MathHelper.sin(x * 3.5f * sc + time * 5.0f) * 0.15f;
                float t = MathHelper.clamp(0.5f + flicker, 0.0f, 1.0f);
                rgb = fourGradient(0x7F0000, 0xFF3700, 0xFFB700, 0xFFFFE0, t);
            }

            case CYBERPUNK -> {
                float wave = MathHelper.sin((x * 1.4f + y * 2.2f) * sc - time * 3.2f);
                float sharp = (float) Math.pow(0.5f + 0.5f * wave, 1.8);
                rgb = lerpRgb(0x00F0FF, 0xFF007F, sharp);
            }

            case MATRIX -> {
                float drop = (y * 4.0f * sc - time * 6.0f);
                float pulse = (float) Math.pow(Math.max(0.0f, MathHelper.sin(drop)), 4.0);
                rgb = lerpRgb(0x003810, 0x39FF14, pulse);
            }

            case AURORA -> {
                float a1 = MathHelper.sin((x * 1.1f + z * 0.5f) * sc + time * 1.2f);
                float a2 = MathHelper.sin((y * 1.8f) * sc - time * 1.6f);
                float mix = 0.5f + 0.25f * (a1 + a2);
                rgb = threeGradient(0x00E676, 0x00B0FF, 0x9C27B0, mix);
            }

            case HOLOGRAPHIC -> {
                float scan = MathHelper.sin(y * 20.0f * sc - time * 4.5f) * 0.15f;
                float hue = (u * 1.5f + v * 1.5f + scan + time * 0.12f) % 1.0f;
                if (hue < 0) hue += 1.0f;
                rgb = Color.HSBtoRGB(hue, 0.45f, 1.0f) & 0xFFFFFF;
            }

            case GLITCH -> {
                float stepY = (float) Math.floor((y * 10.0f) * sc) / 10.0f;
                float stepTime = (float) Math.floor(time * 7.0f) / 7.0f;
                float g = MathHelper.sin(stepY * 6.0f + stepTime * 5.0f);
                float hue = g > 0.35f ? 0.92f : (g < -0.35f ? 0.52f : 0.16f);
                rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0xFFFFFF;
            }

            case ELECTRIC -> {
                float bolt = MathHelper.sin((x * 5.0f + z * 4.0f + y * 2.0f) * sc + time * 9.0f);
                float spike = (float) Math.pow(Math.max(0.0f, bolt), 5.0);
                rgb = lerpRgb(0x0040B0, 0xE8F8FF, spike);
            }

            case SUNSET -> {
                float mix = 0.5f + 0.5f * MathHelper.sin((u * 2.0f + y * 1.2f) * sc - time * 1.5f);
                rgb = threeGradient(0xFF8F00, 0xFF3D00, 0x651FFF, mix);
            }

            case AMETHYST -> {
                float facet = MathHelper.sin((x * 2.8f + y * 3.0f + z * 2.5f) * sc - time * 1.8f);
                float mix = 0.5f + 0.5f * facet;
                rgb = threeGradient(0x31004A, 0x8E24AA, 0xE1BEE7, mix);
            }

            default -> rgb = c1;
        }

        // Применяем стиль рендеринга (Тонирование с текстурой / Неоновое свечение / Сплошной Chams)
        int sr = (rgb >> 16) & 0xFF;
        int sg = (rgb >> 8) & 0xFF;
        int sb = rgb & 0xFF;

        int or = (originalColor >> 16) & 0xFF;
        int og = (originalColor >> 8) & 0xFF;
        int ob = originalColor & 0xFF;

        Style st = module.style.getValue();
        int finalR, finalG, finalB;

        if (st == Style.TINT) {
            finalR = (sr * or) / 255;
            finalG = (sg * og) / 255;
            finalB = (sb * ob) / 255;
        } else if (st == Style.GLOW) {
            finalR = Math.min(255, (sr * or) / 190 + (sr / 4));
            finalG = Math.min(255, (sg * og) / 190 + (sg / 4));
            finalB = Math.min(255, (sb * ob) / 190 + (sb / 4));
        } else {
            finalR = sr;
            finalG = sg;
            finalB = sb;
        }

        return ((alpha & 0xFF) << 24) | ((finalR & 0xFF) << 16) | ((finalG & 0xFF) << 8) | (finalB & 0xFF);
    }

    public static int shade(float x, float y, float z, float u, float v, int originalColor) {
        return shade(x, y, z, u, v, originalColor, 0.0f, 1.0f, 0.0f);
    }

    public static int shade(float x, float y, float z, float u, float v) {
        return shade(x, y, z, u, v, 0xFFFFFFFF, 0.0f, 1.0f, 0.0f);
    }

    private static int lerpRgb(int c1, int c2, float t) {
        float f = MathHelper.clamp(t, 0.0f, 1.0f);
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = Math.round(r1 + (r2 - r1) * f);
        int g = Math.round(g1 + (g2 - g1) * f);
        int b = Math.round(b1 + (b2 - b1) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int threeGradient(int c1, int c2, int c3, float t) {
        float f = (t % 1.0f + 1.0f) % 1.0f;
        if (f < 0.3333f) {
            return lerpRgb(c1, c2, f * 3.0f);
        } else if (f < 0.6666f) {
            return lerpRgb(c2, c3, (f - 0.3333f) * 3.0f);
        } else {
            return lerpRgb(c3, c1, (f - 0.6666f) * 3.0f);
        }
    }

    private static int fourGradient(int c1, int c2, int c3, int c4, float t) {
        float f = MathHelper.clamp(t, 0.0f, 1.0f);
        if (f < 0.3333f) {
            return lerpRgb(c1, c2, f * 3.0f);
        } else if (f < 0.6666f) {
            return lerpRgb(c2, c3, (f - 0.3333f) * 3.0f);
        } else {
            return lerpRgb(c3, c4, (f - 0.6666f) * 3.0f);
        }
    }
}
