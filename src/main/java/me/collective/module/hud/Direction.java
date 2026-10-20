package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction.AxisDirection;

import java.awt.Color;

public class Direction extends HudModule {
    public Direction() {
        super("Direction", "Displays player facing direction.", 4, 320);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        net.minecraft.util.math.Direction dir = mc.player.getHorizontalFacing();
        String axis = dir.getDirection() == AxisDirection.POSITIVE ? "+" : "-";
        String dirText = dir.asString().toUpperCase() + " §7[§f" + axis + dir.getAxis().asString().toUpperCase() + "§7]";

        this.width = FontManager.INSTANCE.getStringWidth(dirText) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, dirText, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}
