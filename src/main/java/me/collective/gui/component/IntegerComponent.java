package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.IntegerSetting;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * Modern IntegerComponent matching ClickGUI 2 slider design.
 * Drag or scroll to adjust, or right-click to enter a precise number.
 */
public class IntegerComponent extends Component {
    private final IntegerSetting setting;
    private boolean dragging;
    private boolean editing;
    private String buffer = "";
    private int cursor;

    public IntegerComponent(IntegerSetting setting, float x, float y, float width, float height) {
        super(x, y, width, ClickGuiTheme.SLIDER_HEIGHT);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return ClickGuiTheme.SLIDER_HEIGHT;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        if (dragging) {
            updateValue(mouseX);
        }

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, height, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 2f, ClickGuiTheme.TEXT_PRIMARY);

        // Value or text input on the right
        String valStr = editing ? buffer : String.valueOf(setting.getValue());
        float valW = ClickGuiTheme.badge().getStringWidth(valStr);
        int valColor = editing ? 0xFFFFFFFF : ClickGuiTheme.getAccentColor();
        float valX = x + width - valW - 6f;
        ClickGuiTheme.badge().drawString(context, valStr, valX, y + 2f, valColor);

        // Blinking caret cursor
        if (editing && (System.currentTimeMillis() % 800 < 450)) {
            cursor = Math.max(0, Math.min(cursor, buffer.length()));
            String beforeCursor = buffer.substring(0, cursor);
            float caretX = valX + ClickGuiTheme.badge().getStringWidth(beforeCursor);
            RenderUtil.drawRect(context, caretX, y + 2f, caretX + 1f, y + 10f, ClickGuiTheme.getAccentColor());
        }

        // Rounded slider track on second line
        float barY = y + 14f;
        float barH = 4f;
        float trackW = width - 12f;
        float trackX = x + 6f;

        double min = setting.getMin();
        double max = setting.getMax();
        float pct = (float) Math.max(0.0, Math.min(1.0, (setting.getValue() - min) / (max - min)));

        // Background track
        SmoothGfx.fillRounded(context, trackX, barY, trackW, barH, 2f, 0x22FFFFFF);

        // Accent fill
        if (pct > 0.01f) {
            SmoothGfx.fillRounded(context, trackX, barY, trackW * pct, barH, 2f, ClickGuiTheme.getAccentColor());
        }

        // Draggable knob
        float knobX = trackX + trackW * pct;
        if (dragging) {
            SmoothGfx.circle(context, knobX, barY + 2f, 5.0f, (ClickGuiTheme.getAccentColor() & 0x00FFFFFF) | 0x55000000);
        }
        SmoothGfx.circle(context, knobX, barY + 2f, 3.5f, ClickGuiTheme.TEXT_PRIMARY);
    }

    private void updateValue(double mouseX) {
        float trackX = x + 6f;
        float trackW = width - 12f;
        double pct = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackW));
        int val = (int) Math.round(setting.getMin() + pct * (setting.getMax() - setting.getMin()));
        setting.setValue(val);
    }

    private int clamp(int value) {
        return Math.max(setting.getMin(), Math.min(setting.getMax(), value));
    }

    private void commit() {
        String trimmed = buffer.trim();
        if (!trimmed.isEmpty()) {
            try {
                setting.setValue(clamp(Integer.parseInt(trimmed)));
            } catch (NumberFormatException ignored) {}
        }
        buffer = "";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                this.dragging = true;
                this.editing = false;
                updateValue(mouseX);
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.1f);
                }
                return true;
            } else if (button == 1) {
                this.editing = !editing;
                this.buffer = String.valueOf(setting.getValue());
                this.cursor = this.buffer.length();
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
                }
                return true;
            }
        }
        if (editing) {
            commit();
            editing = false;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!setting.isVisible() || editing || !isHovered(mouseX, mouseY)) return false;
        if (verticalAmount != 0) {
            setting.setValue(clamp(setting.getValue() + (verticalAmount > 0 ? 1 : -1)));
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
    }

    @Override
    public boolean charTyped(char chr) {
        if (!editing) return false;
        if (chr >= '0' && chr <= '9' && buffer.length() < 9) {
            cursor = Math.max(0, Math.min(cursor, buffer.length()));
            buffer = buffer.substring(0, cursor) + chr + buffer.substring(cursor);
            cursor++;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!editing) return false;
        cursor = Math.max(0, Math.min(cursor, buffer.length()));

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            if (key != GLFW.GLFW_KEY_ESCAPE) {
                commit();
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 0.8f);
                }
            }
            editing = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            if (cursor > 0) cursor--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (cursor < buffer.length()) cursor++;
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            cursor = buffer.length();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursor > 0) {
                buffer = buffer.substring(0, cursor - 1) + buffer.substring(cursor);
                cursor--;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (cursor < buffer.length()) {
                buffer = buffer.substring(0, cursor) + buffer.substring(cursor + 1);
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
}
