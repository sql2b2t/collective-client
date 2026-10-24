package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.render.SoundUtil;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import me.collective.util.KeyHelper;

/**
 * Modern SliderComponent matching ClickGUI 2 slider styling with rounded track and draggable knob.
 */
public class SliderComponent extends Component {
    private final NumberSetting setting;
    private boolean dragging;
    private boolean textEditing;
    private String textInput = "";
    private int cursor;

    public SliderComponent(NumberSetting setting, float x, float y, float width, float height) {
        super(x, y, width, ClickGuiTheme.SLIDER_HEIGHT);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return ClickGuiTheme.SLIDER_HEIGHT;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
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
        String valStr = textEditing ? textInput : (setting.isInteger() ? String.valueOf(setting.getIntValue()) : String.format("%.2f", setting.getValue()));
        float valW = ClickGuiTheme.badge().getStringWidth(valStr);
        int valColor = textEditing ? 0xFFFFFFFF : ClickGuiTheme.getAccentColor();
        float valX = x + width - valW - 6f;
        ClickGuiTheme.badge().drawString(context, valStr, valX, y + 2f, valColor);

        if (textEditing) {
            cursor = Math.max(0, Math.min(cursor, textInput.length()));
            boolean caret = (System.currentTimeMillis() / 450) % 2 == 0;
            if (caret) {
                float caretX = valX + ClickGuiTheme.badge().getStringWidth(textInput.substring(0, cursor));
                RenderUtil.drawRect(context, caretX, y + 2f, caretX + 1f, y + 10f, ClickGuiTheme.getAccentColor());
            }
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
        double range = setting.getMax() - setting.getMin();
        setting.setValue(setting.getMin() + range * pct);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                this.dragging = true;
                this.textEditing = false;
                updateValue(mouseX);
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.1f);
                }
                return true;
            } else if (button == 1) {
                this.textEditing = !textEditing;
                this.textInput = setting.isInteger() ? String.valueOf(setting.getIntValue()) : String.format("%.2f", setting.getValue());
                this.cursor = this.textInput.length();
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
                }
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
            cursor = Math.max(0, Math.min(cursor, textInput.length()));
            if (KeyHelper.isEnter(key)) {
                try {
                    setting.setValue(Double.parseDouble(textInput));
                } catch (Exception ignored) {}
                textEditing = false;
                return true;
            } else if (KeyHelper.isLeft(key)) {
                if (cursor > 0) cursor--;
                return true;
            } else if (KeyHelper.isRight(key)) {
                if (cursor < textInput.length()) cursor++;
                return true;
            } else if (KeyHelper.isHome(key)) {
                cursor = 0;
                return true;
            } else if (KeyHelper.isEnd(key)) {
                cursor = textInput.length();
                return true;
            } else if (KeyHelper.isBackspace(key)) {
                if (cursor > 0 && !textInput.isEmpty()) {
                    textInput = textInput.substring(0, cursor - 1) + textInput.substring(cursor);
                    cursor--;
                }
                return true;
            } else if (KeyHelper.isDelete(key)) {
                if (cursor < textInput.length()) {
                    textInput = textInput.substring(0, cursor) + textInput.substring(cursor + 1);
                }
                return true;
            } else if (KeyHelper.isEscape(key)) {
                textEditing = false;
                return true;
            } else if ((KeyHelper.to263Key(key) == InputConstants.KEY_V || KeyHelper.to263Key(key) == 86) && KeyHelper.isControlDown()) {
                String clip = mc.keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (char c : clip.toCharArray()) {
                        if (Character.isDigit(c) || c == '.' || c == '-') sb.append(c);
                    }
                    if (sb.length() > 0) {
                        textInput = textInput.substring(0, cursor) + sb + textInput.substring(cursor);
                        cursor += sb.length();
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr) {
        if (textEditing && (Character.isDigit(chr) || chr == '.' || chr == '-')) {
            cursor = Math.max(0, Math.min(cursor, textInput.length()));
            textInput = textInput.substring(0, cursor) + chr + textInput.substring(cursor);
            cursor++;
            return true;
        }
        return false;
    }

    @Override
    public void clearFocus() {
        textEditing = false;
        textInput = "";
    }
}