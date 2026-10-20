package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.awt.Color;
import java.util.Collection;

public class Effects extends HudModule {
    public Effects() {
        super("Effects", "Displays active potion effects.", 4, 150);
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

        float curY = y;
        float maxWidth = 50;
        int fontH = FontManager.INSTANCE.getFontHeight();

        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            int amp = effect.getAmplifier() + 1;
            int seconds = effect.getDuration() / 20;
            int mins = seconds / 60;
            seconds %= 60;

            String text = String.format("%s %d §7[%02d:%02d]", name, amp, mins, seconds);
            float w = FontManager.INSTANCE.getStringWidth(text) + 6;
            maxWidth = Math.max(maxWidth, w);

            if (background.getValue()) {
                RenderUtil.drawRect(context, x, curY, x + w, curY + fontH + 2, new Color(20, 20, 20, 160));
            }

            FontManager.INSTANCE.drawStringWithShadow(context, text, x + 3, curY + 1, new Color(230, 230, 230));
            curY += fontH + 3;
        }

        this.width = maxWidth;
        this.height = Math.max(fontH, curY - y);
    }
}
