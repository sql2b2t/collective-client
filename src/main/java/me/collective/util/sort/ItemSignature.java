package me.collective.util.sort;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Подпись стопки: идентификатор предмета плюс набор зачарований.
 * Нужна, чтобы различать «обычный обсидиан» и «зачарованный обсидиан» —
 * иначе сортировщик смешает их в одну категорию.
 */
public final class ItemSignature {
    private ItemSignature() {
    }

    public static String of(ItemStack s) {
        if (s == null || s.isEmpty()) return "";

        String itemId = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
        // У зачарованной книги зачарования лежат не на самой книге, а внутри
        ItemEnchantments ench = s.getItem() == Items.ENCHANTED_BOOK
            ? s.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
            : s.getEnchantments();

        List<String> parts = new ArrayList<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) {
            String id = e.getKey().getRegisteredName();
            if (id != null && !id.isEmpty()) parts.add(id + "=" + e.getIntValue());
        }

        return format(itemId, parts);
    }

    public static String format(String itemId, List<String> enchParts) {
        List<String> sorted = new ArrayList<>(enchParts);
        Collections.sort(sorted);
        return itemId + "|" + String.join(",", sorted);
    }
}