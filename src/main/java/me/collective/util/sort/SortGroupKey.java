package me.collective.util.sort;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

/**
 * Ключ категории («сигнатура») — то, по чему сортировщик решает, что куда.
 *
 * <ul>
 *   <li>Шалкер, где 60%+ стопок одного предмета → {@code shulker#minecraft:обсидиан}</li>
 *   <li>Шалкер с набором разного → {@code shulker#minecraft:обсидиан,minecraft:золото} (кит)</li>
 *   <li>Пустой шалкер → {@code shulker#empty}</li>
 *   <li>Рассыпанный предмет → его {@link ItemSignature#of(ItemStack)}</li>
 * </ul>
 */
public final class SortGroupKey {
    public static final String SHULKER_PREFIX = "shulker#";
    public static final String EMPTY_SHULKERS = "shulker#empty";
    public static final String KIT_SHULKERS = "shulker#kits";
    public static final String MISC_SHULKERS = "shulker#misc";
    public static final String MIXED_ITEMS = "mixed#items";
    public static final String OVERFLOW_SHULKERS = "shulker#overflow";
    public static final String OVERFLOW_ITEMS = "mixed#overflow";
    /** Ниже этой доли стопок один предмет считается «китом», а не россыпью. */
    private static final int DOMINANT_PERCENT = 60;

    private SortGroupKey() {
    }

    public static String of(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        if (!ContainerGeometry.isShulkerItem(s)) return ItemSignature.of(s);

        Map<Item, Integer> stackShare = new HashMap<>();
        int totalStacks = 0;
        for (ItemStack nested : ShulkerDataParser.parseShulkerContentsAsList(s)) {
            if (nested != null && !nested.isEmpty()) {
                stackShare.merge(nested.getItem(), 1, Integer::sum);
                totalStacks++;
            }
        }

        if (stackShare.isEmpty()) return EMPTY_SHULKERS;

        Item top = null;
        int topStacks = 0;
        for (Entry<Item, Integer> e : stackShare.entrySet()) {
            if (e.getValue() > topStacks) {
                top = e.getKey();
                topStacks = e.getValue();
            }
        }

        // Один предмет занимает большую часть шалкера — называем его содержимым
        if (stackShare.size() != 1 && topStacks * 100 < DOMINANT_PERCENT * totalStacks) {
            List<String> ids = new ArrayList<>();
            for (Item item : stackShare.keySet()) ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
            return format(ids);
        }

        return SHULKER_PREFIX + BuiltInRegistries.ITEM.getKey(top);
    }

    /** Кит = шалкер с перечислением нескольких предметов. */
    public static boolean isExactKitKey(String key) {
        return isShulkerKey(key) && key.indexOf(',') >= 0;
    }

    /** Куда сваливать то, что не опозналось как отдельная категория. */
    public static String fallbackKey(String key) {
        if (isExactKitKey(key)) return KIT_SHULKERS;
        return isShulkerKey(key)
            && !EMPTY_SHULKERS.equals(key) && !KIT_SHULKERS.equals(key) && !OVERFLOW_SHULKERS.equals(key)
            ? MISC_SHULKERS
            : key;
    }

    public static String overflowKey(String key) {
        return isShulkerGroup(key) ? OVERFLOW_SHULKERS : OVERFLOW_ITEMS;
    }

    public static boolean isOverflowKey(String key) {
        return OVERFLOW_SHULKERS.equals(key) || OVERFLOW_ITEMS.equals(key);
    }

    public static String format(Collection<String> contentIds) {
        return contentIds.isEmpty() ? EMPTY_SHULKERS : SHULKER_PREFIX + String.join(",", new TreeSet<>(contentIds));
    }

    public static boolean isShulkerKey(String key) {
        return key.startsWith(SHULKER_PREFIX);
    }

    public static boolean isShulkerGroup(String key) {
        return isShulkerKey(key) || itemPath(key).endsWith("shulker_box");
    }

    /** Понятное имя категории для оверлея и сообщений. */
    public static String friendlyName(String key) {
        if (EMPTY_SHULKERS.equals(key)) return "пустые шалкеры";
        if (KIT_SHULKERS.equals(key)) return "кит-шалкеры";
        if (MISC_SHULKERS.equals(key)) return "прочие шалкеры";
        if (OVERFLOW_SHULKERS.equals(key)) return "смешанные шалкеры";
        if (MIXED_ITEMS.equals(key)) return "смешанные предметы";
        if (OVERFLOW_ITEMS.equals(key)) return "переполненные предметы";

        if (isShulkerKey(key)) {
            String[] ids = key.substring(SHULKER_PREFIX.length()).split(",");
            if (ids.length == 1) return path(ids[0]) + " (шалкер)";

            StringBuilder sb = new StringBuilder("киты (").append(path(ids[0])).append(", ").append(path(ids[1]));
            if (ids.length > 2) sb.append(", +").append(ids.length - 2).append(" ещё");
            return sb.append(")").toString();
        }

        int bar = key.indexOf('|');
        String name = path(bar >= 0 ? key.substring(0, bar) : key);
        boolean enchanted = bar >= 0 && bar < key.length() - 1;
        return enchanted ? name + " (зачарован)" : name;
    }

    public static String looseItemId(String key) {
        int bar = key.indexOf('|');
        return bar >= 0 ? key.substring(0, bar) : key;
    }

    /** Предметы, из которых состоит кит; для служебных ключей — пусто. */
    public static List<String> shulkerContentIds(String key) {
        return isShulkerKey(key)
            && !EMPTY_SHULKERS.equals(key) && !KIT_SHULKERS.equals(key)
            && !MISC_SHULKERS.equals(key) && !OVERFLOW_SHULKERS.equals(key)
            ? List.of(key.substring(SHULKER_PREFIX.length()).split(","))
            : List.of();
    }

    public static ItemStack iconStack(String key) {
        if (MIXED_ITEMS.equals(key) || OVERFLOW_ITEMS.equals(key)) return new ItemStack(Items.CHEST);
        return isShulkerKey(key) ? new ItemStack(Items.SHULKER_BOX) : stackFromId(looseItemId(key));
    }

    public static ItemStack badgeStack(String key) {
        List<String> ids = shulkerContentIds(key);
        return ids.isEmpty() ? ItemStack.EMPTY : stackFromId(ids.get(0));
    }

    private static ItemStack stackFromId(String id) {
        Identifier ident = Identifier.tryParse(id);
        return ident != null && BuiltInRegistries.ITEM.containsKey(ident)
            ? new ItemStack((net.minecraft.world.level.ItemLike) BuiltInRegistries.ITEM.getValue(ident))
            : ItemStack.EMPTY;
    }

    private static String itemPath(String sig) {
        int bar = sig.indexOf('|');
        return path(bar >= 0 ? sig.substring(0, bar) : sig);
    }

    /** Отбрасываем namespace — пользователю «обсидиан» понятнее, чем «minecraft:обсидиан». */
    private static String path(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}