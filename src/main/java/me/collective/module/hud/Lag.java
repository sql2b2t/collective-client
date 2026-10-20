package me.collective.module.hud;

import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.font.FontManager;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class Lag extends HudModule {
    private static volatile long lastPacketTime = System.currentTimeMillis();

    private final NumberSetting threshold = add(new NumberSetting("Threshold", 2.0, 0.5, 10.0, 0.5));

    public static void onPacket() {
        lastPacketTime = System.currentTimeMillis();
    }

    public Lag() {
        super("Lag", "Shows server lag duration.", 350, 40);
        background.setValue(true);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastPacketTime = System.currentTimeMillis();
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        lastPacketTime = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled()) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.isInSingleplayer()) return;

        long diff = System.currentTimeMillis() - lastPacketTime;
        long thresholdMs = (long) (threshold.getValue() * 1000.0);

        if (diff < thresholdMs) {
            this.width = 0;
            this.height = 0;
            return;
        }

        FontManager fm = FontManager.INSTANCE;
        String message = "Server not responding";
        String seconds = String.format("%.1fs", diff / 1000.0f);
        float messageW = fm.getStringWidth(message);
        float secondsW = fm.getStringWidth(seconds);
        int fontH = fm.getFontHeight();
        float padX = 7f, padY = 4f;

        if (background.getValue()) {
            this.width = padX * 2 + messageW + 4f + secondsW;
            this.height = fontH + padY * 2;
            HudTheme.plate(context, x, y, width, height, 5.5f);
        } else {
            this.width = messageW + 4f + secondsW;
            this.height = fontH;
        }

        float tx = background.getValue() ? x + padX : x;
        float ty = background.getValue() ? y + padY : y;
        fm.drawStringWithShadow(context, message, tx, ty, HudTheme.text());
        fm.drawStringWithShadow(context, seconds, tx + messageW + 4f, ty, HudTheme.BAD);
    }
}
