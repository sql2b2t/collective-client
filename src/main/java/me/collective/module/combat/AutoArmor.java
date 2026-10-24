package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import me.collective.util.InventoryUtil;

public class AutoArmor extends Module {
    public final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 5.0, 1.0));
    public final BooleanSetting preferBlastProt = add(new BooleanSetting("PreferBlastProt", true));
    public final BooleanSetting saveDurability = add(new BooleanSetting("SaveDurability", true));
    public final BooleanSetting elytraProtect = add(new BooleanSetting("ElytraProtect", true));

    private int delayTimer = 0;

    public AutoArmor() {
        super("AutoArmor", "Автоматически надевает лучшую броню с оценкой прочности и зачарований.", Category.COMBAT);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null || !InventoryUtil.canUsePlayerInventory()) return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        int[] screenSlots = { 5, 6, 7, 8 };

        for (int i = 0; i < slots.length; i++) {
            EquipmentSlot slot = slots[i];
            int screenSlot = screenSlots[i];

            ItemStack current = mc.player.getItemBySlot(slot);
            if (slot == EquipmentSlot.CHEST && elytraProtect.getValue()) {
                if (current.has(DataComponents.GLIDER) || mc.player.isFallFlying()) {
                    continue;
                }
            }

            int bestInvSlot = -1;
            int bestScore = getScore(current);

            for (int invSlot = 0; invSlot < 36; invSlot++) {
                ItemStack stack = mc.player.getInventory().getItem(invSlot);
                if (stack.has(DataComponents.EQUIPPABLE)) {
                    var equippable = stack.get(DataComponents.EQUIPPABLE);
                    if (equippable != null && equippable.slot() == slot) {
                        // Защита от поломки редкой брони
                        if (saveDurability.getValue() && stack.isDamageableItem()) {
                            int maxDmg = stack.getMaxDamage();
                            int curDmg = stack.getDamageValue();
                            if ((maxDmg - curDmg) < 15) continue; // менее 15 прочности
                        }

                        int score = getScore(stack);
                        if (score > bestScore) {
                            bestScore = score;
                            bestInvSlot = invSlot;
                        }
                    }
                }
            }

            if (bestInvSlot != -1) {
                if (!current.isEmpty() && !mc.player.inventoryMenu.getSlot(screenSlot).mayPickup(mc.player)) continue;
                InventoryUtil.move().from(bestInvSlot).to(39 - i);

                delayTimer = delay.getValue().intValue();
                return;
            }
        }
    }

    private int getScore(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.EQUIPPABLE)) return -1;
        if (mc.level == null) return 0;

        int score = 0;

        // Базовый материал
        String name = stack.getItem().toString().toLowerCase();
        if (name.contains("netherite")) score += 40;
        else if (name.contains("diamond")) score += 30;
        else if (name.contains("iron")) score += 20;
        else if (name.contains("chainmail")) score += 15;
        else if (name.contains("golden") || name.contains("gold")) score += 12;
        else if (name.contains("leather")) score += 8;

        // Расчёт зачарований под 2b2t анархию
        try {
            var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var enchs = EnchantmentHelper.getEnchantmentsForCrafting(stack);

            int blastProt = enchs.getLevel(reg.getOrThrow(Enchantments.BLAST_PROTECTION));
            int prot = enchs.getLevel(reg.getOrThrow(Enchantments.PROTECTION));
            int unbreaking = enchs.getLevel(reg.getOrThrow(Enchantments.UNBREAKING));
            int mending = enchs.getLevel(reg.getOrThrow(Enchantments.MENDING));

            // На 2b2t защита от взрывов (кристаллы/анчоры) имеет наивысший приоритет
            if (preferBlastProt.getValue()) {
                score += blastProt * 12;
                score += prot * 6;
            } else {
                score += prot * 10;
                score += blastProt * 8;
            }

            score += unbreaking * 3;
            score += mending * 4;
        } catch (Exception ignored) {}

        return score;
    }
}