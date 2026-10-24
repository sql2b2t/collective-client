package me.collective.util.sort;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Чтение содержимого шалкера. В 1.21.9+ содержимое лежит в компоненте
 * CONTAINER, но на части серверов (и в старых сохранениях) ещё в NBT —
 * поэтому читаем оба варианта.
 */
public final class ShulkerDataParser {
    private ShulkerDataParser() {
    }

    /** Сколько всего предметов каждого вида внутри шалкера. */
    public static Map<Item, Integer> parseShulkerContents(ItemStack shulkerStack) {
        Map<Item, Integer> itemCounts = new HashMap<>();

        ItemContainerContents container = shulkerStack.get(DataComponents.CONTAINER);
        if (container != null) {
            container.itemCopies().toList().forEach(stack -> {
                if (!stack.isEmpty()) itemCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            });
            if (!itemCounts.isEmpty()) return itemCounts;
        }

        CustomData customData = shulkerStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = customData.copyTag();
        if (nbt != null && nbt.contains("BlockEntityTag")) {
            Optional<CompoundTag> optional = nbt.getCompound("BlockEntityTag");
            if (optional.isPresent() && optional.get().contains("Items")) {
                Optional<ListTag> itemsListOpt = optional.get().getList("Items");
                if (itemsListOpt.isPresent()) {
                    ListTag items = itemsListOpt.get();
                    for (int i = 0; i < items.size(); i++) {
                        Optional<CompoundTag> itemOpt = items.getCompound(i);
                        if (itemOpt.isPresent()) {
                            ItemStack parsed = parseItemFromNbt(itemOpt.get());
                            if (!parsed.isEmpty()) itemCounts.merge(parsed.getItem(), parsed.getCount(), Integer::sum);
                        }
                    }
                }
            }
        }

        return itemCounts;
    }

    /** Содержимое списком стопок — нужно, чтобы понять набор внутри кита. */
    public static List<ItemStack> parseShulkerContentsAsList(ItemStack shulkerStack) {
        List<ItemStack> items = new ArrayList<>();

        ItemContainerContents container = shulkerStack.get(DataComponents.CONTAINER);
        if (container != null) {
            container.itemCopies().forEach(stack -> {
                if (stack != null && !stack.isEmpty()) items.add(stack.copy());
            });
            if (!items.isEmpty()) return items;
        }

        // Из общего количества собираем обратно полные стопки
        for (Entry<Item, Integer> entry : parseShulkerContents(shulkerStack).entrySet()) {
            int count = entry.getValue();
            int maxStack = entry.getKey().getDefaultInstance().getMaxStackSize();
            while (count > 0) {
                int stackSize = Math.min(count, maxStack);
                items.add(new ItemStack(entry.getKey(), stackSize));
                count -= stackSize;
            }
        }

        return items;
    }

    private static ItemStack parseItemFromNbt(CompoundTag itemTag) {
        String id = itemTag.getStringOr("id", "");
        if (id.isEmpty()) return ItemStack.EMPTY;

        int count = 1;
        if (itemTag.contains("count")) count = itemTag.getIntOr("count", 1);
        else if (itemTag.contains("Count")) count = itemTag.getByteOr("Count", (byte) 1);

        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;

        return new ItemStack(BuiltInRegistries.ITEM.getValue(itemId), count);
    }
}