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
    private int waitTicks = 0;

    public boolean isEating() {
        return isToggled() && eating;
    }

    public AutoEat() {
        super("AutoEat", "Автоматически ест еду из хотбара при голоде или потере здоровья.", Category.PLAYER);
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
            waitTicks++;
            if (waitTicks > 3 && !mc.player.isUsingItem()) {
                stopEating();
            }
            return;
        }

        boolean preferGapple = lowHealth && gapples.getValue();

        // 1. Check offhand first
        if (isFood(mc.player.getOffHandStack(), preferGapple)) {
            prevSlot = -1;
            mc.options.useKey.setPressed(true);
            eating = true;
            waitTicks = 0;
            return;
        }

        // 2. Check hotbar
        FindItemResult food = findFood(preferGapple);
        if (food.found()) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtil.swap(food.slot());
            mc.options.useKey.setPressed(true);
            eating = true;
            waitTicks = 0;
            return;
        }

        // 3. Check inventory fallback
        FindItemResult invFood = findFoodInInventory(preferGapple);
        if (invFood.found() && InventoryUtil.canUsePlayerInventory()) {
            int currentHotbar = mc.player.getInventory().getSelectedSlot();
            InventoryUtil.move().from(invFood.slot()).to(currentHotbar);
            prevSlot = currentHotbar;
            mc.options.useKey.setPressed(true);
            eating = true;
            waitTicks = 0;
        }
    }

    private boolean isFood(ItemStack stack, boolean preferGapple) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (BAD_FOOD.contains(item)) return false;
        if (preferGapple) {
            return item == Items.ENCHANTED_GOLDEN_APPLE || item == Items.GOLDEN_APPLE;
        }
        return stack.contains(DataComponentTypes.FOOD);
    }

    private FindItemResult findFood(boolean preferGapple) {
        if (preferGapple) {
            FindItemResult gapple = InventoryUtil.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE);
            if (gapple.found()) return gapple;
        }

        return InventoryUtil.findInHotbar(stack -> isFood(stack, false));
    }

    private FindItemResult findFoodInInventory(boolean preferGapple) {
        if (preferGapple) {
            FindItemResult gapple = InventoryUtil.find(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE);
            if (gapple.found()) return gapple;
        }

        return InventoryUtil.find(stack -> isFood(stack, false));
    }

    private void stopEating() {
        if (eating) {
            mc.options.useKey.setPressed(false);
            if (prevSlot != -1 && mc.player != null) {
                InventoryUtil.swap(prevSlot);
                prevSlot = -1;
            }
            eating = false;
            waitTicks = 0;
        }
    }
}
