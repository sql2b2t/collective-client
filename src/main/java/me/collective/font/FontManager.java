package me.collective.font;

import me.collective.module.client.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.awt.Font;
import java.io.InputStream;

public class FontManager {
    public static final FontManager INSTANCE = new FontManager();
    private final Minecraft mc = Minecraft.getInstance();

    private CFont currentFont;
    private String currentFontName = "Arial";
    private float currentFontSize = 8.5f;
    private int currentFontStyle = Font.PLAIN;
    private boolean currentAntiAlias = true;

    public FontManager() {
        reloadFont();
    }

    public void reloadFont() {
        CFont replacement;
        try {
            int supersampledSize = Math.max(12, (int) Math.round(currentFontSize * 2.0f));
            Font font = null;

            if (currentFontName.equalsIgnoreCase("Arial") || currentFontName.equalsIgnoreCase("Liberation Sans")) {
                try (InputStream is = FontManager.class.getResourceAsStream("/assets/collective/fonts/arial.ttf")) {
                    if (is != null) {
                        font = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(currentFontStyle, (float) supersampledSize);
                    }
                } catch (Exception ignored) {}
            }

            if (font == null) {
                font = new Font(currentFontName, currentFontStyle, supersampledSize);
            }

            replacement = new CFont(font, currentAntiAlias, true);
        } catch (Exception e) {
            replacement = new CFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18), true, true);
        }
        CFont previous = currentFont;
        currentFont = replacement;
        if (previous != null) previous.close();
    }

    /** Called on the client thread during shutdown. */
    public void close() {
        if (currentFont != null) {
            currentFont.close();
            currentFont = null;
        }
    }

    public void setFont(String name, float size, int style, boolean antiAlias) {
        if (!name.equalsIgnoreCase(currentFontName) || Math.abs(size - currentFontSize) > 0.01f || style != currentFontStyle || antiAlias != currentAntiAlias) {
            this.currentFontName = name;
            this.currentFontSize = size;
            this.currentFontStyle = style;
            this.currentAntiAlias = antiAlias;
            reloadFont();
        }
    }

    public void drawString(GuiGraphicsExtractor context, String text, float x, float y, Color color) {
        drawString(context, text, x, y, color.getRGB());
    }

    public void drawString(GuiGraphicsExtractor context, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            currentFont.drawString(context, text, x, y, color);
        } else {
            context.text(mc.font, text, (int) Math.round(x), (int) Math.round(y), color, false);
        }
    }

    public void drawStringWithShadow(GuiGraphicsExtractor context, String text, float x, float y, Color color) {
        drawStringWithShadow(context, text, x, y, color.getRGB());
    }

    public void drawStringWithShadow(GuiGraphicsExtractor context, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            if (Fonts.INSTANCE.shadow.getValue()) {
                currentFont.drawStringWithShadow(context, text, x, y, color);
            } else {
                currentFont.drawString(context, text, x, y, color);
            }
        } else {
            context.text(mc.font, text, (int) Math.round(x), (int) Math.round(y), color);
        }
    }

    public float getStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            return currentFont.getStringWidth(text);
        }
        return mc.font.width(text);
    }

    public int getFontHeight() {
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            return currentFont.getFontHeight();
        }
        return mc.font.lineHeight;
    }

    // ------------------------------------------------------------- Масштабируемый текст

    /**
     * Масштаб для текста настроек модулей: настройка задаёт высоту шрифта
     * в экранных единицах (пикселях GUI), делим её на базовую высоту шрифта.
     */
    public static float settingsScale() {
        if (me.collective.module.client.UI.INSTANCE == null) return 1f;
        float px = me.collective.module.client.UI.INSTANCE.settingsFontSize.getFloatValue();
        if (px <= 0.5f) return 1f;
        return px / Math.max(1, INSTANCE.getFontHeight());
    }

    public float getStringWidthScaled(String text, float scale) {
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            return currentFont.getStringWidth(text, scale);
        }
        return mc.font.width(text) * scale;
    }

    public int getFontHeightScaled(float scale) {
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            return currentFont.getFontHeight(scale);
        }
        return (int) Math.ceil(mc.font.lineHeight * scale);
    }

    public void drawStringScaled(GuiGraphicsExtractor context, String text, float x, float y, Color color, float scale) {
        drawStringScaled(context, text, x, y, color.getRGB(), scale);
    }

    public void drawStringScaled(GuiGraphicsExtractor context, String text, float x, float y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            currentFont.drawString(context, text, x, y, color, scale);
        } else if (scale == 1.0f) {
            context.text(mc.font, text, (int) Math.round(x), (int) Math.round(y), color, false);
        } else {
            context.pose().pushMatrix();
            context.pose().scale(scale, scale);
            context.text(mc.font, text, (int) Math.round(x / scale), (int) Math.round(y / scale), color, false);
            context.pose().popMatrix();
        }
    }

    public void drawStringWithShadowScaled(GuiGraphicsExtractor context, String text, float x, float y, Color color, float scale) {
        drawStringWithShadowScaled(context, text, x, y, color.getRGB(), scale);
    }

    public void drawStringWithShadowScaled(GuiGraphicsExtractor context, String text, float x, float y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        if (Fonts.INSTANCE != null && Fonts.INSTANCE.isToggled() && currentFont != null) {
            if (Fonts.INSTANCE.shadow.getValue()) {
                currentFont.drawStringWithShadow(context, text, x, y, color, scale);
            } else {
                currentFont.drawString(context, text, x, y, color, scale);
            }
        } else if (scale == 1.0f) {
            context.text(mc.font, text, (int) Math.round(x), (int) Math.round(y), color);
        } else {
            context.pose().pushMatrix();
            context.pose().scale(scale, scale);
            context.text(mc.font, text, (int) Math.round(x / scale), (int) Math.round(y / scale), color);
            context.pose().popMatrix();
        }
    }

    public void drawCustomString(GuiGraphicsExtractor context, String text, float x, float y, Color color) {
        drawCustomString(context, text, x, y, color.getRGB());
    }

    public void drawCustomString(GuiGraphicsExtractor context, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        if (currentFont == null) reloadFont();
        if (currentFont != null) {
            currentFont.drawString(context, text, x, y, color);
        } else {
            context.text(mc.font, text, (int) Math.round(x), (int) Math.round(y), color, false);
        }
    }

    public void drawCustomStringWithShadow(GuiGraphicsExtractor context, String text, float x, float y, Color color) {
        drawCustomStringWithShadow(context, text, x, y, color.getRGB());
    }

    public void drawCustomStringWithShadow(GuiGraphicsExtractor context, String text, float x, float y, int color) {
        if (text == null || text.isEmpty()) return;
        if (currentFont == null) reloadFont();
        if (currentFont != null) {
            currentFont.drawStringWithShadow(context, text, x, y, color);
        } else {
            context.text(mc.font, text, (int) Math.round(x), (int) Math.round(y), color);
        }
    }

    public float getCustomStringWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (currentFont == null) reloadFont();
        if (currentFont != null) {
            return currentFont.getStringWidth(text);
        }
        return mc.font.width(text);
    }
}