package me.collective.module.hud;

import me.collective.font.FontManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction.AxisDirection;

public class Direction extends HudModule {
    public Direction() {
        super("Direction", "Displays player facing direction.", 4, 320);
        background.setValue(true);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        net.minecraft.util.math.Direction dir = mc.player.getHorizontalFacing();
        String axis = dir.getDirection() == AxisDirection.POSITIVE ? "+" : "-";
        String facing = dir.asString().toUpperCase();
        String brackets = "[" + axis + dir.getAxis().asString().toUpperCase() + "]";

        FontManager fm = FontManager.INSTANCE;
        int fontH = fm.getFontHeight();
        float facingW = fm.getStringWidth(facing);
        float bracketsW = fm.getStringWidth(brackets);
        float padX = 7f, padY = 4f;

        if (background.getValue()) {
            this.width = padX * 2 + facingW + 3f + bracketsW;
            this.height = fontH + padY * 2;
            HudTheme.plate(context, x, y, width, height, 5.5f);
            float tx = x + padX;
            fm.drawStringWithShadow(context, facing, tx, y + padY, HudTheme.text());
            fm.drawStringWithShadow(context, brackets, tx + facingW + 3f, y + padY, HudTheme.MUTED);
            return;
        }

        this.width = facingW + 3f + bracketsW;
        this.height = fontH;
        fm.drawStringWithShadow(context, facing, x, y, HudTheme.text());
        fm.drawStringWithShadow(context, brackets, x + facingW + 3f, y, HudTheme.MUTED);
    }
}
