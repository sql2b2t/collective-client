package me.collective.render;

import com.mojang.blaze3d.systems.RenderSystem;
import me.collective.gui.GuiGfx;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;

public class RenderUtil {

    public static void drawRect(GuiGraphics context, float x1, float y1, float x2, float y2, int color) {
        int left = (int) Math.min(x1, x2);
        int right = (int) Math.max(x1, x2);
        int top = (int) Math.min(y1, y2);
        int bottom = (int) Math.max(y1, y2);
        context.fill(left, top, right, bottom, color);
    }

    public static void drawRect(GuiGraphics context, float x1, float y1, float x2, float y2, Color color) {
        drawRect(context, x1, y1, x2, y2, color.getRGB());
    }

    public static void drawGradient(GuiGraphics context, float x1, float y1, float x2, float y2, int colorStart, int colorEnd) {
        int left = (int) Math.min(x1, x2);
        int right = (int) Math.max(x1, x2);
        int top = (int) Math.min(y1, y2);
        int bottom = (int) Math.max(y1, y2);
        context.fillGradient(left, top, right, bottom, colorStart, colorEnd);
    }

    public static void drawOutline(GuiGraphics context, float x, float y, float width, float height, float thickness, int color) {
        drawRect(context, x, y, x + width, y + thickness, color);
        drawRect(context, x, y + height - thickness, x + width, y + height, color);
        drawRect(context, x, y + thickness, x + thickness, y + height - thickness, color);
        drawRect(context, x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    /** Скруглённый прямоугольник с аналитическим сглаживанием. */
    public static void drawRoundedRect(GuiGraphics context, float x, float y, float width, float height, float radius, int color) {
        GuiGfx.fillRounded(context, x, y, width, height, radius, color);
    }

    /** Настоящий скруглённый прямоугольник с аналитическим сглаживанием. */
    public static void drawSmoothRoundedRect(GuiGraphics context, float x, float y, float width, float height, float radius, int color) {
        GuiGfx.fillRounded(context, x, y, width, height, radius, color);
    }

    /** Скруглённая рамка произвольной толщины с аналитическим сглаживанием. */
    public static void drawRoundedOutline(GuiGraphics context, float x, float y, float width, float height, float radius, float thickness, int color) {
        GuiGfx.strokeRounded(context, x, y, width, height, radius, thickness, color);
    }

    public static void drawShadow(GuiGraphics context, float x, float y, float width, float height, float size, Color shadowColor) {
        if (size <= 0 || shadowColor.getAlpha() == 0) return;
        drawRect(context, x + size, y + height, x + width + size, y + height + size, shadowColor.getRGB());
        drawRect(context, x + width, y + size, x + width + size, y + height, shadowColor.getRGB());
    }

    public static void drawCircle(GuiGraphics context, float centerX, float centerY, float radius, int color) {
        GuiGfx.dot(context, centerX, centerY, radius, color);
    }

    public static void drawGlow(GuiGraphics context, float x, float y, float width, float height, float radius, int color, float intensity, float spread) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;

        int layers = 4;
        for (int i = 1; i <= layers; i++) {
            float factor = (float) i / layers;
            int layerAlpha = (int) (a * 0.25f * (1.0f - factor * 0.7f) * intensity);
            if (layerAlpha <= 0) continue;
            int layerColor = (layerAlpha << 24) | (r << 16) | (g << 8) | b;
            float expand = spread * factor;
            drawRoundedRect(context, x - expand, y - expand, width + expand * 2, height + expand * 2, radius + expand, layerColor);
        }
    }

    public static void drawRotatedImage(GuiGraphics context, net.minecraft.util.Identifier texture, float x, float y, float width, float height, float angle, int color) {
        drawRotatedImage(context, texture, x, y, width, height, angle, color, (int) width, (int) height);
    }

    /** Повёрнутая картинка; texW/texH — реальный размер текстуры (регион — весь). */
    public static void drawRotatedImage(GuiGraphics context, net.minecraft.util.Identifier texture, float x, float y, float width, float height, float angle, int color, int texW, int texH) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x + width / 2.0f, y + height / 2.0f);
        context.getMatrices().rotate((float) Math.toRadians(angle));
        context.getMatrices().translate(-width / 2.0f, -height / 2.0f);
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, (int) width, (int) height, texW, texH, texW, texH, color);
        context.getMatrices().popMatrix();
    }
}