package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.stream.Collectors;

public class TextRadar extends HudModule {
    public final NumberSetting range = add(new NumberSetting("Range", 50, 10, 150, 5));

    public TextRadar() {
        super("TextRadar", "Displays nearby players and their distance.", 4, 180);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null || mc.world == null) return;

        List<PlayerEntity> players = mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive())
                .filter(p -> mc.player.distanceTo(p) <= range.getIntValue())
                .sorted((p1, p2) -> Float.compare(mc.player.distanceTo(p1), mc.player.distanceTo(p2)))
                .collect(Collectors.toList());

        if (players.isEmpty()) {
            this.width = 0;
            this.height = 0;
            return;
        }

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        float padX = 6f, padY = 3.5f, gap = 2.5f;
        float pillH = fontH + padY * 2f;

        float curY = y;
        float maxWidth = 60;
        for (PlayerEntity p : players) {
            String name = p.getName().getString();
            int dist = (int) mc.player.distanceTo(p);
            int hp = (int) (p.getHealth() + p.getAbsorptionAmount());
            String brackets = "[" + dist + "m]";
            String hpText = hp + "❤";

            float nameW = fm.getStringWidth(name);
            float bracketsW = fm.getStringWidth(brackets);
            float hpW = fm.getStringWidth(hpText);
            float pillW = padX * 2 + nameW + 3f + bracketsW + 3f + hpW;
            maxWidth = Math.max(maxWidth, pillW);

            if (background.getValue()) {
                GuiGfx.fillRounded(context, x, curY, pillW, pillH, 4f, HudTheme.PLATE.getRGB());
                GuiGfx.strokeRounded(context, x, curY, pillW, pillH, 4f, 1f, HudTheme.BORDER.getRGB());
            }
            float tx = x + padX;
            fm.drawStringWithShadow(context, name, tx, curY + padY, HudTheme.TEXT);
            fm.drawStringWithShadow(context, brackets, tx + nameW + 3f, curY + padY, HudTheme.MUTED);
            fm.drawStringWithShadow(context, hpText, tx + nameW + 3f + bracketsW + 3f, curY + padY,
                    HudTheme.ratio(hp / 20f));
            curY += pillH + gap;
        }

        this.width = maxWidth;
        this.height = Math.max(fontH, curY - y - gap);
    }
}
