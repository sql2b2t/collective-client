package me.collective.module.tooltips;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Список «контейнер → доминирующий предмет».
 *
 * <p>Порт {@code TooltipsData} и списка {@code Tooltips.list} из модуля
 * Tooltips. Важная деталь оригинала: доминирующий считается по <b>числу
 * слотов</b>, а не по суммарному количеству предметов, и возвращается
 * {@code item.getDefaultStack()} — то есть один предмет без компонентов.
 *
 * <p>Список накапливается из пакетов обновления инвентаря, а не пересчитывается
 * наведением: в оригинале он обновляется на {@code InventoryS2CPacket} и
 * {@code ScreenHandlerSlotUpdateS2CPacket}.
 */
public final class DominantStacks {

    /** Контейнер и его доминирующий предмет. */
    public record Entry(ItemStack container, ItemStack dominant) {
    }

    private DominantStacks() {
    }

    /**
     * Пересчитывает список по содержимому контейнеров.
     *
     * <p>Порядок обхода сохраняется: контейнеры приходят из пакета, а
     * {@link Collections#synchronizedList} — так же, как в оригинале, потому
     * что список пишется из сетевого потока.
     */
    public static List<Entry> collect(List<ItemStack> candidates) {
        List<Entry> out = Collections.synchronizedList(new ArrayList<>());

        if (candidates == null) return out;

        for (ItemStack stack : candidates) {
            Entry entry = of(stack);
            if (entry != null) out.add(entry);
        }

        return out;
    }

    /**
     * Доминирующий предмет одного контейнера.
     *
     * @return запись или {@code null}, если это не контейнер или он пуст
     */
    public static Entry of(ItemStack stack) {
        ItemStack dominant = getDominant(stack);
        return dominant != null ? new Entry(stack, dominant) : null;
    }

    /**
     * Вычисляет доминирующий (наиболее частый по слотам) предмет внутри контейнера или мешка.
     *
     * @param stack предмет-контейнер (шаposition, шалкер-бокс, бандл и т.д.)
     * @return копия доминирующего предмета с сохраненными NBT/компонентами или {@code null}
     */
    public static ItemStack getDominant(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        net.minecraft.component.type.ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container != null) {
            return findDominantFromIterable(container.iterateNonEmpty());
        }

        net.minecraft.component.type.BundleContentsComponent bundle = stack.get(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            return findDominantFromIterable(bundle.iterate());
        }

        return null;
    }

    private static class CategoryGroup {
        int slotCount = 0;
        final java.util.Set<Item> distinctItems = new java.util.HashSet<>();
        ItemStack representative = null;
        int representativePriority = -1;

        void add(Item item, ItemStack stack, int priority) {
            slotCount++;
            distinctItems.add(item);
            if (priority > representativePriority || representative == null) {
                representativePriority = priority;
                representative = stack;
            }
        }

        double getScore() {
            // Если в группе 3+ разных предмета сета (например, шлем, нагрудник, поножи, ботинки) —
            // это полноценный кит/сет, даем максимальный приоритет над расходниками-наполнителями (тотемами, опытом).
            if (distinctItems.size() >= 3) {
                return slotCount * 4.0;
            } else if (distinctItems.size() >= 2) {
                return slotCount * 2.5;
            }
            return slotCount * 1.0;
        }
    }

    private static ItemStack findDominantFromIterable(Iterable<ItemStack> items) {
        if (items == null) return null;

        Map<String, CategoryGroup> groups = new HashMap<>();

        for (ItemStack content : items) {
            if (content == null || content.isEmpty()) continue;
            Item item = content.getItem();
            String category = getCategoryKey(content, item);
            int priority = getRepresentativePriority(content, item);

            groups.computeIfAbsent(category, k -> new CategoryGroup()).add(item, content, priority);
        }

        if (groups.isEmpty()) return null;

        CategoryGroup best = null;
        double bestScore = -1.0;

        for (CategoryGroup group : groups.values()) {
            double score = group.getScore();
            if (best == null || score > bestScore) {
                bestScore = score;
                best = group;
            }
        }

        if (best == null || best.representative == null) return null;

        ItemStack copy = best.representative.copy();
        copy.setCount(1);
        return copy;
    }

    private static String getCategoryKey(ItemStack stack, Item item) {
        String path = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        net.minecraft.component.type.EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);

        if (equippable != null && equippable.slot().isArmorSlot()) {
            if (path.startsWith("netherite_")) return "armor_netherite";
            if (path.startsWith("diamond_")) return "armor_diamond";
            if (path.startsWith("iron_")) return "armor_iron";
            if (path.startsWith("golden_")) return "armor_golden";
            if (path.startsWith("chainmail_")) return "armor_chainmail";
            if (path.startsWith("leather_")) return "armor_leather";
            return "armor_other";
        }

        if (item == net.minecraft.item.Items.ELYTRA) {
            return "armor_netherite"; // Элитра органично дополняет незеритовый PvP-сет
        }

        if (path.endsWith("_sword") || path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel")) {
            if (path.startsWith("netherite_")) return "tools_netherite";
            if (path.startsWith("diamond_")) return "tools_diamond";
        }

        return "item_" + net.minecraft.registry.Registries.ITEM.getId(item);
    }

    private static int getRepresentativePriority(ItemStack stack, Item item) {
        String path = net.minecraft.registry.Registries.ITEM.getId(item).getPath();
        if (path.endsWith("_chestplate")) return 100;
        if (item == net.minecraft.item.Items.ELYTRA) return 95;
        if (path.endsWith("_sword")) return 90;
        if (path.endsWith("_helmet")) return 80;
        if (path.endsWith("_leggings")) return 70;
        if (path.endsWith("_boots")) return 60;
        if (path.endsWith("_pickaxe")) return 50;
        if (path.endsWith("_axe")) return 40;
        return 10;
    }
}