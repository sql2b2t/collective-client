package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.time.LocalTime;

public class Welcomer extends HudModule {
    public final StringSetting text = add(new StringSetting("CustomText", ""));

    public Welcomer() {
        super("Welcomer", "Displays a welcoming message.", 300, 4);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        String name = mc.player.getName().getString();
        String welcome;

        if (!text.getValue().isEmpty()) {
            welcome = text.getValue().replace("%player%", name);
        } else {
            int hour = LocalTime.now().getHour();
            String timeGreeting = (hour >= 6 && hour < 12) ? "Good morning" :
                    (hour >= 12 && hour < 18) ? "Good afternoon" :
                            (hour >= 18 && hour < 23) ? "Good evening" : "Good night";
            welcome = String.format("%s, %s §7(Collective)", timeGreeting, name);
        }

        this.width = FontManager.INSTANCE.getStringWidth(welcome) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, welcome, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}
