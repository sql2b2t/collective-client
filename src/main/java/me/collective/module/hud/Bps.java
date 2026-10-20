package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.Locale;

public class Bps extends HudModule {
    public enum Unit {
        BPS,
        KMH
    }

    public final EnumSetting<Unit> unit = add(new EnumSetting<>("Unit", Unit.BPS));
    public final BooleanSetting both3D = add(new BooleanSetting("3D", false));
    public final BooleanSetting showTitle = add(new BooleanSetting("Title", true));

    public Bps() {
        super("BPS", "Displays your current movement speed in blocks per second.", 4, 360);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        double dX = mc.player.getX() - mc.player.lastX;
        double dY = mc.player.getY() - mc.player.lastY;
        double dZ = mc.player.getZ() - mc.player.lastZ;

        double distance = both3D.getValue() ? Math.sqrt(dX * dX + dY * dY + dZ * dZ) : Math.hypot(dX, dZ);
        double speed = distance * 20.0;

        String unitLabel = "b/s";
        if (unit.getValue() == Unit.KMH) {
            speed *= 3.6;
            unitLabel = "km/h";
        }

        String text;
        if (showTitle.getValue()) {
            text = String.format(Locale.ROOT, "Speed: §f%.1f §7%s", speed, unitLabel);
        } else {
            text = String.format(Locale.ROOT, "§f%.1f §7%s", speed, unitLabel);
        }

        this.width = FontManager.INSTANCE.getStringWidth(text) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, text, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}
