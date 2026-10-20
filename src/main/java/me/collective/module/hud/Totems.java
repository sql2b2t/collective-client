package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.awt.Color;

public class Totems extends HudModule {
    private static final ItemStack TOTEM_STACK = new ItemStack(Items.TOTEM_OF_UNDYING);

    public Totems() {
        super("Totems", "Displays the number of totems in inventory.", 120, 4);
        this.width = 36;
        this.height = 18;
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 160));
        }

        context.drawItem(TOTEM_STACK, (int) x + 1, (int) y + 1);
        FontManager.INSTANCE.drawStringWithShadow(context, String.valueOf(count), x + 20, y + 5, Color.WHITE);
    }
}
