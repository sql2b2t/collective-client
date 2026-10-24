package me.collective.gui.component;

import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.ChoiceSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern ChoiceComponent matching ClickGUI 2 styling.
 * Click header to expand/collapse group of modern boolean switches.
 */
public class ChoiceComponent extends Component {
    private final ChoiceSetting setting;
    private final List<BooleanComponent> options = new ArrayList<>();
    private boolean open;

    public ChoiceComponent(ChoiceSetting setting, float x, float y, float width, float height) {
        super(x, y, width, 16f);
        this.setting = setting;
        for (BooleanSetting opt : setting.getOptions()) {
            options.add(new BooleanComponent(opt, x, y, width, 16f));
        }
    }

    @Override
    public float getHeight() {
        if (open) {
            return 16f + (options.size() * 16f);
        }
        return 16f;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!setting.isVisible()) return;

        boolean hov = isHovered(mouseX, mouseY);
        if (hov) {
            SmoothGfx.fillRounded(context, x + 2f, y, width - 4f, 16f, 3f, 0x12FFFFFF);
        }

        // Setting Name on the left
        ClickGuiTheme.badge().drawString(context, setting.getName(), x + 6f, y + 3.5f, ClickGuiTheme.TEXT_PRIMARY);

        // Group status badge on the right [enabled/total] >
        long enabled = setting.getOptions().stream().filter(BooleanSetting::getValue).count();
        String badgeText = "[" + enabled + "/" + setting.getOptions().size() + "] " + (open ? "v" : ">");
        float bW = ClickGuiTheme.badge().getStringWidth(badgeText) + 8f;
        float bX = x + width - bW - 6f;
        float bY = y + 2f;
        boolean badgeHov = mouseX >= bX && mouseX <= bX + bW && mouseY >= bY && mouseY <= bY + 12f;

        SmoothGfx.fillAndStroke(context, bX, bY, bW, 12f, 3f, badgeHov ? 0x28FFFFFF : 0x16FFFFFF, 0x20FFFFFF, 1.0f);
        ClickGuiTheme.badge().drawString(context, badgeText, bX + 4f, bY + 2f, open ? ClickGuiTheme.getAccentColor() : ClickGuiTheme.TEXT_MUTED);

        if (open) {
            float curY = y + 16f;
            for (BooleanComponent c : options) {
                c.setX(x + 4f);
                c.setY(curY);
                c.setWidth(width - 4f);
                c.render(context, mouseX, mouseY, delta);
                curY += c.getHeight();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;

        // Header click (y to y + 16)
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 16f) {
            if (button == 0 || button == 1) {
                open = !open;
                if (UI.INSTANCE.sounds.getValue()) {
                    SoundUtil.playClick(1.0f, open ? 1.2f : 0.9f);
                }
                return true;
            }
        }

        if (open) {
            for (BooleanComponent c : options) {
                if (c.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (open) {
            for (BooleanComponent c : options) {
                c.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean keyPressed(int key) {
        if (open) {
            for (BooleanComponent c : options) {
                if (c.keyPressed(key)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (open) {
            for (BooleanComponent c : options) {
                if (c.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
            }
        }
        return false;
    }
}