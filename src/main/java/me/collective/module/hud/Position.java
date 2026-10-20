package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class Position extends HudModule {
    public final BooleanSetting nether = add(new BooleanSetting("Nether", true));
    public final BooleanSetting angles = add(new BooleanSetting("Angles", true));

    public Position() {
        super("Position", "Displays current coordinates.", 4, 300);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        double posX = mc.player.getX();
        double posY = mc.player.getY();
        double posZ = mc.player.getZ();

        String coords = String.format("XYZ: §f%.1f, %.1f, %.1f", posX, posY, posZ);

        if (nether.getValue()) {
            boolean isNether = mc.world != null && mc.world.getRegistryKey().getValue().getPath().contains("the_nether");
            double nX = isNether ? posX * 8.0 : posX / 8.0;
            double nZ = isNether ? posZ * 8.0 : posZ / 8.0;
            coords += String.format(" §7[§f%.1f, %.1f§7]", nX, nZ);
        }

        if (angles.getValue()) {
            float yaw = mc.player.getYaw() % 360;
            float pitch = mc.player.getPitch();
            coords += String.format(" §7(§f%.1f§7, §f%.1f§7)", yaw, pitch);
        }

        this.width = FontManager.INSTANCE.getStringWidth(coords) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, coords, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}
