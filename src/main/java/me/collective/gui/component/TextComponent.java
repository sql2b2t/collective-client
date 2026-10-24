package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * Modern TextComponent with cursor navigation, arrow keys, Home/End, Delete, and clipboard support.
 */
public class TextComponent extends Component {
    private final StringSetting setting;
    protected boolean editing;
    private int cursor;

    public TextComponent(StringSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return 16f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, height, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 3.5f, ClickGuiTheme.TEXT_PRIMARY);

        // Input pill on the right
        String value = setting.getValue() == null ? "" : setting.getValue();
        cursor = Math.max(0, Math.min(cursor, value.length()));

        String shown = value.isEmpty() && !editing ? "..." : value;
        float textW = ClickGuiTheme.badge().getStringWidth(shown);
        float sW = Math.max(28f, textW + 10f);
        float sX = x + width - sW - 6f;
        float sY = y + 2f;
        boolean pillHov = mouseX >= sX && mouseX <= sX + sW && mouseY >= sY && mouseY <= sY + 12f;

        int bg = editing ? ClickGuiTheme.getAccentBg() : (pillHov ? 0x24FFFFFF : 0x14FFFFFF);
        int stroke = editing ? ClickGuiTheme.getAccentColor() : 0x20FFFFFF;
        SmoothGfx.fillAndStroke(context, sX, sY, sW, 12f, 3f, bg, stroke, 1.0f);

        // Draw current text
        ClickGuiTheme.badge().drawString(context, shown, sX + 5f, sY + 2f, editing ? ClickGuiTheme.getAccentColor() : ClickGuiTheme.TEXT_PRIMARY);

        // Draw blinking vertical caret cursor at exact cursor position
        if (editing && (System.currentTimeMillis() % 800 < 450)) {
            String beforeCursor = value.substring(0, cursor);
            float caretX = sX + 5f + ClickGuiTheme.badge().getStringWidth(beforeCursor);
            RenderUtil.drawRect(context, caretX, sY + 2.5f, caretX + 1f, sY + 9.5f, ClickGuiTheme.getAccentColor());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            editing = !editing;
            if (editing) {
                String val = setting.getValue() == null ? "" : setting.getValue();
                cursor = val.length();
            }
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 1.0f);
            }
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
        if (chr >= 32 && chr != 127) {
            String val = setting.getValue() == null ? "" : setting.getValue();
            if (val.length() < 64) {
                cursor = Math.max(0, Math.min(cursor, val.length()));
                String next = val.substring(0, cursor) + chr + val.substring(cursor);
                setting.setValue(next);
                cursor++;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key) {
        if (!editing) return false;
        String val = setting.getValue() == null ? "" : setting.getValue();
        cursor = Math.max(0, Math.min(cursor, val.length()));

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT) {
            if (cursor > 0) cursor--;
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            if (cursor < val.length()) cursor++;
            return true;
        }
        if (key == GLFW.GLFW_KEY_HOME) {
            cursor = 0;
            return true;
        }
        if (key == GLFW.GLFW_KEY_END) {
            cursor = val.length();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursor > 0) {
                String next = val.substring(0, cursor - 1) + val.substring(cursor);
                setting.setValue(next);
                cursor--;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE) {
            if (cursor < val.length()) {
                String next = val.substring(0, cursor) + val.substring(cursor + 1);
                setting.setValue(next);
            }
            return true;
        }
        // Paste support: Ctrl+V
        if (key == GLFW.GLFW_KEY_V && isCtrlDown()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) {
                String clip = mc.keyboard.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    clip = clip.replaceAll("[\\r\\n\\t]", "");
                    String next = val.substring(0, cursor) + clip + val.substring(cursor);
                    if (next.length() <= 64) {
                        setting.setValue(next);
                        cursor += clip.length();
                    }
                }
            }
            return true;
        }
        // Copy support: Ctrl+C
        if (key == GLFW.GLFW_KEY_C && isCtrlDown()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.keyboard != null) {
                mc.keyboard.setClipboard(val);
            }
            return true;
        }
        return false;
    }

    private boolean isCtrlDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long h = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public void clearFocus() {
        editing = false;
    }
}
