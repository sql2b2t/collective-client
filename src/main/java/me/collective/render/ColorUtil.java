package me.collective.render;

import java.awt.Color;

public class ColorUtil {
    public static Color rainbow(int delay, float saturation, float brightness) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 20.0);
        rainbowState %= 360;
        return Color.getHSBColor((float) (rainbowState / 360.0), saturation, brightness);
    }

    public static Color gradient(Color start, Color end, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        int r = (int) (start.getRed() + factor * (end.getRed() - start.getRed()));
        int g = (int) (start.getGreen() + factor * (end.getGreen() - start.getGreen()));
        int b = (int) (start.getBlue() + factor * (end.getBlue() - start.getBlue()));
        int a = (int) (start.getAlpha() + factor * (end.getAlpha() - start.getAlpha()));
        return new Color(r, g, b, a);
    }

    public static Color applyAlpha(Color color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static int toRGBA(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return toRGBA(r, g, b, 255);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24);
    }

    public static int multiplyAlpha(int color, float factor) {
        int alpha = (color >> 24) & 0xFF;
        int newAlpha = Math.max(0, Math.min(255, Math.round(alpha * factor)));
        return (color & 0x00FFFFFF) | (newAlpha << 24);
    }

    public static int interpolate(int color1, int color2, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + factor * (a2 - a1));
        int r = (int) (r1 + factor * (r2 - r1));
        int g = (int) (g1 + factor * (g2 - g1));
        int b = (int) (b1 + factor * (b2 - b1));

        return toRGBA(r, g, b, a);
    }

    /**
     * Мягкое затухание подсветки к границе дальности: последние 30% радиуса
     * плавно гасят непрозрачность вместо резкого исчезрования на кромке.
     * Минимум 12% непрозрачности, чтобы дальний объект оставался заметным.
     */
    public static java.awt.Color fadeByDistance(double dist, double range, java.awt.Color base) {
        if (range <= 1.0) return base;
        double start = range * 0.7;
        if (dist <= start) return base;
        double t = 1.0 - (dist - start) / Math.max(1.0, range - start);
        t = Math.max(0.12, Math.min(1.0, t));
        return new java.awt.Color(
            base.getRed(), base.getGreen(), base.getBlue(),
            (int) Math.round(base.getAlpha() * t));

    }

    /** Затухание для контура: контур держится заметнее заливки. */
    public static java.awt.Color fadeLineByDistance(double dist, double range, java.awt.Color base, int lineAlpha) {
        if (range <= 1.0) return new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(), lineAlpha);
        double start = range * 0.7;
        if (dist <= start) return new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(), lineAlpha);
        double t = 1.0 - (dist - start) / Math.max(1.0, range - start);
        t = Math.max(0.25, Math.min(1.0, t));
        return new java.awt.Color(
            base.getRed(), base.getGreen(), base.getBlue(),
            (int) Math.round(lineAlpha * t));
    }
}
