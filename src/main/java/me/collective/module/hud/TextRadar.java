package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class TextRadar extends HudModule {
    public final NumberSetting range = add(new NumberSetting("Range", 50, 10, 150, 5));

    public TextRadar() {
        super("TextRadar", "Displays nearby players and their distance.", 4, 180);
    }

    /** Строка радара: текст, ширина и здоровье посчитаны на пересборке (раз в 50 мс). */
    private static final class Row {
        final String info;
        final float w;
        final int dist;

        Row(String info, float w, int dist) {
            this.info = info;
            this.w = w;
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
        List<Row> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive()) continue;
            double dSq = mc.player.squaredDistanceTo(p);
            if (dSq > rangeSq) continue;

            int dist = (int) Math.sqrt(dSq);
            int hp = (int) (p.getHealth() + p.getAbsorptionAmount());
            String info = String.format("%s §7[%dm] §c%d❤", p.getName().getString(), dist, hp);
            list.add(new Row(info, FontManager.INSTANCE.getStringWidth(info) + 6, dist));
        }
        list.sort((r1, r2) -> Integer.compare(r1.dist, r2.dist));
        return this.cachedRows = list;
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null || mc.world == null) return;

        List<Row> players = rows();

        if (players.isEmpty()) {
            this.width = 0;
            this.height = 0;
            return;
        }

        float curY = y;
        float maxWidth = 60;
        int fontH = FontManager.INSTANCE.getFontHeight();

        for (Row row : players) {
            float w = row.w;
            maxWidth = Math.max(maxWidth, w);

            if (background.getValue()) {
                RenderUtil.drawRect(context, x, curY, x + w, curY + fontH + 2, new Color(20, 20, 20, 160));
            }

            FontManager.INSTANCE.drawStringWithShadow(context, row.info, x + 3, curY + 1, Color.WHITE);
            curY += fontH + 3;
        }

        this.width = maxWidth;
        this.height = Math.max(fontH, curY - y);
    }
}
