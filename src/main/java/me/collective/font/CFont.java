package me.collective.font;

import me.collective.mixin.AbstractTextureAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CFont implements AutoCloseable {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    protected final Font font;
    protected final boolean antiAlias;
    protected final boolean fractionalMetrics;
    protected final Map<Character, CharData> charMap = new HashMap<>();
    protected int imgSize = 1024;
    protected int fontHeight = -1;
    protected Identifier textureId;
    protected NativeImageBackedTexture nativeTexture;
    protected NativeImage nativeImage;
    private boolean closed;

    public static class CharData {
        public int x;
        public int y;
        public int width;
        public int height;

        public CharData(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public CFont(Font font, boolean antiAlias, boolean fractionalMetrics) {
        this.font = font;
        this.antiAlias = antiAlias;
        this.fractionalMetrics = fractionalMetrics;
        generateGlyphs();
    }

    private void generateGlyphs() {
        // ASCII + Latin-1 + Cyrillic
        char[] chars = new char[95 + 96 + 66];
        int idx = 0;
        // ASCII Printable 32 - 126
        for (int i = 32; i < 127; i++) {
            chars[idx++] = (char) i;
        }
        // Latin-1 Supplement 160 - 255
        for (int i = 160; i < 256; i++) {
            chars[idx++] = (char) i;
        }
        // Cyrillic uppercase & lowercase 1040 - 1103 (А-Я, а-я)
        for (int i = 1040; i <= 1103; i++) {
            chars[idx++] = (char) i;
        }
        chars[idx++] = 'Ё';
        chars[idx++] = 'ё';

        BufferedImage tempImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gTemp = tempImg.createGraphics();
        setupHints(gTemp);
        FontMetrics metrics = gTemp.getFontMetrics(font);

        int maxCharWidth = 0;
        for (char c : chars) {
            Rectangle2D bounds = metrics.getStringBounds(String.valueOf(c), gTemp);
            maxCharWidth = Math.max(maxCharWidth, (int) Math.ceil(bounds.getWidth()));
        }
        gTemp.dispose();

        this.imgSize = 1024;
        BufferedImage image = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        setupHints(g);
        g.setFont(font);
        g.setColor(new Color(255, 255, 255, 0));
        g.fillRect(0, 0, imgSize, imgSize);
        g.setColor(Color.WHITE);

        int currentX = 2;
        int currentY = 2;
        int rowHeight = 0;

        for (char c : chars) {
            Rectangle2D bounds = metrics.getStringBounds(String.valueOf(c), g);
            int width = (int) Math.ceil(bounds.getWidth()) + 2;
            int height = (int) Math.ceil(bounds.getHeight());

            if (currentX + width >= imgSize - 2) {
                currentX = 2;
                currentY += rowHeight + 2;
                rowHeight = 0;
            }

            charMap.put(c, new CharData(currentX, currentY, width, height));
            g.drawString(String.valueOf(c), currentX, currentY + metrics.getAscent());

            currentX += width + 2;
            rowHeight = Math.max(rowHeight, height);
            fontHeight = Math.max(fontHeight, height);
        }
        g.dispose();

        try {
            NativeImage nativeImg = new NativeImage(imgSize, imgSize, false);
            int[] pixels = ((java.awt.image.DataBufferInt) image.getRaster().getDataBuffer()).getData();
            for (int y = 0; y < imgSize; y++) {
                int rowOffset = y * imgSize;
                for (int x = 0; x < imgSize; x++) {
                    nativeImg.setColorArgb(x, y, pixels[rowOffset + x]);
                }
            }
            this.nativeImage = nativeImg;
            tryUploadTexture();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized boolean ensureTexture() {
        if (textureId != null) return true;
        return tryUploadTexture();
    }

    private synchronized boolean tryUploadTexture() {
        if (closed) return false;
        if (textureId != null) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null || nativeImage == null) return false;

        try {
            nativeTexture = new NativeImageBackedTexture(() -> "collective_custom_font", nativeImage);
            nativeTexture.upload();
            // Атлас глифов суперсэмплирован (2x) и выводится уменьшенным, поэтому
            // NEAREST при дробном масштабе выбрасывает тексели и текст выглядит
            // пиксельным. Линейная фильтрация даёт корректное сглаживание при
            // уменьшении/увеличении.
            ((AbstractTextureAccessor) (Object) nativeTexture).collective$setSampler(
                    com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getRepeated(
                            com.mojang.blaze3d.textures.FilterMode.LINEAR));
            textureId = Identifier.of("collective", "textures/font/cfont_" + ID_COUNTER.incrementAndGet() + ".png");
            mc.getTextureManager().registerTexture(textureId, nativeTexture);
            return true;
        } catch (Exception e) {
            // A failed upload must not retain a half-created GPU texture.
            if (nativeTexture != null) {
                nativeTexture.close();
                nativeTexture = null;
                nativeImage = null;
            }
            textureId = null;
            return false;
        }
    }

    /** Releases only this font's atlas; glyph layout and rendering are unchanged. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        Minecraft client = Minecraft.getInstance();
        if (textureId != null && client != null && client.getTextureManager() != null) {
            client.getTextureManager().destroyTexture(textureId);
        } else if (nativeTexture != null) {
            nativeTexture.close();
        } else if (nativeImage != null) {
            nativeImage.close();
        }
        textureId = null;
        nativeTexture = null;
        nativeImage = null;
    }

    private void setupHints(Graphics2D g) {
        if (antiAlias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        }
        if (fractionalMetrics) {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }
    }

    public void drawString(GuiGraphics context, String text, float x, float y, int color) {
        drawString(context, text, x, y, color, 1.0f);
    }

    /** Отрисовка с произвольным масштабом (k — множитель к базовому размеру). */
    public void drawString(GuiGraphics context, String text, float x, float y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        if (scale == 1.0f) {
            drawStringDefault(context, text, x, y, color, false);
            return;
        }
        if (!ensureTexture()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);
            context.drawText(Minecraft.getInstance().textRenderer, text, (int) Math.round(x / scale), (int) Math.round(y / scale), color, false);
            context.getMatrices().popMatrix();
            return;
        }
        drawStringInternal(context, text, x, y, color, false, scale);
    }

    public void drawStringWithShadow(GuiGraphics context, String text, float x, float y, int color) {
        drawStringWithShadow(context, text, x, y, color, 1.0f);
    }

    public void drawStringWithShadow(GuiGraphics context, String text, float x, float y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        if (scale == 1.0f) {
            drawStringDefaultWithShadow(context, text, x, y, color);
            return;
        }
        if (!ensureTexture()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().scale(scale, scale);
            context.drawTextWithShadow(Minecraft.getInstance().textRenderer, text, (int) Math.round(x / scale), (int) Math.round(y / scale), color);
            context.getMatrices().popMatrix();
            return;
        }
        drawStringInternal(context, text, x + 0.5f * scale, y + 0.5f * scale, color, true, scale);
        drawStringInternal(context, text, x, y, color, false, scale);
    }

    private void drawStringDefault(GuiGraphics context, String text, float x, float y, int color, boolean shadow) {
        if (!ensureTexture()) {
            Minecraft mcClient = Minecraft.getInstance();
            if (shadow) {
                context.drawTextWithShadow(mcClient.textRenderer, text, (int) Math.round(x), (int) Math.round(y), color);
            } else {
                context.drawText(mcClient.textRenderer, text, (int) Math.round(x), (int) Math.round(y), color, false);
            }
            return;
        }
        if (shadow) {
            drawStringInternal(context, text, x + 0.5f, y + 0.5f, color, true, 1.0f);
            drawStringInternal(context, text, x, y, color, false, 1.0f);
        } else {
            drawStringInternal(context, text, x, y, color, false, 1.0f);
        }
    }

    private void drawStringDefaultWithShadow(GuiGraphics context, String text, float x, float y, int color) {
        if (!ensureTexture()) {
            context.drawTextWithShadow(Minecraft.getInstance().textRenderer, text, (int) Math.round(x), (int) Math.round(y), color);
            return;
        }
        drawStringInternal(context, text, x + 0.5f, y + 0.5f, color, true, 1.0f);
        drawStringInternal(context, text, x, y, color, false, 1.0f);
    }

    private void drawStringInternal(GuiGraphics context, String text, float x, float y, int color, boolean isShadow) {
        drawStringInternal(context, text, x, y, color, isShadow, 1.0f);
    }

    private void drawStringInternal(GuiGraphics context, String text, float x, float y, int color, boolean isShadow, float scale) {
        if (text == null || text.isEmpty()) return;
        if (textureId == null) return;

        int activeColor = applyShadow(color, isShadow);
        float curX = x;

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(0.5f * scale, 0.5f * scale);

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Handle Minecraft color code: §
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                int codeIndex = "0123456789abcdefklmnor".indexOf(code);
                if (codeIndex >= 0 && codeIndex <= 15) {
                    int codeColor = getColorForCode(codeIndex, (color >> 24) & 0xFF);
                    activeColor = applyShadow(codeColor, isShadow);
                } else if (code == 'r') {
                    activeColor = applyShadow(color, isShadow);
                }
                i++;
                continue;
            }

            CharData data = charMap.get(c);
            if (data == null) {
                data = charMap.get('?');
            }

            if (data != null) {
                context.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        textureId,
                        (int) Math.round(curX * 2.0f / scale),
                        (int) Math.round(y * 2.0f / scale),
                        (float) data.x,
                        (float) data.y,
                        data.width,
                        data.height,
                        imgSize,
                        imgSize,
                        activeColor
                );
                curX += (data.width - 2) * 0.5f * scale;
            } else {
                curX += 4.0f * scale;
            }
        }

        context.getMatrices().popMatrix();
    }

    private int applyShadow(int argb, boolean isShadow) {
        if (!isShadow) return argb;
        int a = (argb >> 24) & 0xFF;
        if (a == 0) a = 255;
        // Divide RGB by 4 (0.25x brightness) matching client shadow:
        int r = ((argb >> 16) & 0xFF) >> 2;
        int g = ((argb >> 8) & 0xFF) >> 2;
        int b = (argb & 0xFF) >> 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public float getStringWidth(String text) {
        return getStringWidth(text, 1.0f);
    }

    /** Ширина текста при заданном масштабе. */
    public float getStringWidth(String text, float scale) {
        if (text == null || text.isEmpty()) return 0;
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            CharData data = charMap.get(c);
            if (data != null) {
                width += (data.width - 2) * 0.5f * scale;
            } else {
                width += 4.0f * scale;
            }
        }
        return width;
    }

    public int getFontHeight() {
        return getFontHeight(1.0f);
    }

    public int getFontHeight(float scale) {
        return (int) Math.ceil((fontHeight > 0 ? fontHeight : 18) * 0.5f * scale);
    }

    private static int getColorForCode(int code, int alpha) {
        int[] palette = {
                0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
                0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
                0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
                0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
        };
        int rgb = palette[code % palette.length];
        return (alpha << 24) | rgb;
    }
}