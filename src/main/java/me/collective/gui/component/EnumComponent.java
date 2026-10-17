package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.EnumSetting;
import net.minecraft.client.gui.DrawContext;

public class EnumComponent extends Component {
    private final EnumSetting<?> setting;
    private boolean open;

    public EnumComponent(EnumSetting<?> setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;
        float fs = FontManager.settingsScale();
        int fh = FontManager.INSTANCE.getFontHeightScaled(fs);

        // Setting background
        RenderUtil.drawRect(context, x, y, x + width, y + 11, UI.INSTANCE.bgColor.getValue());

        // Text: Name: Mode
        String text = setting.getName() + ": " + setting.getValue().name();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, text, x + 4, y + (11 - fh) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);

        // If open, render list of options
        if (open) {
            Enum<?>[] values = setting.getValues();
            float curY = y + 11;
            for (Enum<?> val : values) {
                boolean selected = val == setting.getValue();
                RenderUtil.drawRect(context, x + 2, curY, x + width - 2, curY + 11, UI.INSTANCE.bgColor.getValue());

                if (selected) {
                    RenderUtil.drawRect(context, x + 3, curY + 1, x + 5, curY + 10, UI.INSTANCE.color.getValue());
                }

                int col = selected ? UI.INSTANCE.color.getValue().getRGB() : UI.INSTANCE.textColor.getValue().getRGB();
                FontManager.INSTANCE.drawStringWithShadowScaled(context, val.name(), x + 8, curY + (11 - fh) / 2f + 0.5f, col, fs);
                curY += 11;
            }

            if (UI.INSTANCE.line.getValue()) {
                RenderUtil.drawOutline(context, x + 2, y + 11, width - 4, values.length * 11, 1, UI.INSTANCE.color.getValue().getRGB());
            }
        }
    }

    @Override
    public float getHeight() {
        if (open) {
            return 11 + (setting.getValues().length * 11);
        }
        return 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 11) {
            if (button == 0) {
                setting.cycle();
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
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
            Enum<?>[] values = setting.getValues();
            float curY = y + 11;
            for (int i = 0; i < values.length; i++) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= curY && mouseY <= curY + 11) {
                    setEnumOrdinal(i);
                    if (UI.INSTANCE.sounds.getValue()) {
                        SoundUtil.playClick(1.0f, 1.0f);
                    }
                    return true;
                }
                curY += 11;
            }
        }

        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setEnumOrdinal(int ordinal) {
        EnumSetting raw = setting;
        raw.setValue(raw.getValues()[ordinal]);
    }
}
