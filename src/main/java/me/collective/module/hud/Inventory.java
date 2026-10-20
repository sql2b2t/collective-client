package me.collective.module.hud;

import me.collective.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.awt.Color;

public class Inventory extends HudModule {
    public Inventory() {
        super("Inventory", "Draws an inventory preview.", 250, 250);
        this.width = 9 * 18 + 4;
        this.height = 3 * 18 + 4;
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 180));
        RenderUtil.drawOutline(context, x, y, width, height, 1, new Color(110, 80, 230, 200).getRGB());

        int startSlot = 9;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = startSlot + row * 9 + col;
                ItemStack stack = mc.player.getInventory().getStack(slot);
                int itemX = (int) (x + 2 + col * 18);
                int itemY = (int) (y + 2 + row * 18);

                RenderUtil.drawRect(context, itemX, itemY, itemX + 16, itemY + 16, new Color(40, 40, 40, 100));
                if (!stack.isEmpty()) {
                    context.drawItem(stack, itemX, itemY);
                    context.drawStackOverlay(mc.textRenderer, stack, itemX, itemY);
                }
            }
        }
    }
}
