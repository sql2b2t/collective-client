package me.collective.client.utils.sort

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.TreeSet

/**
 * Ключ категории («сигнатура») — то, по чему сортировщик решает, что куда.
 *
 * <ul>
 *   <li>Шалкер, где 60%+ стопок одного предмета → {@code shulker#minecraft:обсидиан}</li>
 *   <li>Шалкер с набором разного → {@code shulker#minecraft:обсидиан,minecraft:золото} (кит)</li>
 *   <li>Пустой шалкер → {@code shulker#empty}</li>
 *   <li>Рассыпанный предмет → его [ItemSignature.of]</li>
 * </ul>
 */
class SortGroupKey private constructor() {
    companion object {
        const val SHULKER_PREFIX: String = "shulker#"
        const val EMPTY_SHULKERS: String = "shulker#empty"
        const val KIT_SHULKERS: String = "shulker#kits"
        const val MISC_SHULKERS: String = "shulker#misc"
        const val MIXED_ITEMS: String = "mixed#items"
        const val OVERFLOW_SHULKERS: String = "shulker#overflow"
        const val OVERFLOW_ITEMS: String = "mixed#overflow"
        /** Ниже этой доли стопок один предмет считается «китом», а не россыпью. */
        private const val DOMINANT_PERCENT: Int = 60

        @JvmStatic
        fun of(s: ItemStack?): String {
            if (s == null || s.isEmpty) return ""
            if (!ContainerGeometry.isShulkerItem(s)) return ItemSignature.of(s)

            val stackShare = HashMap<Item, Int>()
            var totalStacks = 0
            for (nested in ShulkerDataParser.parseShulkerContentsAsList(s)) {
                if (nested != null && !nested.isEmpty) {
                    stackShare.merge(nested.item, 1) { a, b -> a + b }
                    totalStacks++
                }
            }

            if (stackShare.isEmpty()) return EMPTY_SHULKERS

            var top: Item? = null
            var topStacks = 0
            for (e in stackShare.entries) {
                if (e.value > topStacks) {
                    top = e.key
                    topStacks = e.value
                }
            }

            // Один предмет занимает большую часть шалкера — называем его содержимым
            if (stackShare.size != 1 && topStacks * 100 < DOMINANT_PERCENT * totalStacks) {
                val ids = ArrayList<String>()
                for (item in stackShare.keys) ids.add(BuiltInRegistries.ITEM.getKey(item).toString())
                return format(ids)
            }

            return SHULKER_PREFIX + BuiltInRegistries.ITEM.getKey(top!!)
        }

        /** Кит = шалкер с перечислением нескольких предметов. */
        @JvmStatic
        fun isExactKitKey(key: String): Boolean {
            return isShulkerKey(key) && key.indexOf(',') >= 0
        }

        /** Куда сваливать то, что не опозналось как отдельная категория. */
        @JvmStatic
        fun fallbackKey(key: String): String {
            if (isExactKitKey(key)) return KIT_SHULKERS
            return if (isShulkerKey(key) &&
         key != EMPTY_SHULKERS && key != KIT_SHULKERS && key != OVERFLOW_SHULKERS
            ) MISC_SHULKERS else key
        }

        @JvmStatic
        fun overflowKey(key: String): String {
            return if (isShulkerGroup(key)) OVERFLOW_SHULKERS else OVERFLOW_ITEMS
        }

        @JvmStatic
        fun isOverflowKey(key: String): Boolean {
            return key == OVERFLOW_SHULKERS || key == OVERFLOW_ITEMS
        }

        @JvmStatic
        fun format(contentIds: Collection<String>): String {
            return if (contentIds.isEmpty()) EMPTY_SHULKERS else SHULKER_PREFIX + TreeSet(contentIds).joinToString(",")
        }

        @JvmStatic
        fun isShulkerKey(key: String): Boolean {
            return key.startsWith(SHULKER_PREFIX)
        }

        @JvmStatic
        fun isShulkerGroup(key: String): Boolean {
            return isShulkerKey(key) || itemPath(key).endsWith("shulker_box")
        }

        /** Понятное имя категории для оверлея и сообщений. */
        @JvmStatic
        fun friendlyName(key: String): String {
            if (key == EMPTY_SHULKERS) return "пустые шалкеры"
            if (key == KIT_SHULKERS) return "кит-шалкеры"
            if (key == MISC_SHULKERS) return "прочие шалкеры"
            if (key == OVERFLOW_SHULKERS) return "смешанные шалкеры"
            if (key == MIXED_ITEMS) return "смешанные предметы"
            if (key == OVERFLOW_ITEMS) return "переполненные предметы"

            if (isShulkerKey(key)) {
                val ids: List<String> = key.substring(SHULKER_PREFIX.length).split(",")
                if (ids.size == 1) return path(ids[0]) + " (шалкер)"

                val sb = StringBuilder("киты (").append(path(ids[0])).append(", ").append(path(ids[1]))
                if (ids.size > 2) sb.append(", +").append(ids.size - 2).append(" ещё")
                return sb.append(")").toString()
            }

            val bar = key.indexOf('|')
            val name = path(if (bar >= 0) key.substring(0, bar) else key)
            val enchanted = bar >= 0 && bar < key.length - 1
            return if (enchanted) name + " (зачарован)" else name
        }

        @JvmStatic
        fun looseItemId(key: String): String {
            val bar = key.indexOf('|')
            return if (bar >= 0) key.substring(0, bar) else key
        }

        /** Предметы, из которых состоит кит; для служебных ключей — пусто. */
        @JvmStatic
        fun shulkerContentIds(key: String): List<String> {
            return if (isShulkerKey(key) &&
         key != EMPTY_SHULKERS && key != KIT_SHULKERS &&
         key != MISC_SHULKERS && key != OVERFLOW_SHULKERS
            ) key.substring(SHULKER_PREFIX.length).split(",").toList() else emptyList()
        }

        @JvmStatic
        fun iconStack(key: String): ItemStack {
            if (key == MIXED_ITEMS || key == OVERFLOW_ITEMS) return ItemStack(Items.CHEST)
            return if (isShulkerKey(key)) ItemStack(Items.SHULKER_BOX) else stackFromId(looseItemId(key))
        }

        @JvmStatic
        fun badgeStack(key: String): ItemStack {
            val ids = shulkerContentIds(key)
            return if (ids.isEmpty()) ItemStack.EMPTY else stackFromId(ids[0])
        }

        private fun stackFromId(id: String): ItemStack {
            val ident = Identifier.tryParse(id)
            return if (ident != null && BuiltInRegistries.ITEM.containsKey(ident))
                ItemStack(BuiltInRegistries.ITEM.getValue(ident))
            else
                ItemStack.EMPTY
        }

        private fun itemPath(sig: String): String {
            val bar = sig.indexOf('|')
            return path(if (bar >= 0) sig.substring(0, bar) else sig)
        }

        /** Отбрасываем namespace — пользователю «обсидиан» понятнее, чем «minecraft:обсидиан». */
        private fun path(id: String): String {
            val colon = id.indexOf(':')
            return if (colon >= 0) id.substring(colon + 1) else id
        }
    }
}
