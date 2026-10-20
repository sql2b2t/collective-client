package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;

/**
 * Порт AutoMend из Meteor Client.
 *
 * <p>Кидает бутыльки опыта под ноги, пока броня с починкой не восстановится.
 * Спасает сет в кристальных дуэлях.
 */
public class AutoMend extends Module {
    public final NumberSetting minDurability = add(new NumberSetting("Мин. прочность %", 80.0, 10.0, 100.0, 1.0));
    public final NumberSetting delay = add(new NumberSetting("Задержка", 2.0, 0.0, 10.0, 1.0));
    public final BooleanSetting silentAim = add(new BooleanSetting("Тихий прицел", true));
    public final BooleanSetting autoDisable = add(new BooleanSetting("Выкл когда готово", true));

    private int delayTimer = 0;
    /** Предупреждение про бутыльки_Integer один раз, а не каждый тик. */
    private boolean warnedNoXp = false;

    public AutoMend() {
        super("AutoMend", "Чинит броню бутыльками опыта.", Category.PLAYER);
    }

    @Override
    public void onUpdate() {
        if (mc.player == null || mc.world == null) return;
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        if (!needsRepair()) {
            if (autoDisable.getValue() && isToggled()) setToggled(false);
            return;
        }

        FindItemResult xp = InventoryUtil.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!xp.found()) {
            if (!warnedNoXp) {
                warnedNoXp = true;
                warning("Нет бутыльков опыта в хотбаре!");
            }
            if (autoDisable.getValue()) setToggled(false);
            return;
        }
        warnedNoXp = false;

        if (silentAim.getValue()) {
            RotationUtils.setServerRotation(mc.player.getYaw(), 90f);
        }
        InventoryUtil.swap(xp.slot(), true);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
        InventoryUtil.swapBack();
        delayTimer = delay.getIntValue();
    }

    private boolean needsRepair() {
        double threshold = minDurability.getFloatValue() / 100.0;
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack armor = mc.player.getEquippedStack(slot);
            if (armor.isEmpty() || !hasMending(armor)) continue;
            if (!armor.isDamageable()) continue;
            double durability = 1.0 - (double) armor.getDamage() / armor.getMaxDamage();
            if (durability < threshold) return true;
        }
        return false;
    }

    private boolean hasMending(ItemStack stack) {
        try {
            var enchantments = stack.getEnchantments();
            RegistryEntry<Enchantment> mending = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
            return enchantments.getEnchantments().stream().anyMatch(e -> e.equals(mending));
        } catch (Exception e) {
            return false;
        }
    }
}
