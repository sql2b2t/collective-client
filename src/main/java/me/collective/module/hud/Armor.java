package me.collective.module.hud;

import me.collective.font.FontManager;
import me.collective.render.RenderUtil;
import me.collective.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Armor extends HudModule {
    public final BooleanSetting showDamage = add(new BooleanSetting("ShowDamage", true));
    public final BooleanSetting showHands = add(new BooleanSetting("ShowHands", true));

    public Armor() {
        super("Armor", "Displays armor status and durability.", 200, 200);
    }

    @Override
    public void render(DrawContext context, float delta) {
        if (!isToggled() || mc.player == null) return;

        List<ItemStack> items = new ArrayList<>();
        if (showHands.getValue()) {
            items.add(mc.player.getMainHandStack());
        }
        items.add(mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
        items.add(mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
        items.add(mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
        items.add(mc.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
        if (showHands.getValue()) {
            items.add(mc.player.getOffHandStack());
        }
        Collections.reverse(items);

        int itemX = (int) x;
        int itemY = (int) y;
        this.width = items.size() * 18;
        this.height = showDamage.getValue() ? 28 : 18;

        if (background.getValue()) {
            RenderUtil.drawRect(context, x, y, x + width, y + height, new Color(20, 20, 20, 150));
        }

        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                context.drawItem(stack, itemX, itemY);
                context.drawStackOverlay(mc.textRenderer, stack, itemX, itemY);

                if (showDamage.getValue() && stack.isDamageable()) {
                    int maxDamage = stack.getMaxDamage();
                    int damage = stack.getDamage();
                    int percent = (int) (((maxDamage - damage) / (float) maxDamage) * 100);

                    Color dmgColor = percent > 50 ? Color.GREEN : (percent > 25 ? Color.YELLOW : Color.RED);
                    String pctText = percent + "%";
                    float textX = itemX + 9 - FontManager.INSTANCE.getStringWidth(pctText) / 2f;
                    FontManager.INSTANCE.drawStringWithShadow(context, pctText, textX, itemY + 18, dmgColor);
                }
            }
            itemX += 18;
        }
    }
}
