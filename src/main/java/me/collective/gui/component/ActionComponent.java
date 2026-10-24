package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.ActionSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Modern ActionComponent matching ClickGUI 2 pill button design.
 */
public class ActionComponent extends Component {
    private final ActionSetting setting;

    public ActionComponent(ActionSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return 16f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, height, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 3.5f, ClickGuiTheme.TEXT_PRIMARY);

        // Action Pill Button on the right
        String actText = setting.getButtonText();
        float aW = ClickGuiTheme.badge().getStringWidth(actText) + 8f;
        float aX = x + width - aW - 6f;
        float aY = y + 2f;
        boolean btnHov = mouseX >= aX && mouseX <= aX + aW && mouseY >= aY && mouseY <= aY + 12f;

        int bg = btnHov ? 0x38FFFFFF : 0x20FFFFFF;
        SmoothGfx.fillAndStroke(context, aX, aY, aW, 12f, 3f, bg, 0x24FFFFFF, 1.0f);
        ClickGuiTheme.badge().drawString(context, actText, aX + 4f, aY + 2f, ClickGuiTheme.getAccentColor());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            setting.run();
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 1.1f);
            }
            return true;
        }
        return false;
    }
}