package me.collective.gui.component;

import me.collective.font.FontManager;
import me.collective.gui.BlockPickerScreen;
import me.collective.gui.ClickGuiScreen;
import me.collective.module.client.UI;
import me.collective.render.RenderUtil;
import me.collective.setting.BlockListSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

import java.awt.Color;

/**
 * Настройка BlockListSetting в панели модуля — компактная строка, по клику
 * открывает полноэкранный пикер блоков ({@link BlockPickerScreen}) в стиле
 * Meteor: поиск и две колонки — все блоки / выбранные.
 */
public class BlockListComponent extends Component {
    private static final float HEADER_H = 11f;

    private final BlockListSetting setting;
    private String lastName = "";

    public BlockListComponent(BlockListSetting setting, float x, float y, float width, float height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (lastName.isEmpty()) lastName = setting.getName();
        this.height = HEADER_H;

        float fs = FontManager.settingsScale();
        int fh = FontManager.INSTANCE.getFontHeightScaled(fs);
        Color bg = UI.INSTANCE.bgColor.getValue();
        Color text = UI.INSTANCE.textColor.getValue();
        Color accent = UI.INSTANCE.color.getValue();

        boolean hover = isHovered(mouseX, mouseY);
        RenderUtil.drawRect(context, x, y, x + width, y + HEADER_H, bg);

        String label = lastName + " [" + setting.getValue().size() + "]";
        FontManager.INSTANCE.drawStringWithShadowScaled(context, label, x + 4,
                y + (HEADER_H - fh) / 2f + 0.5f, hover ? accent : text, fs);

        String gear = ">";
        float gw = FontManager.INSTANCE.getStringWidthScaled(gear, fs);
        FontManager.INSTANCE.drawStringWithShadowScaled(context, gear, x + width - 4 - gw,
                y + (HEADER_H - fh) / 2f + 0.5f,
                hover ? accent : new Color(150, 150, 156), fs);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (!isHovered(mouseX, mouseY)) return false;

        if (UI.INSTANCE.sounds.getValue()) {
            me.collective.render.SoundUtil.playClick(1.0f, 1.2f);
        }
        MinecraftClient.getInstance().setScreen(new BlockPickerScreen(setting, ClickGuiScreen.getInstance()));
        return true;
    }
}
