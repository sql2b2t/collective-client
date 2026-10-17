package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.Set;

public class AutoEat extends Module {
    public final NumberSetting hungerThreshold = add(new NumberSetting("Hunger", 16.0, 1.0, 19.0, 1.0));
    public final NumberSetting healthThreshold = add(new NumberSetting("Health", 12.0, 1.0, 36.0, 1.0));
    public final BooleanSetting gapples = add(new BooleanSetting("GapplesOnLowHp", true));

    private static final Set<Item> BAD_FOOD = Set.of(
        Items.ROTTEN_FLESH, Items.PUFFERFISH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.CHORUS_FRUIT
    );

    private boolean eating = false;
    private int prevSlot = -1;

    public boolean isEating() {
        return isToggled() && eating;
    }

    public AutoEat() {
        super("AutoEat", "Automatically eats food when hunger or health is low.", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        stopEating();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        boolean lowHunger = mc.player.getHungerManager().getFoodLevel() <= hungerThreshold.getValue().intValue();
        boolean lowHealth = (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= healthThreshold.getValue().floatValue();

        if (!lowHunger && !lowHealth) {
            if (eating) stopEating();
            return;
        }

        if (eating) {
            if (!mc.player.isUsingItem()) {
                stopEating();
            }
            return;
        }

        FindItemResult food = findFood(lowHealth && gapples.getValue());
        if (food.found()) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtil.swap(food.slot());
            mc.options.useKey.setPressed(true);
            eating = true;
        }
    }

    private FindItemResult findFood(boolean preferGapple) {
        if (preferGapple) {
            FindItemResult gapple = InventoryUtil.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE);
            if (gapple.found()) return gapple;
        }

        return InventoryUtil.findInHotbar(stack -> {
            if (stack.isEmpty()) return false;
            Item item = stack.getItem();
            if (BAD_FOOD.contains(item)) return false;
            return stack.contains(DataComponentTypes.FOOD);
        });
    }

    private void stopEating() {
        if (eating) {
            mc.options.useKey.setPressed(false);
            if (prevSlot != -1 && mc.player != null) {
                InventoryUtil.swap(prevSlot);
                prevSlot = -1;
            }
            eating = false;
        }
    }
}
