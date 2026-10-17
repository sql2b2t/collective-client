package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class SliderComponent extends Component {
    private final NumberSetting setting;
    private boolean dragging;
    private boolean textEditing;
    private String textInput = "";

    public SliderComponent(NumberSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        if (dragging) {
            updateValue(mouseX);
        }

        // Setting background
        RenderUtil.drawRect(context, x, y, x + width, y + height, UI.INSTANCE.bgColor.getValue());

        // Slider fill bar
        double min = setting.getMin();
        double max = setting.getMax();
        double val = setting.getValue();
        float percent = (float) ((val - min) / (max - min));
        float barWidth = Math.max(0, Math.min(width - 4, (width - 4) * percent));

        RenderUtil.drawRect(context, x + 2.0f, y, x + 2.0f + barWidth, y + height, UI.INSTANCE.color.getValue());

        // Text display
        String text;
        if (textEditing) {
            text = setting.getName() + ": " + textInput + "_";
        } else {
            String valStr = setting.isInteger() ? String.valueOf(setting.getIntValue()) : String.format("%.2f", setting.getValue());
            text = setting.getName() + ": " + valStr;
        }

        float fs = FontManager.settingsScale();
        FontManager.INSTANCE.drawStringWithShadowScaled(context, text, x + 4,
            y + (height - FontManager.INSTANCE.getFontHeightScaled(fs)) / 2f + 0.5f, UI.INSTANCE.textColor.getValue(), fs);
    }

    private void updateValue(double mouseX) {
        double diff = mouseX - (x + 2);
        double total = width - 4;
        double percent = Math.max(0, Math.min(1.0, diff / total));
        double range = setting.getMax() - setting.getMin();
        setting.setValue(setting.getMin() + range * percent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                this.dragging = true;
                this.textEditing = false;
                updateValue(mouseX);
                return true;
            } else if (button == 1) {
                this.textEditing = !textEditing;
                this.textInput = setting.isInteger() ? String.valueOf(setting.getIntValue()) : String.format("%.2f", setting.getValue());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!setting.isVisible() || !isHovered(mouseX, mouseY)) return false;
        double step = setting.getIncrement() > 0 ? setting.getIncrement() : 0.1;
        double change = (verticalAmount > 0 ? 1 : -1) * step;
        setting.setValue(setting.getValue() + change);
        return true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
    }

    @Override
    public boolean keyPressed(int key) {
        if (textEditing) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                try {
                    setting.setValue(Double.parseDouble(textInput));
                } catch (Exception ignored) {}
                textEditing = false;
                return true;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!textInput.isEmpty()) {
                    textInput = textInput.substring(0, textInput.length() - 1);
                }
                return true;
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                textEditing = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr) {
        if (textEditing && (Character.isDigit(chr) || chr == '.' || chr == '-')) {
            textInput += chr;
            return true;
        }
        return false;
    }
}
