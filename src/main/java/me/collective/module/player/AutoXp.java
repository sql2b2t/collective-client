package me.collective.module.player;

import me.collective.category.Category;
import me.collective.event.EventHandler;
import me.collective.event.events.TickEvent;
import me.collective.module.Module;
import me.collective.setting.BooleanSetting;
import me.collective.setting.NumberSetting;
import me.collective.util.FindItemResult;
import me.collective.util.InventoryUtil;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;

/**
 * AutoXp: автоматически кидает бутылки опыта для починки брони с Починкой.
 * 1:1 порт из b2xy.
 */
public class AutoXp extends Module {

    public final NumberSetting durabilityThreshold = add(new NumberSetting("DurabilityThreshold", 70, 1, 99, 1));
    public final NumberSetting keepLevel = add(new NumberSetting("KeepLevel", 0, 0, 100, 1));
    public final NumberSetting cooldown = add(new NumberSetting("Cooldown", 8, 1, 40, 1));
    public final BooleanSetting lookDown = add(new BooleanSetting("LookDown", true));

    private int timer = 0;

    public AutoXp() {
        super("AutoXp", "Автоматически кидает бутылки опыта для починки брони с Починкой.", Category.PLAYER);
    }

    @Override
    public void onEnable() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (timer > 0) {
            timer--;
            return;
        }
        if (mc.player.experienceLevel < keepLevel.getIntValue()) return;

        FindItemResult bottle = InventoryUtil.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!bottle.found()) return;

        if (!needsMending()) return;

        if (lookDown.getValue()) {
            me.collective.util.RotationUtils.setServerRotation(mc.player.getYaw(), 90.0f);
        }

        InventoryUtil.swap(bottle.slot(), true);
        mc.interactionManager.interactItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swingHand(InteractionHand.MAIN_HAND);
        InventoryUtil.swapBack();
        timer = cooldown.getIntValue();
    }

    private boolean needsMending() {
        var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue;
            var stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty() || !stack.isDamageable()) continue;
            int max = stack.getMaxDamage();
            int dmg = stack.getDamage();
            if (max <= 0 || (max - dmg) * 100 / max >= durabilityThreshold.getIntValue()) continue;
            if (EnchantmentHelper.getLevel(reg.getEntry(reg.getValueOrThrow(Enchantments.MENDING)), stack) > 0) return true;
        }
        return false;
    }
}