package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.client.gui.DrawContext;

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
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        double dX = mc.player.getX() - mc.player.lastX;
        double dY = mc.player.getY() - mc.player.lastY;
        double dZ = mc.player.getZ() - mc.player.lastZ;

        double distance = both3D.getValue() ? Math.sqrt(dX * dX + dY * dY + dZ * dZ) : Math.hypot(dX, dZ);
        double speed = distance * 20.0;

        String unitLabel = " b/s";
        if (unit.getValue() == Unit.KMH) {
            speed *= 3.6;
            unitLabel = " km/h";
        }

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        String value = String.format(Locale.ROOT, "%.1f", speed);
        float valW = fm.getStringWidth(value);
        float labW = fm.getStringWidth(unitLabel);
        float titleW = showTitle.getValue() ? fm.getStringWidth("Speed") + 5f : 0f;
        float padX = 7f, padY = 4f;

        if (background.getValue()) {
            this.width = padX * 2 + titleW + valW + labW;
            this.height = fontH + padY * 2;
            HudTheme.plate(context, x, y, width, height, 5.5f);
        } else {
            this.width = titleW + valW + labW;
            this.height = fontH;
        }

        float tx = background.getValue() ? x + padX : x;
        float ty = background.getValue() ? y + padY : y;
        if (titleW > 0) {
            fm.drawStringWithShadow(context, "Speed", tx, ty, HudTheme.MUTED);
            tx += titleW;
        }
        fm.drawStringWithShadow(context, value, tx, ty, HudTheme.text());
        fm.drawStringWithShadow(context, unitLabel, tx + valW, ty, HudTheme.MUTED);
    }
}
