package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * Modern ModeComponent matching ClickGUI 2 styling.
 * Right-click opens the dropdown list of all available modes.
 * Left-click cycles through modes.
 */
public class ModeComponent extends Component {
    private final ModeSetting setting;
    private boolean open;

    public ModeComponent(ModeSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
    }

    @Override
    public float getHeight() {
        if (open) {
            return 16f + 3f + setting.getModes().size() * 14f + 3f;
        }
        return 16f;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, 16f, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 3.5f, ClickGuiTheme.TEXT_PRIMARY);

        // Modern Mode Pill on the right: < Mode >
        String modeVal = "< " + setting.getValue() + " >";
        float mW = ClickGuiTheme.badge().getStringWidth(modeVal) + 6f;
        float mX = x + width - mW - 6f;
        float mY = y + 2f;
        boolean pillHov = mouseX >= mX && mouseX <= mX + mW && mouseY >= mY && mouseY <= mY + 12f;

        int pillBg = open ? ClickGuiTheme.getAccentBg() : (pillHov ? 0x28FFFFFF : 0x16FFFFFF);
        int pillStroke = open ? ClickGuiTheme.getAccentColor() : 0x20FFFFFF;
        SmoothGfx.fillAndStroke(context, mX, mY, mW, 12f, 3f, pillBg, pillStroke, 1.0f);
        ClickGuiTheme.badge().drawString(context, modeVal, mX + 3f, mY + 2f, ClickGuiTheme.getAccentColor());

        // Expanded Dropdown List of Modes when open
        if (open) {
            List<String> modes = setting.getModes();
            float listY = y + 16f + 2f;
            float listH = modes.size() * 14f + 2f;
            float listX = x + 3f;
            float listW = width - 6f;

            // Sleek card container
            SmoothGfx.fillAndStroke(context, listX, listY, listW, listH, 3.5f, 0xE00C121C, 0x28FFFFFF, 1.0f);

            for (int i = 0; i < modes.size(); i++) {
                String mName = modes.get(i);
                float itemY = listY + 2f + i * 14f;
                float itemH = 13f;
                float itemX = listX + 2f;
                float itemW = listW - 4f;
                boolean itemHov = mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= itemY && mouseY <= itemY + itemH;
                boolean selected = mName.trim().equalsIgnoreCase(setting.getValue().trim());

                if (selected) {
                    SmoothGfx.fillRounded(context, itemX, itemY, itemW, itemH, 2.5f, ClickGuiTheme.getAccentBg());
                    SmoothGfx.fillRounded(context, itemX + 2f, itemY + 2f, 2f, itemH - 4f, 1f, ClickGuiTheme.getAccentColor());
                    ClickGuiTheme.badge().drawString(context, mName, itemX + 7f, itemY + 2.5f, ClickGuiTheme.getAccentColor());
                } else {
                    if (itemHov) {
                        SmoothGfx.fillRounded(context, itemX, itemY, itemW, itemH, 2.5f, 0x1AFFFFFF);
                    }
                    ClickGuiTheme.badge().drawString(context, mName, itemX + 7f, itemY + 2.5f, itemHov ? ClickGuiTheme.TEXT_PRIMARY : ClickGuiTheme.TEXT_MUTED);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;

        // Click on header (y to y + 16)
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16f) {
            if (button == 1) { // Right-click opens/closes dropdown!
                open = !open;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, open ? 1.2f : 0.9f);
                }
                return true;
            } else if (button == 0) { // Left-click cycles through modes
                String modeVal = "< " + setting.getValue() + " >";
                float mW = ClickGuiTheme.badge().getStringWidth(modeVal) + 6f;
                float mX = x + width - mW - 6f;
                if (mouseX < mX + mW / 2f) {
                    setting.cycleReverse();
                } else {
                    setting.cycle();
                }
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, 1.0f);
                }
                return true;
            }
        }

        // Click inside dropdown list
        if (open) {
            List<String> modes = setting.getModes();
            float listY = y + 16f + 2f;
            float listX = x + 3f;
            float listW = width - 6f;

            for (int i = 0; i < modes.size(); i++) {
                float itemY = listY + 2f + i * 14f;
                float itemH = 13f;
                float itemX = listX + 2f;
                float itemW = listW - 4f;

                if (mouseX >= itemX && mouseX <= itemX + itemW && mouseY >= itemY && mouseY <= itemY + itemH) {
                    setting.setValue(modes.get(i));
                    if (UI.INSTANCE.sounds.getValue()) {
                        SoundUtil.playClick(1.0f, 1.0f);
                    }
                    return true;
                }
            }
        }

        return false;
    }
}