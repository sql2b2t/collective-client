package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class TextRadar extends HudModule {
    public final NumberSetting range = add(new NumberSetting("Range", 50, 10, 150, 5));

    public TextRadar() {
        super("TextRadar", "Displays nearby players and their distance.", 4, 180);
        background.setValue(true);
    }

    /**
     * Готовая строка радара: строки, ширины и здоровье посчитаны на пересборке.
     * Список пересобирается не чаще, чем раз в 50 мс, а не на каждый кадр.
     */
    private static final class Row {
        final String name;
        final String brackets;
        final String hpText;
        final float nameW;
        final float bracketsW;
        final float hpW;
        final int hp;
        final int dist;

        Row(String name, String brackets, String hpText, float nameW, float bracketsW, float hpW, int hp, int dist) {
            this.name = name;
            this.brackets = brackets;
            this.hpText = hpText;
            this.nameW = nameW;
            this.bracketsW = bracketsW;
            this.hpW = hpW;
            this.hp = hp;
            this.dist = dist;
        }
    }

    private List<Row> cachedRows = List.of();
    private long lastRebuild;

    private List<Row> rows() {
        long now = System.currentTimeMillis();
        if (now - this.lastRebuild < 50) return this.cachedRows;
        this.lastRebuild = now;

        if (mc.player == null || mc.world == null) return this.cachedRows = List.of();

        double rangeSq = (double) this.range.getIntValue() * this.range.getIntValue();
        FontManager fm = FontManager.INSTANCE;
        List<Row> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            double dSq = mc.player.squaredDistanceTo(p);
            if (dSq > rangeSq) continue;

            int dist = (int) Math.sqrt(dSq);
            String name = p.getName().getString();
            String brackets = "[" + dist + "m]";
            int hp = (int) (p.getHealth() + p.getAbsorptionAmount());
            String hpText = hp + "❤";
            list.add(new Row(name, brackets, hpText,
                    fm.getStringWidth(name), fm.getStringWidth(brackets), fm.getStringWidth(hpText), hp, dist));
        }
        list.sort((r1, r2) -> Integer.compare(r1.dist, r2.dist));
        return this.cachedRows = list;
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        List<Row> players = rows();
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
        for (Row row : players) {
            float pillW = padX * 2 + row.nameW + 3f + row.bracketsW + 3f + row.hpW;
            maxWidth = Math.max(maxWidth, pillW);

            if (background.getValue()) {
                GuiGfx.fillRounded(context, x, curY, pillW, pillH, 4f, HudTheme.PLATE.getRGB());
                GuiGfx.strokeRounded(context, x, curY, pillW, pillH, 4f, 1f, HudTheme.BORDER.getRGB());
            }
            float tx = x + padX;
            fm.drawStringWithShadow(context, row.name, tx, curY + padY, HudTheme.TEXT);
            fm.drawStringWithShadow(context, row.brackets, tx + row.nameW + 3f, curY + padY, HudTheme.MUTED);
            fm.drawStringWithShadow(context, row.hpText, tx + row.nameW + 3f + row.bracketsW + 3f, curY + padY,
                    HudTheme.ratio(row.hp / 20f));
            curY += pillH + gap;
        }

        this.width = maxWidth;
        this.height = Math.max(fontH, curY - y - gap);
    }
}
