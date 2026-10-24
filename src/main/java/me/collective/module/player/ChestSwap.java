package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.EnumSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.collective.util.InventoryUtil;
import net.minecraft.world.inventory.ClickType;

public class ChestSwap extends Module {
    public enum TargetChest {
        NETHERITE, DIAMOND, PREFER_NETHERITE, PREFER_DIAMOND
    }

    public final EnumSetting<TargetChest> chestplateMode = add(new EnumSetting<>("Chestplate", TargetChest.PREFER_NETHERITE));
    public final BooleanSetting closeScreen = add(new BooleanSetting("CloseScreen", true));

    public ChestSwap() {
        super("ChestSwap", "Мгновенно меняет нагрудник на элитры и обратно по нажатию клавиши.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        swap();
        setToggled(false);
    }

    public void swap() {
        if (!InventoryUtil.canUsePlayerInventory()) return;

        ItemStack current = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (current.contains(DataComponentTypes.GLIDER)) {
            equipChestplate();
        } else {
            equipElytra();
        }
    }

    private boolean equipChestplate() {
        int bestSlot = -1;

        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            Item item = mc.player.getInventory().getMainStacks().get(i).getItem();

            if (chestplateMode.getValue() == TargetChest.NETHERITE && item == Items.NETHERITE_CHESTPLATE) {
                bestSlot = i;
                break;
            } else if (chestplateMode.getValue() == TargetChest.DIAMOND && item == Items.DIAMOND_CHESTPLATE) {
                bestSlot = i;
                break;
            } else if (chestplateMode.getValue() == TargetChest.PREFER_NETHERITE) {
                if (item == Items.NETHERITE_CHESTPLATE) {
                    bestSlot = i;
                    break;
                } else if (item == Items.DIAMOND_CHESTPLATE && bestSlot == -1) {
                    bestSlot = i;
                }
            } else if (chestplateMode.getValue() == TargetChest.PREFER_DIAMOND) {
                if (item == Items.DIAMOND_CHESTPLATE) {
                    bestSlot = i;
                    break;
                } else if (item == Items.NETHERITE_CHESTPLATE && bestSlot == -1) {
                    bestSlot = i;
                }
            }
        }

        if (bestSlot != -1) {
            equip(bestSlot);
            return true;
        }
        return false;
    }

    private void equipElytra() {
        for (int i = 0; i < mc.player.getInventory().getMainStacks().size(); i++) {
            ItemStack stack = mc.player.getInventory().getMainStacks().get(i);
            if (stack.contains(DataComponentTypes.GLIDER)) {
                equip(i);
                break;
            }
        }
    }

    private void equip(int slot) {
        if (!InventoryUtil.canUsePlayerInventory() || mc.currentScreen != null) return;
        if (!mc.player.playerScreenHandler.getSlot(6).getStack().isEmpty()
            && !mc.player.playerScreenHandler.getSlot(6).canTakeItems(mc.player)) return;
        if (slot >= 0 && slot < 9) {
            // Хотбар: один атомарный SWAP вместо трёх PICKUP (меньше флагов)
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, slot, SlotActionType.SWAP, mc.player);
        } else {
            InventoryUtil.move().from(slot).to(38);
        }

        // No synthetic close packet: the player's handler remains open locally too.
    }
}