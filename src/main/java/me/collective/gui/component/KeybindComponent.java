package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.KeybindSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class KeybindComponent extends Component {
    private final KeybindSetting setting;
    private boolean binding;

    public KeybindComponent(KeybindSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderUtil.drawRect(context, x, y, x + width, y + height, UI.INSTANCE.bgColor.getValue());

        String keyName = binding ? "..." : setting.getKeyName().toUpperCase();
        String text = "Key: " + keyName;

        int col = binding ? Color.YELLOW.getRGB() : UI.INSTANCE.textColor.getValue().getRGB();
        float fs = FontManager.settingsScale();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, text, x + 4, y + (height - FontManager.INSTANCE.getFontHeightScaled(fs)) / 2f + 0.5f, col, fs);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (!binding) {
                if (button == 0) {
                    this.binding = true;
                    if (UI.INSTANCE.sounds.getValue()) {
                        SoundUtil.playClick(1.0f, 1.0f);
                    }
                    return true;
                }
            } else {
                // If clicked while binding, bind that mouse button! (button 0 = LMB, button 1 = RMB, etc.)
                setting.setValue(-100 - button);
                this.binding = false;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (binding) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                setting.setValue(GLFW.GLFW_KEY_UNKNOWN);
            } else {
                setting.setValue(key);
            }
            this.binding = false;
            return true;
        }
        return false;
    }
}
