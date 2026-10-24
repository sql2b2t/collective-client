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

        String itemId = Registries.ITEM.getId(s.getItem()).toString();
        // У зачарованной книги зачарования лежат не на самой книге, а внутри
        ItemEnchantmentsComponent ench = s.getItem() == Items.ENCHANTED_BOOK
            ? s.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT)
            : s.getEnchantments();

        List<String> parts = new ArrayList<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : ench.getEnchantmentEntries()) {
            String id = e.getKey().getIdAsString();
            if (!id.isEmpty()) parts.add(id + "=" + e.getIntValue());
        }

        return format(itemId, parts);
    }

    public static String format(String itemId, List<String> enchParts) {
        List<String> sorted = new ArrayList<>(enchParts);
        Collections.sort(sorted);
        return itemId + "|" + String.join(",", sorted);
    }
}