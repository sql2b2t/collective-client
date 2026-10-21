package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/** Текстовая настройка (StringSetting): ввод по всей строке, Enter — применить. */
public class TextComponent extends Component {
    private static final int LIMIT = 32;

    private final StringSetting setting;
    protected boolean editing;

    public TextComponent(StringSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        RenderUtil.drawRect(context, x, y, x + width, y + height, UI.INSTANCE.bgColor.getValue());

        float fs = FontManager.settingsScale();
        float textH = FontManager.INSTANCE.getFontHeightScaled(fs);

        // Поле ввода справа; при компактных панелях забирает половину строки
        float boxW = Math.min(width * 0.5f, 110f);
        float boxH = height - 3f;
        float boxX = x + width - boxW - 1f;
        float boxY = y + 1.5f;

        RenderUtil.drawRect(context, boxX, boxY, boxX + boxW, boxY + boxH,
            UI.INSTANCE.bgButton.getValue());
        if (editing) {
            // Подчёркивание в акценте — режим ввода активен
            RenderUtil.drawRect(context, boxX, boxY + boxH - 1f, boxX + boxW, boxY + boxH,
                UI.INSTANCE.color.getValue());
        }

        String value = setting.getValue() == null ? "" : setting.getValue();
        String shown = value.isEmpty() ? (editing ? "" : "...") : value;
        // Не влезает — показываем хвост
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            editing = true;
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
        String current = setting.getValue() == null ? "" : setting.getValue();
        if (chr >= 32 && chr != 127 && current.length() < LIMIT) {
            setting.setValue(current + chr);
        }
        return true;
    }

    @Override
    public void clearFocus() {
        editing = false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!editing) return false;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
            || key == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            if (key != GLFW.GLFW_KEY_ESCAPE && UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 0.8f);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            String current = setting.getValue();
            if (current != null && !current.isEmpty()) {
                setting.setValue(current.substring(0, current.length() - 1));
            }
            return true;
        }
        return false;
    }
}
