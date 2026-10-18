package me.collective.module.hud;

import me.collective.gui.GuiGfx;
import me.collective.font.FontManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class Totems extends HudModule {
    private static final ItemStack TOTEM_STACK = new ItemStack(Items.TOTEM_OF_UNDYING);

    public Totems() {
        super("Totems", "Displays the number of totems in inventory.", 120, 4);
        background.setValue(true);
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

        FontManager fm = FontManager.INSTANCE;
        String num = String.valueOf(count);
        float numW = fm.getStringWidth(num);
        int fontH = fm.getFontHeight();

        if (background.getValue()) {
            this.width = 3f + 16f + 3f + numW + 5f;
            this.height = 22f;
            HudTheme.plate(context, x, y, width, height, 6f);
            context.drawItem(TOTEM_STACK, (int) x + 3, (int) y + 3);
            fm.drawStringWithShadow(context, num, x + 3f + 16f + 3f, y + (height - fontH) / 2f + 0.5f, HudTheme.TEXT);
            return;
        }

        this.width = 16f + 4f + numW;
        this.height = 18f;
        context.drawItem(TOTEM_STACK, (int) x, (int) y + 1);
        fm.drawStringWithShadow(context, num, x + 16f + 4f, y + 5f, HudTheme.TEXT);
    }
}
