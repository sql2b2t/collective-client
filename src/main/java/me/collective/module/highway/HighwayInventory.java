package me.collective.module.highway;

import me.collective.module.highway.HighwayBuilderHIG;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.registry.tag.ItemTags;

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

    /**
     * Выбор слота хотбара под новый предмет: пустой слот, затем мусор по
     * TrashItems, затем стак «строительных» блоков с минимальным счётчиком.
     * Читает настройки билдера (TrashItems/BlocksToPlace).
     */
    static int findHotbarSlot(HighwayBuilderHIG b, boolean replaceTools) {
        int thrashSlot = -1;
        int slotsWithBlocks = 0;
        int slotWithLeastBlocks = -1;
        int slotWithLeastBlocksCount = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);

            if (itemStack.isEmpty()) return i;

            if (replaceTools && isToolLike(itemStack)) return i;

            if (b.trashItems.getValue().contains(itemStack.getItem())) thrashSlot = i;

            if (itemStack.getItem() instanceof BlockItem blockItem
                && (b.blocksToPlace.getValue().contains(blockItem.getBlock())
                    || b.blocksToPlace.getValue().contains(Blocks.OBSIDIAN) && blockItem == Items.ENDER_CHEST)) {
                slotsWithBlocks++;

                if (itemStack.getCount() < slotWithLeastBlocksCount) {
                    slotWithLeastBlocksCount = itemStack.getCount();
                    slotWithLeastBlocks = i;
                }
            }
        }

        if (thrashSlot != -1) return thrashSlot;

        if (slotsWithBlocks > 0) return slotWithLeastBlocks;

        b.error("No empty space in hotbar.");
        return -1;
    }

    /** Находит предмет и при необходимости доставляет его в хотбар. */
    static int findAndMoveToHotbar(HighwayBuilderHIG b, Predicate<ItemStack> predicate) {
        int slot = findSlot(predicate, true);
        if (slot != -1) return slot;

        int hotbarSlot = findHotbarSlot(b, false);
        if (hotbarSlot == -1) return -1;

        slot = findSlot(predicate, false);
        if (slot == -1) return -1;

        movePairToHotbar(slot, hotbarSlot);

        return hotbarSlot;
    }

    /**
     * Лучший инструмент под блок с гардом минимального числа кирок
     * (SavePickaxes + restock). Возвращает слот хотбара или -1, если
     * инструмент отдыхает до рестока.
     */
    static int findAndMoveBestToolToHotbar(HighwayBuilderHIG b, BlockState blockState, boolean noSilkTouch) {
        if (mc.player.isCreative()) return mc.player.getInventory().selectedSlot;

        double bestScore = -1;
        int bestSlot = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            double score = toolScore(b, stack, blockState, noSilkTouch);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot == -1) return mc.player.getInventory().selectedSlot;

        ItemStack bestStack = mc.player.getInventory().getStack(bestSlot);
        if (bestStack.isIn(ItemTags.PICKAXES)) {
            int count = countItem(stack -> stack.isIn(ItemTags.PICKAXES));

            if (count <= b.savePickaxes.getIntValue()
                && !(b.restockTask.pickaxes && bestStack.getMaxDamage() - bestStack.getDamage()
                    > (bestStack.getMaxDamage() * (b.breakDurability.getValue() / 100.0)))) {
                if (!b.restockTask.pickaxes && (b.searchEnderChest.getValue() || b.searchShulkers.getValue())) {
                    b.restockTask.setPickaxes();
                } else {
                    b.error("Found less than the minimum amount of pickaxes required: "
                        + count + "/" + (b.savePickaxes.getIntValue() + 1));
                }

                return -1;
            }
        }

        if (bestSlot < 9) return bestSlot;

        int hotbarSlot = findHotbarSlot(b, true);
        if (hotbarSlot == -1) return -1;

        movePairToHotbar(bestSlot, hotbarSlot);

        return hotbarSlot;
    }

    /** PICKUP-пара: слот инвентаря -> слот хотбара с возвратом вытесненного предмета в исходный слот. */
    private static void movePairToHotbar(int fromInv, int toHotbar) {
        if (mc.player == null || mc.interactionManager == null) return;
        int from = fromInv < 9 ? 36 + fromInv : fromInv;
        int to = 36 + toHotbar;
        if (from == to) return;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, to, 0, SlotActionType.PICKUP, mc.player);
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        }
    }
}
