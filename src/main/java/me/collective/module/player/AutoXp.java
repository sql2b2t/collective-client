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
        if (mc.player == null || mc.level == null) return;
        if (timer > 0) {
            timer--;
            return;
        }
        if (mc.player.experienceLevel < keepLevel.getIntValue()) return;

        FindItemResult bottle = InventoryUtil.findInHotbar(Items.EXPERIENCE_BOTTLE);
        if (!bottle.found()) return;

        if (!needsMending()) return;

        if (lookDown.getValue()) {
            me.collective.util.RotationUtils.setServerRotation(mc.player.getYRot(), 90.0f);
        }

        InventoryUtil.swap(bottle.slot(), true);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        me.collective.util.Utils.swingHand(InteractionHand.MAIN_HAND);
        InventoryUtil.swapBack();
        timer = cooldown.getIntValue();
    }

    private boolean needsMending() {
        var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            var stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            int max = stack.getMaxDamage();
            int dmg = stack.getDamageValue();
            if (max <= 0 || (max - dmg) * 100 / max >= durabilityThreshold.getIntValue()) continue;
            if (EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(reg.getOrThrow(Enchantments.MENDING)) > 0) return true;
        }
        return false;
    }
}