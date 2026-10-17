package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

public class BooleanComponent extends Component {
    private final BooleanSetting setting;
    private float progress;

    public BooleanComponent(BooleanSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
        this.progress = setting.getValue() ? 1.0f : 0.0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        float speed = UI.INSTANCE.animSpeed.getFloatValue() * 0.15f;
        if (setting.getValue()) {
            progress = Math.min(1.0f, progress + speed);
        } else {
            progress = Math.max(0.0f, progress - speed);
        }

        // Setting background
        RenderUtil.drawRect(context, x, y, x + width, y + height, UI.INSTANCE.bgColor.getValue());

        // Animated fill bar with accent color
        if (progress > 0.001f) {
            float fillX2 = x + 2.0f + (width - 4.0f) * progress;
            RenderUtil.drawRect(context, x + 2.0f, y, fillX2, y + height, UI.INSTANCE.color.getValue());
        }

        // Setting Name
        float fs = FontManager.settingsScale();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, setting.getName(), x + 4, y + (height - FontManager.INSTANCE.getFontHeightScaled(fs)) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            setting.toggle();
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 1.0f);
            }
            return true;
        }
        return false;
    }
}
