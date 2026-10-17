package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Компонент для ModeSetting (ЛКМ листает, ПКМ раскрывает список).
 */
public class ModeComponent extends Component {
    private final ModeSetting setting;
    private boolean open;

    public ModeComponent(ModeSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;
        float fs = FontManager.settingsScale();
        int fh = FontManager.INSTANCE.getFontHeightScaled(fs);

        RenderUtil.drawRect(context, x, y, x + width, y + 11, UI.INSTANCE.bgColor.getValue());

        String text = setting.getName() + ": " + setting.getValue();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, text, x + 4, y + (11 - fh) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);

        if (open) {
            List<String> modes = setting.getModes();
            float curY = y + 11;
            for (String val : modes) {
                boolean selected = val.trim().equalsIgnoreCase(setting.getValue().trim());
                RenderUtil.drawRect(context, x + 2, curY, x + width - 2, curY + 11, UI.INSTANCE.bgColor.getValue());

                if (selected) {
                    RenderUtil.drawRect(context, x + 3, curY + 1, x + 5, curY + 10, UI.INSTANCE.color.getValue());
                }

                int col = selected ? UI.INSTANCE.color.getValue().getRGB() : UI.INSTANCE.textColor.getValue().getRGB();
                FontManager.INSTANCE.drawStringWithShadowScaled(context, val, x + 8, curY + (11 - fh) / 2f + 0.5f, col, fs);
                curY += 11;
            }

            if (UI.INSTANCE.line.getValue()) {
                RenderUtil.drawOutline(context, x + 2, y + 11, width - 4, modes.size() * 11, 1, UI.INSTANCE.color.getValue().getRGB());
            }
        }
    }

    @Override
    public float getHeight() {
        if (open) {
            return 11 + (setting.getModes().size() * 11);
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
            List<String> modes = setting.getModes();
            float curY = y + 11;
            for (int i = 0; i < modes.size(); i++) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= curY && mouseY <= curY + 11) {
                    setting.setValue(modes.get(i));
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
}
