package me.collective.gui.clickgui2;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.render.RenderUtil;
import me.collective.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * Всплывающий пикер цвета — перенос структуры исходника: панель на стекле
 * BACKGROUND/OUTLINE, область Saturation/Value по площади, полоса Hue
 * отдельной колонкой, строка прозрачности и hex-подпись формата
 * "#%02X%02X%02X%02X" (32 кодпоинта, Enter — применить, Backspace — стереть —
 * эти клавиши в v1 цикл редактирования не открывают: значение тянется мышью).
 */
public class ClickGui2Picker {
    private static final float W = 120f;
    private static final float H = 118f;
    private static final float PAD = 8f;
    private static final float SV_W = 86f;
    private static final float SV_H = 70f;
    private static final float HUE_W = 6f;
    private static final float ALPHA_H = 3f;
    private static final float RADIUS = 8f;

    private final ColorSetting target;

    private float x, y;
    private float hue, sat, val;
    private float alpha;

    private boolean draggingSV, draggingHue, draggingAlpha;

    public ClickGui2Picker(ColorSetting target, float px, float py, float screenW, float screenH) {
        this.target = target;
        Color c = target.getValue() != null ? target.getValue() : Color.GRAY;
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
        this.alpha = c.getAlpha() / 255.0f;

        this.x = Math.max(4f, Math.min(screenW - W - 4f, px + 10f));
        this.y = Math.max(4f, Math.min(screenH - H - 4f, py - 10f));
    }

    public float width() {
        return W;
    }

    public boolean inBounds(float mx, float my) {
        return mx >= x && mx <= x + W && my >= y && my <= y + H;
    }

    /** Прямоугольник пикера для блюра: {x1, y1, x2, y2}. */
    public float[] bounds() {
        return new float[]{x, y, x + W, y + H};
    }

    // ── Взаимодействие ──

    public boolean mouseClicked(float mx, float my) {
        if (!inBounds(mx, my)) return false;

        float svX = x + PAD;
        float svY = y + PAD;
        if (mx >= svX && mx <= svX + SV_W && my >= svY && my <= svY + SV_H) {
            draggingSV = true;
            updateSV(mx, my);
            return true;
        }

        float hueX = x + W - PAD - HUE_W;
        if (mx >= hueX && mx <= hueX + HUE_W && my >= svY && my <= svY + SV_H) {
            draggingHue = true;
            updateHue(my);
            return true;
        }

        float alphaY = svY + SV_H + 8f;
        if (mx >= svX && mx <= svX + SV_W && my >= alphaY - 3f && my <= alphaY + 3f) {
            draggingAlpha = true;
            updateAlpha(mx);
            return true;
        }

        return true;
    }

