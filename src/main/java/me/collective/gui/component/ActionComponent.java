package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.ActionSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

/**
 * Кнопка-настройка (ActionSetting): строка настроек, по клику мгновенно
 * выполняет действие. Справа — подпись кнопки, подсвечивается при наведении.
 */
public class ActionComponent extends Component {
    private final ActionSetting setting;
    private float hover;

    public ActionComponent(ActionSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        boolean hov = isHovered(mouseX, mouseY);
        float speed = UI.INSTANCE.animSpeed.getFloatValue() * 0.15f;
        hover = Math.max(0.0f, Math.min(1.0f, hover + (hov ? speed : -speed)));

        RenderUtil.drawRect(context, x, y, x + width, y + height, UI.INSTANCE.bgColor.getValue());
        if (hover > 0.001f) {
            RenderUtil.drawRect(context, x, y, x + width, y + height,
                    new Color(255, 255, 255, (int) (28 * hover)));
        }

        float fs = FontManager.settingsScale();
        float textY = y + (height - FontManager.INSTANCE.getFontHeightScaled(fs)) / 2f + 0.5f;

        FontManager.INSTANCE.drawStringWithShadowScaled(context, setting.getName(), x + 4, textY,
                UI.INSTANCE.textColor.getValue(), fs);

        String btn = setting.getButtonText();
        float btnW = FontManager.INSTANCE.getStringWidthScaled(btn, fs);
        Color accent = UI.INSTANCE.enabledColor.getValue();
        Color btnColor = hover > 0.5f ? accent
                : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 170);
        FontManager.INSTANCE.drawStringWithShadowScaled(context, btn, x + width - 4 - btnW, textY, btnColor, fs);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            setting.run();
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 0.9f);
            }
            return true;
        }
        return false;
    }
}
