package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Modern BooleanComponent matching ClickGUI 2 iOS-style toggle design.
 */
public class BooleanComponent extends Component {
    private final BooleanSetting setting;
    private float progress;

    public BooleanComponent(BooleanSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
        this.progress = setting.getValue() ? 1.0f : 0.0f;
    }

    @Override
    public float getHeight() {
        return 16f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        float speed = UI.INSTANCE.animSpeed.getFloatValue() * 0.15f;
        if (setting.getValue()) {
            progress = Math.min(1.0f, progress + speed);
        } else {
            progress = Math.max(0.0f, progress - speed);
        }

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, height, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 4f, ClickGuiTheme.TEXT_PRIMARY);

        // Modern iOS toggle switch on the right
        float swW = 16f;
        float swH = 9f;
        float swX = x + width - swW - 6f;
        float swY = y + (height - swH) / 2f;
        boolean swHov = mouseX >= swX && mouseX <= swX + swW && mouseY >= swY && mouseY <= swY + swH;

        int accent = ClickGuiTheme.getAccentColor();
        int offBg = (swHov || hov) ? 0x30FFFFFF : 0x1CFFFFFF;
        int onBg = (swHov || hov) ? accent : ((accent & 0x00FFFFFF) | 0xDD000000);

        int bg = progress > 0.5f ? onBg : offBg;
        SmoothGfx.fillRounded(context, swX, swY, swW, swH, swH * 0.5f, bg);

        float knobX = swX + 1.5f + (swW - 9.0f) * progress;
        SmoothGfx.circle(context, knobX + 3.0f, swY + 4.5f, 3.0f, ClickGuiTheme.TEXT_PRIMARY);
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