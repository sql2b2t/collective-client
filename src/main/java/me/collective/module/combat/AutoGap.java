package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.item.Items;

public class AutoGap extends Module {
    public final NumberSetting health = add(new NumberSetting("Health", 15.0, 1.0, 36.0, 1.0));
    public final BooleanSetting preferEnchanted = add(new BooleanSetting("PreferGodApples", true));

    private boolean eating = false;
    private int prevSlot = -1;
    private int waitTicks = 0;

    public AutoGap() {
        super("AutoGap", "Автоматически ест золотые яблоки при низком уровне здоровья.", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        stopEating();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (hp > health.getValue().floatValue()) {
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

        // 1. Offhand check
        var offhand = mc.player.getOffHandStack();
        if (offhand.isOf(Items.ENCHANTED_GOLDEN_APPLE) || (!preferEnchanted.getValue() && offhand.isOf(Items.GOLDEN_APPLE))) {
            prevSlot = -1;
            mc.options.useKey.setPressed(true);
            eating = true;
            waitTicks = 0;
            return;
        }

        // 2. Hotbar check
        FindItemResult gap = findGap();
        if (gap.found()) {
            prevSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtil.swap(gap.slot());
            mc.options.useKey.setPressed(true);
            eating = true;
            waitTicks = 0;
        }
    }

    private FindItemResult findGap() {
        if (preferEnchanted.getValue()) {
            FindItemResult god = InventoryUtil.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE);
            if (god.found()) return god;
        }
        return InventoryUtil.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE);
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
