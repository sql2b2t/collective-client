package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.Collection;

public class Effects extends HudModule {
    public Effects() {
        super("Effects", "Displays active potion effects.", 4, 150);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        if (effects.isEmpty()) {
            this.width = 0;
            this.height = 0;
            return;
        }

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        float padX = 6f, padY = 3.5f, gap = 2.5f;
        float pillH = fontH + padY * 2f;

        float curY = y;
        float maxWidth = 50;
        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            int amp = effect.getAmplifier() + 1;
            int seconds = effect.getDuration() / 20;
            int mins = seconds / 60;
            seconds %= 60;
            if (amp > 1) name += " " + amp;
            String time = String.format("[%02d:%02d]", mins, seconds);

            float nameW = fm.getStringWidth(name);
            float timeW = fm.getStringWidth(time);
            float pillW = padX * 2 + nameW + 4f + timeW;
            maxWidth = Math.max(maxWidth, pillW);

            if (background.getValue()) {
                GuiGfx.fillRounded(context, x, curY, pillW, pillH, 4f, HudTheme.PLATE.getRGB());
                GuiGfx.strokeRounded(context, x, curY, pillW, pillH, 4f, 1f, HudTheme.BORDER.getRGB());
            }
            float tx = x + padX;
            fm.drawStringWithShadow(context, name, tx, curY + padY, HudTheme.TEXT);
            fm.drawStringWithShadow(context, time, tx + nameW + 4f, curY + padY, HudTheme.MUTED);
            curY += pillH + gap;
        }

        this.width = maxWidth;
        this.height = Math.max(fontH, curY - y - gap);
    }
}
