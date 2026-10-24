package me.collective.module.hud;

import me.collective.event.EventHandler;
import me.collective.event.events.PacketEvent;
import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import me.collective.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;

public class Lag extends HudModule {
    private static volatile long lastPacketTime = System.currentTimeMillis();

    private final NumberSetting threshold = add(new NumberSetting("Threshold", 2.0, 0.5, 10.0, 0.5));

    public static void onPacket() {
        lastPacketTime = System.currentTimeMillis();
    }

    public Lag() {
        super("Lag", "Показывает время задержки ответа сервера (лаг тиков).", 350, 40);
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
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled()) return;
        if (mc.player == null || mc.level == null) return;
        if (mc.hasSingleplayerServer()) return;

        long diff = System.currentTimeMillis() - lastPacketTime;
        long thresholdMs = (long) (threshold.getValue() * 1000.0);

        boolean inEditor = mc.gui.screen() instanceof me.collective.gui.hud.HudEditorScreen;
        if (diff < thresholdMs && !inEditor) {
            this.width = 0;
            this.height = 0;
            return;
        }

        String lagText = (inEditor && diff < thresholdMs)
            ? "Server not responding: §c2.5s"
            : String.format("Server not responding: §c%.1fs", diff / 1000.0f);
        this.width = FontManager.INSTANCE.getStringWidth(lagText) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 180));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, lagText, x + 3, y + 2, Color.RED);
    }
}