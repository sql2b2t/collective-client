package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.IntegerSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Целочисленная настройка: ввод цифрами (Enter — применить) или колесико. */
public class IntegerComponent extends Component {
    private final IntegerSetting setting;
    private boolean editing;
    private String buffer = "";

    public IntegerComponent(IntegerSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        RenderUtil.drawRect(context, x, y, x + width, y + height, UI.INSTANCE.bgColor.getValue());

        float fs = FontManager.settingsScale();
        float textH = FontManager.INSTANCE.getFontHeightScaled(fs);

        float boxW = Math.min(width * 0.5f, 110f);
        float boxH = height - 3f;
        float boxX = x + width - boxW - 1f;
        float boxY = y + 1.5f;

        RenderUtil.drawRect(context, boxX, boxY, boxX + boxW, boxY + boxH,
            UI.INSTANCE.bgButton.getValue());
        if (editing) {
            RenderUtil.drawRect(context, boxX, boxY + boxH - 1f, boxX + boxW, boxY + boxH,
                UI.INSTANCE.color.getValue());
        }

        String shown = editing ? buffer : String.valueOf(setting.getValue());
        while (shown.length() > 1
            && FontManager.INSTANCE.getStringWidthScaled(shown, fs) > boxW - 8f) {
            shown = shown.substring(1);
        }

        float boxTextY = boxY + (boxH - textH) / 2f;
        FontManager.INSTANCE.drawStringWithShadowScaled(context, shown, boxX + 4f, boxTextY,
            UI.INSTANCE.enabledColor.getValue(), fs);
        if (editing) {
            float caretX = boxX + 4f + FontManager.INSTANCE.getStringWidthScaled(shown, fs) + 1f;
            if (caretX > boxX + boxW - 3f) caretX = boxX + boxW - 3f;
            RenderUtil.drawRect(context, caretX, boxY + 2f, caretX + 1f, boxY + boxH - 2f,
                UI.INSTANCE.enabledColor.getValue());
        }

        FontManager.INSTANCE.drawStringWithShadowScaled(context, setting.getName(), x + 4f,
            y + (height - textH) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);
    }

    private int clamp(int value) {
        return Math.max(setting.getMin(), Math.min(setting.getMax(), value));
    }

    private void commit() {
        String trimmed = buffer.trim();
        if (!trimmed.isEmpty()) {
            try {
                setting.setValue(clamp(Integer.parseInt(trimmed)));
            } catch (NumberFormatException ignored) {
            }
        }
        buffer = "";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            editing = true;
            buffer = String.valueOf(setting.getValue());
            return true;
        }
        if (editing) {
            editing = false;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr) {
        if (!editing) return false;
        if (chr >= '0' && chr <= '9' && buffer.length() < 9) {
            buffer += chr;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!editing) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
            || key == GLFW.GLFW_KEY_ESCAPE) {
            boolean wasEscape = key == GLFW.GLFW_KEY_ESCAPE;
            if (!wasEscape) {
                commit();
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 0.8f);
                }
            }
            editing = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
            }
            return true;
        }
        return false;
    }

    @Override
    public void clearFocus() {
        editing = false;
        buffer = "";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!setting.isVisible() || editing) return false;
        if (verticalAmount != 0 && isHovered(mouseX, mouseY)) {
            setting.setValue(clamp(setting.getValue() + (verticalAmount > 0 ? 1 : -1)));
            return true;
        }
        return false;
    }
}
