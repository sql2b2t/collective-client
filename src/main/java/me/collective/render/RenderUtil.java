package me.collective.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class RenderUtil {

    public static void drawRect(DrawContext context, float x1, float y1, float x2, float y2, int color) {
        int left = (int) Math.min(x1, x2);
        int right = (int) Math.max(x1, x2);
        int top = (int) Math.min(y1, y2);
        int bottom = (int) Math.max(y1, y2);
        context.fill(left, top, right, bottom, color);
    }

    public static void drawRect(DrawContext context, float x1, float y1, float x2, float y2, Color color) {
        drawRect(context, x1, y1, x2, y2, color.getRGB());
    }

    public static void drawGradient(DrawContext context, float x1, float y1, float x2, float y2, int colorStart, int colorEnd) {
        int left = (int) Math.min(x1, x2);
        int right = (int) Math.max(x1, x2);
        int top = (int) Math.min(y1, y2);
        int bottom = (int) Math.max(y1, y2);
        context.fillGradient(left, top, right, bottom, colorStart, colorEnd);
    }

    public static void drawOutline(DrawContext context, float x, float y, float width, float height, float thickness, int color) {
        drawRect(context, x, y, x + width, y + thickness, color);
        drawRect(context, x, y + height - thickness, x + width, y + height, color);
        drawRect(context, x, y + thickness, x + thickness, y + height - thickness, color);
        drawRect(context, x + width - thickness, y + thickness, x + width, y + height - thickness, color);
    }

    public static void drawRoundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        drawRect(context, x + radius, y, x + width - radius, y + height, color);
        drawRect(context, x, y + radius, x + radius, y + height - radius, color);
        drawRect(context, x + width - radius, y + radius, x + width, y + height - radius, color);
    }

    /** Настоящий скруглённый прямоугольник: полосы + 4 диска по углам. */
    public static void drawSmoothRoundedRect(DrawContext context, float x, float y, float width, float height, float radius, int color) {
        if (radius <= 0.5f) {
            drawRect(context, x, y, x + width, y + height, color);
            return;
        }
        radius = Math.min(radius, Math.min(width, height) / 2f);
        drawRect(context, x + radius, y, x + width - radius, y + height, color);
        drawRect(context, x, y + radius, x + radius, y + height - radius, color);
        drawRect(context, x + width - radius, y + radius, x + width, y + height - radius, color);
        drawCircle(context, x + radius, y + radius, radius, color);
        drawCircle(context, x + width - radius, y + radius, radius, color);
        drawCircle(context, x + radius, y + height - radius, radius, color);
        drawCircle(context, x + width - radius, y + height - radius, radius, color);
    }

    /** Тонкая скруглённая рамка: укороченные полосы + четверти дуг по углам. */
    public static void drawRoundedOutline(DrawContext context, float x, float y, float width, float height, float radius, float thickness, int color) {
        radius = Math.min(radius, Math.min(width, height) / 2f);
        float t = thickness;
        drawRect(context, x + radius, y, x + width - radius, y + t, color);
        drawRect(context, x + radius, y + height - t, x + width - radius, y + height, color);
        drawRect(context, x, y + radius, x + t, y + height - radius, color);
        drawRect(context, x + width - t, y + radius, x + width, y + height - radius, color);
        cornerArc(context, x + radius, y + radius, radius, t, -1, -1, color);
        cornerArc(context, x + width - radius, y + radius, radius, t, 1, -1, color);
        cornerArc(context, x + radius, y + height - radius, radius, t, -1, 1, color);
        cornerArc(context, x + width - radius, y + height - radius, radius, t, 1, 1, color);
    }

    /** Четверть кольца: sx/sy = ±1 — в какой квадрант от центра. */
    private static void cornerArc(DrawContext context, float cx, float cy, float r, float t, int sx, int sy, int color) {
        int y0 = (int) Math.floor(cy - r);
        int y1 = (int) Math.ceil(cy + r);
        for (int row = y0; row <= y1; row++) {
            float yc = row + 0.5f;
            float dy = (yc - cy) * sy;
            if (dy < 0 || dy > r) continue;
            float outer = (float) Math.sqrt(Math.max(0, r * r - dy * dy));
            float innerR = Math.max(0, r - t);
            float inner = (float) Math.sqrt(Math.max(0, innerR * innerR - dy * dy));
            float x1 = sx < 0 ? cx - outer : cx + inner;
            float x2 = sx < 0 ? cx - inner : cx + outer;
            if (x2 > x1) drawRect(context, x1, row, x2, row + 1, color);
        }
    }

    public static void drawShadow(DrawContext context, float x, float y, float width, float height, float size, Color shadowColor) {
        if (size <= 0 || shadowColor.getAlpha() == 0) return;
        drawRect(context, x + size, y + height, x + width + size, y + height + size, shadowColor.getRGB());
        drawRect(context, x + width, y + size, x + width + size, y + height, shadowColor.getRGB());
    }

    public static void drawCircle(DrawContext context, float centerX, float centerY, float radius, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;

        int segments = Math.max(16, (int) (radius * 4.0f));
        float prevX = centerX + radius;
        float prevY = centerY;
        for (int i = 1; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float currX = centerX + (float) Math.cos(angle) * radius;
            float currY = centerY + (float) Math.sin(angle) * radius;
            context.fill((int) Math.min(prevX, currX), (int) Math.min(prevY, currY), (int) Math.max(prevX, currX) + 1, (int) Math.max(prevY, currY) + 1, color);
            prevX = currX;
            prevY = currY;
        }
    }

    public static void drawGlow(DrawContext context, float x, float y, float width, float height, float radius, int color, float intensity, float spread) {
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

    public static void drawRotatedImage(DrawContext context, net.minecraft.util.Identifier texture, float x, float y, float width, float height, float angle, int color) {
        drawRotatedImage(context, texture, x, y, width, height, angle, color, (int) width, (int) height);
    }

    /** Повёрнутая картинка; texW/texH — реальный размер текстуры (регион — весь). */
    public static void drawRotatedImage(DrawContext context, net.minecraft.util.Identifier texture, float x, float y, float width, float height, float angle, int color, int texW, int texH) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x + width / 2.0f, y + height / 2.0f);
        context.getMatrices().rotate((float) Math.toRadians(angle));
        context.getMatrices().translate(-width / 2.0f, -height / 2.0f);
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0f, 0f, (int) width, (int) height, texW, texH, texW, texH, color);
        context.getMatrices().popMatrix();
    }
}
