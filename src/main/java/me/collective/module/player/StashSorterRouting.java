package me.collective.module.player;

import me.collective.util.sort.ContainerGeometry;
import me.collective.util.sort.SortGroupKey;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Маршрутизация стопок для {@link StashSorter}: ключ категории предмета,
 * свёртка мелких категорий и правила попадания в общий/переполненный сундук.
 * Читает настройки сортировщика и его карты маршрутов, ничего не мутирует.
 *
 * <p>Выделено из тела {@link StashSorter} по паттерну компаньонов
 * ({@code HighwayRestock} и далее): сортировщик держит тонкие делегаты,
 * логика живёт здесь.
 */
final class StashSorterRouting {
    private StashSorterRouting() {
    }

    /**
     * Куда едет эта стопка. Мелкие категории сводим в общую, если так настроено,
     * а если дома нет — в «переполненное», иначе вещи останутся в инвентаре.
     */
    @Nullable
    static String routeKeyOf(StashSorter b, ItemStack s, boolean allowOverflow) {
        boolean shulker = ContainerGeometry.isShulkerItem(s);
        if (shulker && !b.moveShulkers.get()) return null;
        if (!shulker && b.looseItems.get() == StashSorter.LooseItems.IGNORE) return null;

        String key = SortGroupKey.of(s);
        if (b.sigTargets.containsKey(key)) return allowOverflow ? b.overflowRoutes.getOrDefault(key, key) : key;

        String fallback = groupOf(b, key);
        if (!fallback.equals(key) && b.sigTargets.containsKey(fallback)) {
            return allowOverflow ? b.overflowRoutes.getOrDefault(fallback, fallback) : fallback;
        }
        return allowOverflow ? SortGroupKey.overflowKey(key) : null;
    }

    static String groupOf(StashSorter b, String key) {
        String mapped = b.keyCollapse.get(key);
        if (mapped != null) return mapped;
        return b.looseItems.get() == StashSorter.LooseItems.MIXED_CHEST && !key.isEmpty() && !SortGroupKey.isShulkerGroup(key)
            ? SortGroupKey.MIXED_ITEMS
            : SortGroupKey.fallbackKey(key);
    }

    static boolean matchesRoute(StashSorter b, String key, String routeKey) {
        if (key.equals(routeKey)) return true;
        if (SortGroupKey.OVERFLOW_SHULKERS.equals(routeKey)) return SortGroupKey.isShulkerGroup(key);
        if (SortGroupKey.MIXED_ITEMS.equals(routeKey) || SortGroupKey.OVERFLOW_ITEMS.equals(routeKey)) {
            return !key.isEmpty() && !SortGroupKey.isShulkerGroup(key);
        }
        return routeKey.equals(groupOf(b, key));
    }

    /** В смешанном сундуке считаем стопки, а не предметы — иначе 64 блока «съедят» целый сундук. */
    static int groupAmount(String key, String group, int count) {
        if (!SortGroupKey.MIXED_ITEMS.equals(group)) return count;
        int maxStack = Math.max(1, maxStackOfSig(key));
        return Math.max(1, (count + maxStack - 1) / maxStack);
    }

    static String collapseKey(StashSorter b, String key, Map<String, Integer> totals, Set<String> pinnedKeys) {
        if (pinnedKeys.contains(key)) return key;

        if (SortGroupKey.isShulkerKey(key)) {
            boolean bucket = SortGroupKey.EMPTY_SHULKERS.equals(key) || SortGroupKey.KIT_SHULKERS.equals(key)
                || SortGroupKey.MISC_SHULKERS.equals(key);
            if (bucket) return key;
            return totals.getOrDefault(key, 0) < b.minShulkersForOwnChest.get() ? SortGroupKey.fallbackKey(key) : key;
        }
        if (SortGroupKey.isShulkerGroup(key)) return key;
        return b.looseItems.get() == StashSorter.LooseItems.MIXED_CHEST ? SortGroupKey.MIXED_ITEMS : key;
    }

    static int maxStackOfSig(String sig) {
        if (SortGroupKey.isShulkerKey(sig) || SortGroupKey.MIXED_ITEMS.equals(sig)) return 1;
        String id = SortGroupKey.looseItemId(sig);
        Identifier ident = Identifier.tryParse(id);
        return ident != null && Registries.ITEM.containsId(ident)
            ? new ItemStack((ItemConvertible) Registries.ITEM.get(ident)).getMaxCount()
            : 64;
    }
}
