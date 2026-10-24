package me.collective.module.player;

import me.collective.category.Category;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import me.collective.util.RotationUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;

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
        if (mc.player == null || mc.level == null) return;
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
            RotationUtils.setServerRotation(mc.player.getYRot(), 90f);
        }
        InventoryUtil.swap(xp.slot(), true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);
        InventoryUtil.swapBack();
        delayTimer = delay.getIntValue();
    }

    private boolean needsRepair() {
        double threshold = minDurability.getFloatValue() / 100.0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack armor = mc.player.getItemBySlot(slot);
            if (armor.isEmpty() || !hasMending(armor)) continue;
            if (!armor.isDamageableItem()) continue;
            double durability = 1.0 - (double) armor.getDamageValue() / armor.getMaxDamage();
            if (durability < threshold) return true;
        }
        return false;
    }

    private boolean hasMending(ItemStack stack) {
        try {
            net.minecraft.core.Holder<Enchantment> mending = mc.level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING);
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(mending, stack) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}