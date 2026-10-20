package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.Color;

public class Metrics extends HudModule {
    public final BooleanSetting fps = add(new BooleanSetting("FPS", true));
    public final BooleanSetting ping = add(new BooleanSetting("Ping", true));
    public final BooleanSetting server = add(new BooleanSetting("Server", true));

    public Metrics() {
        super("Metrics", "Displays FPS, Ping, and Server.", 4, 340);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;

        StringBuilder sb = new StringBuilder();
        if (fps.getValue()) {
            sb.append("FPS: §f").append(mc.getCurrentFps()).append(" ");
        }

        if (ping.getValue() && mc.player != null && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            int latency = entry != null ? entry.getLatency() : 0;
            sb.append("Ping: §f").append(latency).append("ms ");
        }

        if (server.getValue()) {
            String ip = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer";
            sb.append("Server: §f").append(ip);
        }

        String text = sb.toString().trim();
        this.width = FontManager.INSTANCE.getStringWidth(text) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, text, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}
