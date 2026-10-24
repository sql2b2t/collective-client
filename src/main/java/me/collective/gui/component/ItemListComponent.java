package me.collective.gui.component;

import me.collective.gui.ClickGuiScreen;
import me.collective.gui.alt.SmoothGfx;
import me.collective.gui.ClickGuiTheme;
import me.collective.gui.settings.ItemListSettingScreen;
import me.collective.module.client.UI;
import me.collective.render.SoundUtil;
import me.collective.setting.ItemListSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Modern ItemListComponent matching ClickGUI 2 pill button design.
 */
public class ItemListComponent extends Component {
    private final ItemListSetting setting;

    public ItemListComponent(ItemListSetting setting, float x, float y, float width, float height) {
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

        // Pill Button on the right
        int count = setting.getValue() != null ? setting.getValue().size() : 0;
        String btnText = "Items (" + count + ")";
        float bW = ClickGuiTheme.badge().getStringWidth(btnText) + 8f;
        float bX = x + width - bW - 6f;
        float bY = y + 2f;
        boolean btnHov = mouseX >= bX && mouseX <= bX + bW && mouseY >= bY && mouseY <= bY + 12f;

        SmoothGfx.fillAndStroke(context, bX, bY, bW, 12f, 3f, btnHov ? 0x28FFFFFF : 0x16FFFFFF, 0x20FFFFFF, 1.0f);
        ClickGuiTheme.badge().drawString(context, btnText, bX + 4f, bY + 2f, ClickGuiTheme.getAccentColor());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!setting.isVisible()) return false;
        if (button == 0 && isHovered(mouseX, mouseY)) {
            if (UI.INSTANCE.sounds.getValue()) {
                SoundUtil.playClick(1.0f, 1.0f);
            }
            MinecraftClient.getInstance().setScreen(new ItemListSettingScreen(ClickGuiScreen.getInstance(), setting));
            return true;
        }
        return false;
    }
}
