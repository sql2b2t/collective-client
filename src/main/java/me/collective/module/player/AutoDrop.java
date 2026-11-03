package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.InventoryUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Set;

/**
 * Порт AutoDrop из Meteor Client.
 *
 * <p>Выкидывает мусор из инвентаря при копке тоннелей.
 */
public class AutoDrop extends Module {
    public final NumberSetting delay = add(new NumberSetting("Задержка", 5.0, 1.0, 40.0, 1.0));
    public final BooleanSetting cobble = add(new BooleanSetting("Булыжник", true));
    public final BooleanSetting dirt = add(new BooleanSetting("Земля/гравий", true));
    public final BooleanSetting stoneTypes = add(new BooleanSetting("Каменные типы", true));
    public final BooleanSetting netherrack = add(new BooleanSetting("Незер-камень", true));

    private static final Set<Item> DIRT = Set.of(Items.DIRT, Items.GRAVEL, Items.SAND, Items.RED_SAND, Items.SOUL_SAND, Items.SOUL_SOIL);
    private static final Set<Item> STONE_TYPES = Set.of(Items.DIORITE, Items.GRANITE, Items.ANDESITE, Items.TUFF, Items.CALCITE, Items.SANDSTONE, Items.BASALT, Items.BLACKSTONE);

    private int delayTimer = 0;

    public AutoDrop() {
        super("AutoDrop", "Выкидывает мусор из инвентаря.", Category.PLAYER);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || !InventoryUtil.canUsePlayerInventory()) return;
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || s.contains(DataComponentTypes.CUSTOM_NAME)
                || s.contains(DataComponentTypes.CUSTOM_DATA)
                || s.hasEnchantments()) continue;
            if (isTrash(s.getItem())) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, mc.player);
                delayTimer = delay.getIntValue();
                return;
            }
        }
    }

    private boolean isTrash(Item item) {
        if (cobble.getValue() && item == Items.COBBLESTONE) return true;
        if (dirt.getValue() && DIRT.contains(item)) return true;
        if (stoneTypes.getValue() && STONE_TYPES.contains(item)) return true;
        if (netherrack.getValue() && item == Items.NETHERRACK) return true;
        return false;
    }
}
