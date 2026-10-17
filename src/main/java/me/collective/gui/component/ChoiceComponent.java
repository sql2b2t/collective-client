package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ChoiceSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Компонент для ChoiceSetting: заголовок группы + раскрывающийся список
 * тумблеров (ЛКМ/ПКМ по заголовку раскрывает).
 */
public class ChoiceComponent extends Component {
    private final ChoiceSetting setting;
    private final List<BooleanComponent> options = new ArrayList<>();
    private boolean open;

    public ChoiceComponent(ChoiceSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
        for (BooleanSetting opt : setting.getOptions()) {
            options.add(new BooleanComponent(opt, x, y, width, 11));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        RenderUtil.drawRect(context, x, y, x + width, y + 11, UI.INSTANCE.bgColor.getValue());

        long enabled = setting.getOptions().stream().filter(s -> s.getValue()).count();
        String text = setting.getName() + " [" + enabled + "/" + setting.getOptions().size() + "]" + (open ? " -" : " +");
        float fs = FontManager.settingsScale();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, text, x + 4, y + (11 - FontManager.INSTANCE.getFontHeightScaled(fs)) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);

        if (open) {
            float curY = y + 11;
            for (BooleanComponent c : options) {
                c.setX(x + 6);
                c.setY(curY);
                c.setWidth(width - 6);
                c.render(context, mouseX, mouseY, delta);
                curY += 11;
            }
        }
    }

    @Override
    public float getHeight() {
        if (open) {
            return 11 + (options.size() * 11);
        }
        return 11;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;

        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 11) {
            if (button == 0 || button == 1) {
                open = !open;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.2f);
                }
                return true;
            }
        }

        if (open) {
            for (BooleanComponent c : options) {
                if (c.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (open) {
            for (BooleanComponent c : options) {
                c.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean keyPressed(int key) {
        if (open) {
            for (BooleanComponent c : options) {
                if (c.keyPressed(key)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (open) {
            for (BooleanComponent c : options) {
                if (c.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
            }
        }
        return false;
    }
}