    public boolean mouseDragging(float mx, float my) {
        if (draggingSV) {
            updateSV(mx, my);
            return true;
        }
        if (draggingHue) {
            updateHue(my);
            return true;
        }
        if (draggingAlpha) {
            updateAlpha(mx);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        draggingSV = false;
        draggingHue = false;
        draggingAlpha = false;
    }

    private void updateSV(float mx, float my) {
        sat = Math.max(0f, Math.min(1f, (mx - (x + PAD)) / SV_W));
        val = Math.max(0f, Math.min(1f, 1f - (my - (y + PAD)) / SV_H));
        apply();
    }

    private void updateHue(float my) {
        hue = Math.max(0f, Math.min(0.999f, (my - (y + PAD)) / SV_H));
        apply();
    }

    private void updateAlpha(float mx) {
        alpha = Math.max(0f, Math.min(1f, (mx - (x + PAD)) / SV_W));
        apply();
    }

    private void apply() {
        int packed = Color.HSBtoRGB(hue, sat, val);
        int r = (packed >> 16) & 0xFF;
        int g = (packed >> 8) & 0xFF;
        int b = packed & 0xFF;
        int a = Math.round(alpha * 255f);
        target.setValue(new Color(r, g, b, a));
    }

    // ── Рендер ──

    public void render(DrawContext ctx, float mx, float my) {
        GuiGfx.shadow(ctx, (int) x - 3, (int) y - 3, (int) W + 6, (int) H + 6, 5);
        GuiGfx.fillRounded(ctx, x, y, W, H, RADIUS, ClickGui2Theme.rgb(ClickGui2Theme.Role.BACKGROUND));
        GuiGfx.outlineRounded(ctx, x, y, W, H, RADIUS, ClickGui2Theme.rgb(ClickGui2Theme.Role.OUTLINE));

        FontManager fm = FontManager.INSTANCE;
        float scale = 7f;
        float labelY = y + PAD + SV_H + 12f;

        // область Saturation/Value — билинейная имитация полосами
        float svX = x + PAD;
        float svY = y + PAD;
        int packed = Color.HSBtoRGB(hue, 1f, 1f);
        RenderUtil.drawRect(ctx, svX, svY, svX + SV_W, svY + SV_H,
                new Color((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF));
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) steps;
            float wSeg = SV_W / steps;
            int whiteA = Math.round(255f * (1f - t));
            RenderUtil.drawRect(ctx, svX + i * wSeg, svY, svX + (i + 1) * wSeg, svY + SV_H,
                    new Color(255, 255, 255, whiteA));
        }
        for (int j = 0; j < steps; j++) {
            float t = j / (float) steps;
            float hSeg = SV_H / steps;
            int blackA = Math.round(255f * t);
            RenderUtil.drawRect(ctx, svX, svY + j * hSeg, svX + SV_W, svY + (j + 1) * hSeg,
                    new Color(0, 0, 0, blackA));
        }
        float knobX = svX + sat * SV_W;
        float knobY = svY + (1f - val) * SV_H;
        GuiGfx.dotRing(ctx, knobX, knobY, 3f, ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE));

        // полоса Hue — сегменты по кругу оттенка
        float hueX = x + W - PAD - HUE_W;
        int hueSteps = 24;
        for (int i = 0; i < hueSteps; i++) {
            float t = i / (float) hueSteps;
            float seg = SV_H / hueSteps;
            int hc = Color.HSBtoRGB(t, 1f, 1f) & 0xFFFFFF;
            RenderUtil.drawRect(ctx, hueX, svY + i * seg, hueX + HUE_W, svY + (i + 1) * seg,
                    new Color(0xFF000000 | hc));
        }
        float hueKnobY = svY + hue * SV_H;
        RenderUtil.drawRect(ctx, hueX - 2f, hueKnobY - 1.5f, hueX + HUE_W + 2f, hueKnobY + 1.5f,
                new Color(ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE)));

        // полоса альфы — шахматный фон имитирует прозрачность
        float alphaY = svY + SV_H + 8f;
        for (int i = 0; i < 17; i++) {
            float wSeg = SV_W / 17f;
            int cellAlpha = (i % 2) == 0 ? 30 : 62;
            RenderUtil.drawRect(ctx, svX + i * wSeg, alphaY - 1f, svX + (i + 1) * wSeg, alphaY + ALPHA_H + 1f,
                    new Color(255, 255, 255, cellAlpha));
        }
        RenderUtil.drawRect(ctx, svX, alphaY - 1f, svX + SV_W * alpha, alphaY + ALPHA_H + 1f,
                new Color(Color.HSBtoRGB(hue, sat, val) & 0xFFFFFF));
        float alphaKnobX = svX + SV_W * alpha;
        GuiGfx.dot(ctx, alphaKnobX, alphaY + ALPHA_H / 2f, 3f, ClickGui2Theme.rgb(ClickGui2Theme.Role.CIRCLE));

        // hex-подпись
        Color cur = target.getValue() != null ? target.getValue() : Color.GRAY;
        String hex = String.format("#%02X%02X%02X%02X",
                cur.getAlpha(), cur.getRed(), cur.getGreen(), cur.getBlue());
        fm.drawStringScaled(ctx, hex, x + PAD, labelY, ClickGui2Theme.rgb(ClickGui2Theme.Role.SUR_TEXT), scale);

        fm.drawStringScaled(ctx, "Color", x + W - PAD - fm.getStringWidthScaled("Color", scale), labelY,
                ClickGui2Theme.rgb(ClickGui2Theme.Role.OPEN_SETTING_TEXT), scale);
    }
}
