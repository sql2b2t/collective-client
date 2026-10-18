package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Metrics extends HudModule {
    public final BooleanSetting fps = add(new BooleanSetting("FPS", true));
    public final BooleanSetting ping = add(new BooleanSetting("Ping", true));
    public final BooleanSetting server = add(new BooleanSetting("Server", true));

    public Metrics() {
        super("Metrics", "Displays FPS, Ping, and Server.", 4, 340);
        background.setValue(true);
    }

    private static final class Seg {
        final String text;
        final Color color;

        Seg(String text, Color color) {
            this.text = text;
            this.color = color;
        }
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        FontManager fm = FontManager.INSTANCE;
        List<Seg> segs = new ArrayList<>();

        if (fps.getValue()) {
            segs.add(new Seg(String.valueOf(mc.getCurrentFps()), HudTheme.TEXT));
            segs.add(new Seg(" fps", HudTheme.MUTED));
        }

        if (ping.getValue() && mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            int latency = entry != null ? entry.getLatency() : 0;
            if (fps.getValue()) segs.add(new Seg("  ·  ", HudTheme.MUTED));
            segs.add(new Seg(String.valueOf(latency), HudTheme.TEXT));
            segs.add(new Seg(" ms", HudTheme.MUTED));
        }

        if (server.getValue()) {
            String ip = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
            if (!segs.isEmpty()) segs.add(new Seg("  ·  ", HudTheme.MUTED));
            segs.add(new Seg(ip, ip.equals("Singleplayer") ? HudTheme.MUTED : HudTheme.TEXT));
        }

        if (segs.isEmpty()) {
            this.width = 40f;
            this.height = fm.getFontHeight();
            return;
        }

        float textW = 0f;
        for (Seg s : segs) textW += fm.getStringWidth(s.text);
        int fontH = fm.getFontHeight();
        float padX = 7f, padY = 4f;

        if (background.getValue()) {
            this.width = textW + padX * 2;
            this.height = fontH + padY * 2;
            HudTheme.plate(context, x, y, width, height, 5.5f);
            drawSegs(context, segs, x + padX, y + padY);
            return;
        }

        this.width = textW;
        this.height = fontH;
        drawSegs(context, segs, x, y);
    }

    private void drawSegs(DrawContext context, List<Seg> segs, float tx, float ty) {
        for (Seg s : segs) {
            FontManager.INSTANCE.drawStringWithShadow(context, s.text, tx, ty, s.color);
            tx += FontManager.INSTANCE.getStringWidth(s.text);
        }
    }
}
