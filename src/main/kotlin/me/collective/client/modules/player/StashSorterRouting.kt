package me.collective.client.modules.player

import me.collective.client.utils.sort.ContainerGeometry
import me.collective.client.utils.sort.SortGroupKey
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/**
 * Маршрутизация стопок для [StashSorter]: ключ категории предмета,
 * свёртка мелких категорий и правила попадания в общий/переполненный сундук.
 * Читает настройки сортировщика и его карты маршрутов, ничего не мутирует.
 *
 * Выделено из тела [StashSorter] по паттерну компаньонов: сортировщик
 * держит тонкие делегаты, логика живёт здесь.
 */
object StashSorterRouting {

    /**
     * Куда едет эта стопка. Мелкие категории сводим в общую, если так настроено,
     * а если дома нет — в «переполненное», иначе вещи останутся в инвентаре.
     */
    @JvmStatic
    fun routeKeyOf(b: StashSorter, s: ItemStack, allowOverflow: Boolean): String? {
        val shulker = ContainerGeometry.isShulkerItem(s)
        if (shulker && !b.moveShulkers.value) return null
        if (!shulker && b.looseItems.value == StashSorter.LooseItems.IGNORE) return null

        val key = SortGroupKey.of(s)
        if (b.sigTargets.containsKey(key)) return if (allowOverflow) b.overflowRoutes.getOrDefault(key, key) else key

        val fallback = groupOf(b, key)
        if (fallback != key && b.sigTargets.containsKey(fallback)) {
            return if (allowOverflow) b.overflowRoutes.getOrDefault(fallback, fallback) else fallback
        }
        return if (allowOverflow) SortGroupKey.overflowKey(key) else null
    }

    @JvmStatic
    fun groupOf(b: StashSorter, key: String): String {
        val mapped = b.keyCollapse[key]
        if (mapped != null) return mapped
        return if (b.looseItems.value == StashSorter.LooseItems.MIXED_CHEST && !key.isEmpty() && !SortGroupKey.isShulkerGroup(key))
            SortGroupKey.MIXED_ITEMS
        else
            SortGroupKey.fallbackKey(key)
    }

    @JvmStatic
    fun matchesRoute(b: StashSorter, key: String, routeKey: String): Boolean {
        if (key == routeKey) return true
        if (SortGroupKey.OVERFLOW_SHULKERS == routeKey) return SortGroupKey.isShulkerGroup(key)
        if (SortGroupKey.MIXED_ITEMS == routeKey || SortGroupKey.OVERFLOW_ITEMS == routeKey) {
            return !key.isEmpty() && !SortGroupKey.isShulkerGroup(key)
        }
        return routeKey == groupOf(b, key)
    }

    /** В смешанном сундуке считаем стопки, а не предметы — иначе 64 блока «съедят» целый сундук. */
    @JvmStatic
    fun groupAmount(key: String, group: String, count: Int): Int {
        if (SortGroupKey.MIXED_ITEMS != group) return count
        val maxStack = maxOf(1, maxStackOfSig(key))
        return maxOf(1, (count + maxStack - 1) / maxStack)
    }

    @JvmStatic
    fun collapseKey(b: StashSorter, key: String, totals: Map<String, Int>, pinnedKeys: Set<String>): String {
        if (pinnedKeys.contains(key)) return key

        if (SortGroupKey.isShulkerKey(key)) {
            val bucket = SortGroupKey.EMPTY_SHULKERS == key || SortGroupKey.KIT_SHULKERS == key ||
         SortGroupKey.MISC_SHULKERS == key
            if (bucket) return key
            return if (totals.getOrDefault(key, 0) < b.minShulkersForOwnChest.value) SortGroupKey.fallbackKey(key) else key
        }
        if (SortGroupKey.isShulkerGroup(key)) return key
        return if (b.looseItems.value == StashSorter.LooseItems.MIXED_CHEST) SortGroupKey.MIXED_ITEMS else key
    }

    @JvmStatic
    fun maxStackOfSig(sig: String): Int {
        if (SortGroupKey.isShulkerKey(sig) || SortGroupKey.MIXED_ITEMS == sig) return 1
        val id = SortGroupKey.looseItemId(sig)
        val ident = Identifier.tryParse(id)
        return if (ident != null && BuiltInRegistries.ITEM.containsKey(ident))
            ItemStack(BuiltInRegistries.ITEM.getValue(ident)).maxStackSize
        else 64
    }
}
