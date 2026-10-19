package me.collective.module.hud;

import me.collective.gui.GuiGfx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.awt.Color;

public class Inventory extends HudModule {
    public Inventory() {
        super("Inventory", "Draws an inventory preview.", 250, 250);
        background.setValue(true);
        this.width = 9 * 18 + 4;
        this.height = 3 * 18 + 4;
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        int startSlot = 9;
        boolean plate = background.getValue();

        if (plate) {
            GuiGfx.fillRounded(context, x, y, width, height, 7f, HudTheme.PLATE.getRGB());
            GuiGfx.strokeRounded(context, x, y, width, height, 7f, 1f, HudTheme.BORDER.getRGB());
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = startSlot + row * 9 + col;
                ItemStack stack = mc.player.getInventory().getStack(slot);
                int itemX = (int) (x + 2 + col * 18);
                int itemY = (int) (y + 2 + row * 18);

                if (plate) {
                    GuiGfx.fillRounded(context, itemX, itemY, 16, 16, 3f, new Color(255, 255, 255, 14).getRGB());
                }

                if (!stack.isEmpty()) {
                    context.drawItem(stack, itemX, itemY);
                    context.drawStackOverlay(mc.textRenderer, stack, itemX, itemY);
                }
            }
        }
    }
}
