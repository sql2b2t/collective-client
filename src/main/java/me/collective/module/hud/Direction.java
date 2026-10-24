package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.module.client.HUD;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Direction.AxisDirection;

import java.awt.Color;

public class Direction extends HudModule {
    public Direction() {
        super("Direction", "Показывает сторону света и ось направления взгляда игрока.", 4, 320);
    }

    @Override
    public void render(GuiGraphicsExtractor context, float delta) {
        if (!isToggled() || mc.player == null) return;

        net.minecraft.core.Direction dir = mc.player.getDirection();
        String axis = dir.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? "+" : "-";
        String dirText = dir.getName().toUpperCase() + " §7[§f" + axis + dir.getAxis().getName().toUpperCase() + "§7]";

        this.width = FontManager.INSTANCE.getStringWidth(dirText) + 6;
        this.height = FontManager.INSTANCE.getFontHeight() + 4;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        FontManager.INSTANCE.drawStringWithShadow(context, dirText, x + 3, y + 2, HUD.INSTANCE.getHudColor(0));
    }
}