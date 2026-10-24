package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.SettingColorSetting;
import me.collective.util.SettingColor;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Modern SettingColorComponent matching ClickGUI 2 styling with rounded color swatch and sliders.
 */
public class SettingColorComponent extends Component {
    private final SettingColorSetting setting;
    private boolean open;
    private int draggingSlider = -1; // 0=Red, 1=Green, 2=Blue, 3=Alpha

    public SettingColorComponent(SettingColorSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return open ? (16f + 3f + 4 * 14f + 3f) : 16f;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        SettingColor cur = setting.getValue();
        if (cur == null) return;

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
        SmoothGfx.fillAndStroke(context, boxX, boxY, boxSize, boxSize, 2.5f, cur.toAwt().getRGB(), 0x33FFFFFF, 1.0f);

        if (open) {
            float listY = y + 16f + 2f;
            float listH = 4 * 14f + 2f;
            float listX = x + 3f;
            float listW = width - 6f;

            // Card container
            SmoothGfx.fillAndStroke(context, listX, listY, listW, listH, 3.5f, 0xE00C121C, 0x28FFFFFF, 1.0f);

            float curRowY = listY + 2f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "R: " + cur.r, cur.r / 255f, 0xFFE04040);
            curRowY += 14f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "G: " + cur.g, cur.g / 255f, 0xFF40D040);
            curRowY += 14f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "B: " + cur.b, cur.b / 255f, 0xFF4080F0);
            curRowY += 14f;
            renderSliderRow(context, listX + 2f, curRowY, listW - 4f, "A: " + cur.a, cur.a / 255f, 0xFFCCCCCC);
        }
    }

    private void renderSliderRow(GuiGraphics context, float rx, float ry, float rw, String label, float pct, int barColor) {
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

        SettingColor cur = setting.getValue();
        if (cur == null) return;
        switch (sliderIdx) {
            case 0 -> cur.r = val;
            case 1 -> cur.g = val;
            case 2 -> cur.b = val;
            case 3 -> cur.a = val;
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