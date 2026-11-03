package me.collective.module.highway;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;

import java.util.function.Predicate;

/**
 * Инвентарные хелперы хайвей-билдера: поиск/подсчёт предметов и оценка
 * инструментов. Настройки модуля не читает — зависящий от конфига выбор
 * слота ({@code findHotbarSlot} и т.п.) остаётся в {@link HighwayBuilderHIG}.
 */
final class HighwayInventory {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private HighwayInventory() {
    }

    /** Индекс стека по предикату; хотбар 0-8 или инвентарь 9-35. */
    static int findSlot(Predicate<ItemStack> predicate, boolean hotbar) {
        if (mc.player == null) return -1;
        for (int i = hotbar ? 0 : 9; i < (hotbar ? 9 : 36); i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return i;
        }
        return -1;
    }

    static boolean hasItem(Predicate<ItemStack> predicate) {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (predicate.test(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    static int countItem(Predicate<ItemStack> predicate) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (predicate.test(stack)) count += stack.getCount();
        }
        return count;
    }

    /**
     * Оценка инструмента под блок: пригодность + эффективность + прочность.
     * Гард прочности читает настройки билдера (DontBreakTools/DurabilityPct).
     */
    static double toolScore(HighwayBuilderHIG b, ItemStack stack, BlockState state, boolean noSilkTouch) {
        if (stack.isEmpty() || !stack.isDamageable()) return -1;
        if (noSilkTouch && hasEnchantment(stack, Enchantments.SILK_TOUCH)) return -1;
        if (b.dontBreakTools.getValue()
            && stack.getMaxDamage() - stack.getDamage() <= stack.getMaxDamage() * (b.breakDurability.getValue() / 100.0)) {
            return -1;
        }
        double score = 0;
        if (stack.isSuitableFor(state)) score += 100;
        score += effLevel(stack) * 10;
        score += stack.getMaxDamage() / 128.0;
        return score;
    }

    static boolean hasEnchantment(ItemStack stack, net.minecraft.registry.RegistryKey<Enchantment> key) {
        try {
            RegistryEntry<Enchantment> entry = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key);
            return stack.getEnchantments().getEnchantments().stream().anyMatch(e -> e.equals(entry));
        } catch (Exception ignored) {
            return false;
        }
    }

    static int effLevel(ItemStack stack) {
        try {
            RegistryEntry<Enchantment> entry = mc.world.getRegistryManager()
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
            return stack.getEnchantments().getLevel(entry);
        } catch (Exception ignored) {
            return 0;
        }
    }

    static boolean isShulker(Item item) {
        return item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    static boolean isToolLike(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageable() && !(stack.getItem() instanceof BlockItem);
    }
}
