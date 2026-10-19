package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;

import java.time.LocalTime;

public class Welcomer extends HudModule {
    public final StringSetting text = add(new StringSetting("CustomText", ""));

    public Welcomer() {
        super("Welcomer", "Displays a welcoming message.", 300, 4);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        FontManager fm = FontManager.INSTANCE;
        String name = mc.player.getName().getString();

        String greeting;
        String suffix = "";
        if (!text.getValue().isEmpty()) {
            greeting = text.getValue().replace("%player%", name);
        } else {
            int hour = LocalTime.now().getHour();
            String timeGreeting = (hour >= 6 && hour < 12) ? "Good morning" :
                    (hour >= 12 && hour < 18) ? "Good afternoon" :
                            (hour >= 18 && hour < 23) ? "Good evening" : "Good night";
            greeting = timeGreeting + ", " + name;
            suffix = "  ·  Collective";
        }

        float greetingW = fm.getStringWidth(greeting);
        float suffixW = suffix.isEmpty() ? 0f : fm.getStringWidth(suffix);
        int fontH = fm.getFontHeight();
        float padX = 7f, padY = 4f;

        if (background.getValue()) {
            this.width = padX * 2 + greetingW + suffixW;
            this.height = fontH + padY * 2;
            HudTheme.plate(context, x, y, width, height, 5.5f);
        } else {
            this.width = greetingW + suffixW;
            this.height = fontH;
        }

        float tx = background.getValue() ? x + padX : x;
        float ty = background.getValue() ? y + padY : y;
        fm.drawStringWithShadow(context, greeting, tx, ty, HudTheme.text());
        if (suffixW > 0) fm.drawStringWithShadow(context, suffix, tx + greetingW, ty, HudTheme.MUTED);
    }
}
