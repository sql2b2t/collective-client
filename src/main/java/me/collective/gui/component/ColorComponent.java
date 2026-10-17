package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.Colors;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class ColorComponent extends Component {
    private final ColorSetting setting;
    private boolean open;
    private int draggingSlider = -1; // 0=Red, 1=Green, 2=Blue, 3=Alpha

    public ColorComponent(ColorSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        // Process slider dragging
        if (open && draggingSlider >= 0) {
            updateSlider(draggingSlider, mouseX);
        }

        // Main setting bar (height 11)
        RenderUtil.drawRect(context, x, y, x + width, y + 11, UI.INSTANCE.bgColor.getValue());
        float fs = FontManager.settingsScale();
        int fh = FontManager.INSTANCE.getFontHeightScaled(fs);
        FontManager.INSTANCE.drawStringWithShadowScaled(context, setting.getName(), x + 4, y + (11 - fh) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);

        // Color preview box
        float boxX1 = x + width - 13;
        float boxX2 = x + width - 3;
        float boxY1 = y + 2;
        float boxY2 = y + 9;
        RenderUtil.drawRect(context, boxX1, boxY1, boxX2, boxY2, setting.getColor());
        RenderUtil.drawOutline(context, boxX1, boxY1, boxX2 - boxX1, boxY2 - boxY1, 1, 0xFF404045);

        if (open) {
            float rowY = y + 11;
            Color cur = setting.getValue();

            // Row 1: Rainbow & Sync buttons
            RenderUtil.drawRect(context, x + 2, rowY, x + width - 2, rowY + 11, UI.INSTANCE.bgColor.getValue());
            float halfW = (width - 4) / 2f;

            // Rainbow toggle
            String rbText = "Rainbow: " + (setting.isRainbow() ? "ON" : "OFF");
            int rbCol = setting.isRainbow() ? UI.INSTANCE.color.getValue().getRGB() : UI.INSTANCE.textColor.getValue().getRGB();
            FontManager.INSTANCE.drawStringWithShadowScaled(context, rbText, x + 4, rowY + (11 - fh) / 2f + 0.5f, rbCol, fs);

            // Sync toggle
            if (Colors.INSTANCE != null && setting != Colors.INSTANCE.themeColor) {
                String syncText = "Sync: " + (setting.isSync() ? "ON" : "OFF");
                int syncCol = setting.isSync() ? UI.INSTANCE.color.getValue().getRGB() : UI.INSTANCE.textColor.getValue().getRGB();
                FontManager.INSTANCE.drawStringWithShadowScaled(context, syncText, x + 2 + halfW + 2, rowY + (11 - fh) / 2f + 0.5f, syncCol, fs);
            }
            rowY += 11;

            // Row 2: Red slider
            renderSlider(context, rowY, "Red: " + cur.getRed(), cur.getRed() / 255f, new Color(220, 60, 60, 200));
            rowY += 11;

            // Row 3: Green slider
            renderSlider(context, rowY, "Green: " + cur.getGreen(), cur.getGreen() / 255f, new Color(60, 200, 60, 200));
            rowY += 11;

            // Row 4: Blue slider
            renderSlider(context, rowY, "Blue: " + cur.getBlue(), cur.getBlue() / 255f, new Color(60, 100, 240, 200));
            rowY += 11;

            // Row 5: Alpha slider
            renderSlider(context, rowY, "Alpha: " + cur.getAlpha(), cur.getAlpha() / 255f, new Color(180, 180, 180, 200));
        }
    }

    private void renderSlider(DrawContext context, float sY, String label, float percent, Color barColor) {
        RenderUtil.drawRect(context, x + 2, sY, x + width - 2, sY + 11, UI.INSTANCE.bgColor.getValue());
        float barW = Math.max(0, Math.min(width - 6, (width - 6) * percent));
        RenderUtil.drawRect(context, x + 3, sY + 1, x + 3 + barW, sY + 10, barColor);
        float fs = FontManager.settingsScale();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, label, x + 5, sY + (11 - FontManager.INSTANCE.getFontHeightScaled(fs)) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);
    }

    private void updateSlider(int sliderIdx, double mouseX) {
        double diff = mouseX - (x + 3);
        double total = width - 6;
        float percent = (float) Math.max(0.0, Math.min(1.0, diff / total));
        int val = Math.round(percent * 255);

        switch (sliderIdx) {
            case 0 -> setting.setRed(val);
            case 1 -> setting.setGreen(val);
            case 2 -> setting.setBlue(val);
            case 3 -> setting.setAlpha(val);
        }
    }

    @Override
    public float getHeight() {
        return open ? 11 + 11 * 5 : 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;

        // Header click
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 11) {
            if (button == 0) {
                open = !open;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, open ? 1.2f : 0.9f);
                }
                return true;
            } else if (button == 1) {
                open = !open;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.2f);
                }
                return true;
            }
        }

        if (open) {
            float rowY = y + 11;
            // Row 1: Rainbow & Sync buttons
            if (mouseY >= rowY && mouseY < rowY + 11 && mouseX >= x && mouseX <= x + width) {
                float halfW = (width - 4) / 2f;
                if (mouseX < x + 2 + halfW) {
                    setting.setRainbow(!setting.isRainbow());
                } else if (Colors.INSTANCE != null && setting != Colors.INSTANCE.themeColor) {
                    setting.setSync(!setting.isSync());
                }
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
                }
                return true;
            }
            rowY += 11;

            // Rows 2..5: Red, Green, Blue, Alpha
            for (int i = 0; i < 4; i++) {
                if (mouseY >= rowY && mouseY < rowY + 11 && mouseX >= x && mouseX <= x + width) {
                    if (button == 0) {
                        draggingSlider = i;
                        updateSlider(i, mouseX);
                        return true;
                    }
                }
                rowY += 11;
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
