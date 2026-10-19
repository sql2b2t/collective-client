package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.setting.BooleanSetting;
import me.collective.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;

public class Watermark extends HudModule {
    public final StringSetting text = add(new StringSetting("Text", "Collective"));
    public final BooleanSetting version = add(new BooleanSetting("Version", true));

    public Watermark() {
        super("Watermark", "Displays the client watermark.", 4, 4);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        FontManager fm = FontManager.INSTANCE;
        String logo = text.getValue();
        String ver = version.getValue() ? "v1.1" : "";

        float logoW = fm.getStringWidth(logo);
        float verW = ver.isEmpty() ? 0f : fm.getStringWidth(ver);
        int fontH = fm.getFontHeight();
        float accentW = 2.5f;

        if (background.getValue()) {
            float padY = 4f;
            float textLeft = x + 1.5f + accentW + 4.5f;
            float textRight = textLeft + logoW + (verW > 0 ? 4f : 0f) + verW;
            this.width = textRight - x + 6f;
            this.height = fontH + padY * 2;

            HudTheme.plate(context, x, y, width, height, 5f);
            GuiGfx.fillRounded(context, x + 1.5f, y + 4f, accentW, height - 8f, accentW / 2f, HudTheme.accent().getRGB());

            float ty = y + padY;
            fm.drawStringWithShadow(context, logo, textLeft, ty, HudTheme.text());
            if (verW > 0) fm.drawStringWithShadow(context, ver, textLeft + logoW + 4f, ty, HudTheme.MUTED);
            return;
        }

        this.width = logoW + (verW > 0 ? 4f : 0f) + verW;
        this.height = fontH + 2f;
        fm.drawStringWithShadow(context, logo, x, y, HudTheme.text());
        if (verW > 0) fm.drawStringWithShadow(context, ver, x + logoW + 4f, y, HudTheme.MUTED);
    }
}
