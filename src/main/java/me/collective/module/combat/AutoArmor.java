package me.collective.module.combat;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.slot.SlotActionType;

public class AutoArmor extends Module {
    public final NumberSetting delay = add(new NumberSetting("Delay", 1.0, 0.0, 5.0, 1.0));
    public final BooleanSetting preferBlastProt = add(new BooleanSetting("PreferBlastProt", true));
    public final BooleanSetting saveDurability = add(new BooleanSetting("SaveDurability", true));

    private int delayTimer = 0;

    public AutoArmor() {
        super("AutoArmor", "Automatically equips the strongest armor with enchantment scoring for 2b2t.", Category.COMBAT);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        int[] screenSlots = { 5, 6, 7, 8 };

        for (int i = 0; i < slots.length; i++) {
            EquipmentSlot slot = slots[i];
            int screenSlot = screenSlots[i];

            ItemStack current = mc.player.getEquippedStack(slot);
            int bestInvSlot = -1;
            int bestScore = getScore(current);

            for (int invSlot = 0; invSlot < 36; invSlot++) {
                ItemStack stack = mc.player.getInventory().getStack(invSlot);
                if (stack.contains(DataComponentTypes.EQUIPPABLE)) {
                    var equippable = stack.get(DataComponentTypes.EQUIPPABLE);
                    if (equippable != null && equippable.slot() == slot) {
                        // Защита от поломки редкой брони
                        if (saveDurability.getValue() && stack.isDamageable()) {
                            int maxDmg = stack.getMaxDamage();
                            int curDmg = stack.getDamage();
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
                int fromSlot = bestInvSlot < 9 ? 36 + bestInvSlot : bestInvSlot;

                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fromSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, fromSlot, 0, SlotActionType.PICKUP, mc.player);

                delayTimer = delay.getValue().intValue();
                return;
            }
        }
    }

    private int getScore(ItemStack stack) {
        if (stack.isEmpty() || !stack.contains(DataComponentTypes.EQUIPPABLE)) return -1;
        if (mc.world == null) return 0;

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
            var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);

            var blastProtEntry = reg.getEntry(reg.getValueOrThrow(Enchantments.BLAST_PROTECTION));
            int blastProt = EnchantmentHelper.getLevel(blastProtEntry, stack);

            var protEntry = reg.getEntry(reg.getValueOrThrow(Enchantments.PROTECTION));
            int prot = EnchantmentHelper.getLevel(protEntry, stack);

            var unbreakingEntry = reg.getEntry(reg.getValueOrThrow(Enchantments.UNBREAKING));
            int unbreaking = EnchantmentHelper.getLevel(unbreakingEntry, stack);

            var mendingEntry = reg.getEntry(reg.getValueOrThrow(Enchantments.MENDING));
            int mending = EnchantmentHelper.getLevel(mendingEntry, stack);

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
