package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.gui.GuiGfx;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

public class Position extends HudModule {
    public final BooleanSetting nether = add(new BooleanSetting("Nether", true));
    public final BooleanSetting angles = add(new BooleanSetting("Angles", true));

    public Position() {
        super("Position", "Displays current coordinates.", 4, 300);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        FontManager fm = FontManager.INSTANCE;

        double posX = mc.player.getX();
        double posY = mc.player.getY();
        double posZ = mc.player.getZ();
        String coords = String.format("%.1f, %.1f, %.1f", posX, posY, posZ);

        StringBuilder sub = new StringBuilder();
        if (nether.getValue()) {
            boolean isNether = mc.world != null && mc.world.getRegistryKey().getValue().getPath().contains("the_nether");
            double nX = isNether ? posX * 8.0 : posX / 8.0;
            double nZ = isNether ? posZ * 8.0 : posZ / 8.0;
            sub.append(String.format("%.1f, %.1f", nX, nZ));
        }
        if (angles.getValue()) {
            if (sub.length() > 0) sub.append("  ·  ");
            float yaw = mc.player.getYaw() % 360;
            float pitch = mc.player.getPitch();
            sub.append(String.format("%.0f°, %.0f°", yaw, pitch));
        }

        int fontH = fm.getFontHeight();
        float coordsW = fm.getStringWidth(coords);
        String subStr = sub.toString();
        float subW = subStr.isEmpty() ? 0f : fm.getStringWidth(subStr);
        float padX = 7f, padY = 4f;

        if (background.getValue()) {
            float dotD = 4f, dotGap = 3.5f;
            boolean hasSub = !subStr.isEmpty();
            this.width = (hasSub ? Math.max(coordsW, subW) : coordsW) + padX * 2 + dotD + dotGap;
            this.height = fontH + padY * 2 + (hasSub ? fontH + 3f : 0f);

            HudTheme.plate(context, x, y, width, height, 5.5f);
            float textX = x + padX + dotD + dotGap;
            GuiGfx.dot(context, x + padX + 2f, y + padY + fontH / 2f - 0.5f, 2f, HudTheme.accent().getRGB());
            fm.drawStringWithShadow(context, coords, textX, y + padY, HudTheme.TEXT);
            if (hasSub) {
                fm.drawStringWithShadow(context, subStr, textX, y + padY + fontH + 3f, HudTheme.MUTED);
            }
            return;
        }

        this.width = Math.max(coordsW, subW);
        this.height = fontH * (subStr.isEmpty() ? 1 : 2) + 3f;
        fm.drawStringWithShadow(context, coords, x, y, HudTheme.TEXT);
        if (!subStr.isEmpty()) fm.drawStringWithShadow(context, subStr, x, y + fontH + 3f, HudTheme.MUTED);
    }
}
