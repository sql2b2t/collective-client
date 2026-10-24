package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.Colors;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.ColorSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

/**
 * Modern ColorComponent matching ClickGUI 2 styling with rounded color swatch and sliders.
 */
public class ColorComponent extends Component {
    private final ColorSetting setting;
    private boolean open;
    private int draggingSlider = -1; // 0=Red, 1=Green, 2=Blue, 3=Alpha

    public ColorComponent(ColorSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return open ? (16f + 3f + 5 * 14f + 3f) : 16f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        if (open && draggingSlider >= 0) {
            updateSlider(draggingSlider, mouseX);
        }

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, 16f, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 3.5f, ClickGuiTheme.TEXT_PRIMARY);

        // Color Swatch on the right
        float boxSize = 10f;
        float boxX = x + width - boxSize - 6f;
        float boxY = y + 3f;
        SmoothGfx.fillAndStroke(context, boxX, boxY, boxSize, boxSize, 2.5f, setting.getColor().getRGB(), 0x33FFFFFF, 1.0f);

        if (open) {
            float listY = y + 16f + 2f;
            float listH = 5 * 14f + 2f;
            float listX = x + 3f;
            float listW = width - 6f;

            // Card container
            SmoothGfx.fillAndStroke(context, listX, listY, listW, listH, 3.5f, 0xE00C121C, 0x28FFFFFF, 1.0f);

            Color cur = setting.getValue();
            float curRowY = listY + 2f;

            // Row 1: Rainbow & Sync buttons
            float halfW = (listW - 6f) / 2f;
            boolean rbHov = mouseX >= listX + 2f && mouseX <= listX + 2f + halfW && mouseY >= curRowY && mouseY <= curRowY + 12f;
            int rbBg = setting.isRainbow() ? ClickGuiTheme.getAccentBg() : (rbHov ? 0x24FFFFFF : 0x14FFFFFF);
            int rbStroke = setting.isRainbow() ? ClickGuiTheme.getAccentColor() : 0x20FFFFFF;
            SmoothGfx.fillAndStroke(context, listX + 2f, curRowY, halfW, 12f, 2.5f, rbBg, rbStroke, 1.0f);
            ClickGuiTheme.badge().drawString(context, "Rainbow", listX + 5f, curRowY + 2f, setting.isRainbow() ? ClickGuiTheme.getAccentColor() : ClickGuiTheme.TEXT_MUTED);

            if (Colors.INSTANCE != null && setting != Colors.INSTANCE.themeColor) {
                float syncX = listX + 4f + halfW;
                boolean syncHov = mouseX >= syncX && mouseX <= syncX + halfW && mouseY >= curRowY && mouseY <= curRowY + 12f;
                int syncBg = setting.isSync() ? ClickGuiTheme.getAccentBg() : (syncHov ? 0x24FFFFFF : 0x14FFFFFF);
                int syncStroke = setting.isSync() ? ClickGuiTheme.getAccentColor() : 0x20FFFFFF;
                SmoothGfx.fillAndStroke(context, syncX, curRowY, halfW, 12f, 2.5f, syncBg, syncStroke, 1.0f);
                ClickGuiTheme.badge().drawString(context, "Sync", syncX + 5f, curRowY + 2f, setting.isSync() ? ClickGuiTheme.getAccentColor() : ClickGuiTheme.TEXT_MUTED);
            }
            curRowY += 14f;

            // Rows 2..5: Red, Green, Blue, Alpha
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "R: " + cur.getRed(), cur.getRed() / 255f, 0xFFE04040);
            curRowY += 14f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "G: " + cur.getGreen(), cur.getGreen() / 255f, 0xFF40D040);
            curRowY += 14f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "B: " + cur.getBlue(), cur.getBlue() / 255f, 0xFF4080F0);
            curRowY += 14f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "A: " + cur.getAlpha(), cur.getAlpha() / 255f, 0xFFCCCCCC);
        }
    }

    private void renderSliderRow(GuiGraphicsExtractor context, float rx, float ry, float rw, String label, float pct, int barColor) {
        ClickGuiTheme.badge().drawString(context, label, rx + 2f, ry + 2f, ClickGuiTheme.TEXT_MUTED);

        float trackX = rx + 36f;
        float trackW = rw - 38f;
        float trackY = ry + 4.5f;
        float trackH = 3.5f;

        SmoothGfx.fillRounded(context, trackX, trackY, trackW, trackH, 1.75f, 0x22FFFFFF);
        if (pct > 0.01f) {
            SmoothGfx.fillRounded(context, trackX, trackY, trackW * pct, trackH, 1.75f, barColor);
        }
        float knobX = trackX + trackW * pct;
        SmoothGfx.circle(context, knobX, trackY + 1.75f, 3.0f, ClickGuiTheme.TEXT_PRIMARY);
    }

    private void updateSlider(int sliderIdx, double mouseX) {
        float listX = x + 3f;
        float listW = width - 6f;
        float trackX = listX + 2f + 36f;
        float trackW = (listW - 4f) - 38f;

        float percent = (float) Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackW));
        int val = Math.round(percent * 255);

        switch (sliderIdx) {
            case 0 -> setting.setRed(val);
            case 1 -> setting.setGreen(val);
            case 2 -> setting.setBlue(val);
            case 3 -> setting.setAlpha(val);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;

        // Header click (y to y + 16)
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16f) {
            open = !open;
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, open ? 1.2f : 0.9f);
            }
            return true;
        }

        if (open) {
            float listY = y + 16f + 2f;
            float listX = x + 3f;
            float listW = width - 6f;
            float curRowY = listY + 2f;

            // Row 1: Rainbow & Sync buttons
            if (mouseY >= curRowY && mouseY <= curRowY + 12f) {
                float halfW = (listW - 6f) / 2f;
                if (mouseX >= listX + 2f && mouseX <= listX + 2f + halfW) {
                    setting.setRainbow(!setting.isRainbow());
                    if (UI.INSTANCE.sounds.getValue()) SoundUtil.playClick(1.0f, 1.0f);
                    return true;
                } else if (Colors.INSTANCE != null && setting != Colors.INSTANCE.themeColor && mouseX >= listX + 4f + halfW && mouseX <= listX + 4f + halfW * 2f) {
                    setting.setSync(!setting.isSync());
                    if (UI.INSTANCE.sounds.getValue()) SoundUtil.playClick(1.0f, 1.0f);
                    return true;
                }
            }
            curRowY += 14f;

            // Rows 2..5: Red, Green, Blue, Alpha
            for (int i = 0; i < 4; i++) {
                if (mouseY >= curRowY && mouseY <= curRowY + 13f && mouseX >= listX && mouseX <= listX + listW) {
                    if (button == 0) {
                        draggingSlider = i;
                        updateSlider(i, mouseX);
                        if (UI.INSTANCE.sounds.getValue()) SoundUtil.playClick(1.0f, 1.1f);
                        return true;
                    }
                }
                curRowY += 14f;
            }
        }

        return false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingSlider = -1;
        }
    }
}