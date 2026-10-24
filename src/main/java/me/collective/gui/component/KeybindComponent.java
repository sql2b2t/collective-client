package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.Keybind;
import me.collective.setting.KeybindSetting;
import me.collective.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * Modern KeybindComponent matching ClickGUI 2 pill badge styling.
 */
public class KeybindComponent extends Component {
    private final Setting<?> setting;
    private boolean binding;

    public KeybindComponent(Setting<?> setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        return 16f;
    }

    private String getKeyName() {
        if (setting instanceof KeybindSetting ks) {
            return ks.getKeyName();
        }
        if (setting.getValue() instanceof Keybind kb) {
            return formatKey(kb.getKey(), kb.isButton());
        }
        return "None";
    }

    private static String formatKey(int key, boolean isButton) {
        return KeybindSetting.formatKey(key, isButton);
    }

    @SuppressWarnings("unchecked")
    private void setKey(int key, boolean isButton) {
        if (setting instanceof KeybindSetting ks) {
            ks.setValue(isButton ? -100 - key : key);
        } else if (setting.getValue() instanceof Keybind) {
            ((Setting<Keybind>) setting).setValue(new Keybind(key, isButton));
        }
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

        // Modern Pill on the right
        String keyName = getKeyName().toUpperCase();
        String bindText = binding ? "..." : (!keyName.equalsIgnoreCase("NONE") && !keyName.equalsIgnoreCase("UNKNOWN") ? keyName : "Bind");
        float bW = ClickGuiTheme.badge().getStringWidth(bindText) + 8f;
        float bX = x + width - bW - 6f;
        float bY = y + 2f;
        boolean pillHov = mouseX >= bX && mouseX <= bX + bW && mouseY >= bY && mouseY <= bY + 12f;

        int bg = binding ? ClickGuiTheme.getAccentBg() : (pillHov ? 0x28FFFFFF : 0x16FFFFFF);
        int stroke = binding ? ClickGuiTheme.getAccentColor() : 0x20FFFFFF;
        SmoothGfx.fillAndStroke(context, bX, bY, bW, 12f, 3f, bg, stroke, 1.0f);
        ClickGuiTheme.badge().drawString(context, bindText, bX + 4f, bY + 2f, binding ? ClickGuiTheme.getAccentColor() : ClickGuiTheme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (isHovered(mouseX, mouseY)) {
            if (!binding) {
                if (button == 0) {
                    this.binding = true;
                    if (UI.INSTANCE.sounds.getValue()) {
                        SoundUtil.playClick(1.0f, 1.0f);
                    }
                    return true;
                } else if (button == 1) { // Right click clears bind
                    setKey(-1, false);
                    this.binding = false;
                    if (UI.INSTANCE.sounds.getValue()) {
                        SoundUtil.playClick(1.0f, 0.8f);
                    }
                    return true;
                }
            } else {
                setKey(button, true);
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
            int k263 = me.collective.util.KeyHelper.to263Key(key);
            if (k263 == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE
                    || k263 == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE
                    || k263 == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
                setKey(-1, false);
            } else {
                setKey(k263, false);
            }
            this.binding = false;
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 1.2f);
            }
            return true;
        }
        return false;
    }
}