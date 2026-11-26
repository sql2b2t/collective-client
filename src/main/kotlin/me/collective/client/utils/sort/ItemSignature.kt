package me.collective.client.utils.sort

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.ItemEnchantments
import java.util.Collections

/**
 * Подпись стопки: идентификатор предмета плюс набор зачарований.
 * Нужна, чтобы различать «обычный обсидиан» и «зачарованный обсидиан» —
 * иначе сортировщик смешает их в одну категорию.
 */
class ItemSignature private constructor() {
    companion object {
        @JvmStatic
        fun of(s: ItemStack?): String {
            if (s == null || s.isEmpty) return ""

            val itemId = BuiltInRegistries.ITEM.getKey(s.item).toString()
            // У зачарованной книги зачарования лежат не на самой книге, а внутри
            val ench: ItemEnchantments = if (s.item === Items.ENCHANTED_BOOK)
                s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
            else
                s.enchantments

            val parts = ArrayList<String>()
            for (e in ench.entrySet()) {
                val id: String = e.key.registeredName
                if (id.isNotEmpty()) parts.add("$id=${e.intValue}")
            }

            return format(itemId, parts)
        }

        @JvmStatic
        fun format(itemId: String, enchParts: List<String>): String {
            val sorted = ArrayList(enchParts)
            Collections.sort(sorted)
            return "$itemId|${sorted.joinToString(",")}"
        }
    }
}
