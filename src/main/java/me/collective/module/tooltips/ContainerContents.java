package me.collective.module.tooltips;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Чтение содержимого контейнера.
 *
 * <p>Перенос из модуля Tooltips: сначала берётся компонент {@code CONTAINER},
 * при его отсутствии — NBT {@code BlockEntityTag.Items}. Второй путь нужен для
 * предметов, полученных до перехода на компоненты, и для серверов, которые
 * шлют содержимое шилкера в NBT.
 *
 * <p>Сетка всегда из 27 ячеек: пустые слоты — {@link ItemStack#EMPTY}, чтобы
 * сохранить форму 3×9.
 */
public final class ContainerContents {

    /** Число слотов шилкера. */
    public static final int SLOTS = 27;

    private ContainerContents() {
    }

    /**
     * Читает 27 слотов контейнера.
     *
     * @return список из 27 стаков, пустые ячейки — {@link ItemStack#EMPTY}
     */
    public static List<ItemStack> read(ItemStack stack) {
        List<ItemStack> slots = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) slots.add(ItemStack.EMPTY);

        ItemContainerContents component = stack.get(DataComponents.CONTAINER);
        if (component != null) {
            List<ItemStack> contents = component.itemCopies().toList();
            for (int i = 0; i < contents.size() && i < SLOTS; i++) {
                ItemStack copy = contents.get(i).copy();
                // в оригинале нулевое количество помечается значением 69,
                // чтобы отрисовать его как «0»
                if (copy.getCount() == 0) copy.setCount(ContainerTooltipComponent.ZERO_MARKER);
                slots.set(i, copy);
            }
            return slots;
        }

        readFromNbt(stack, slots);
        return slots;
    }

    /**
     * Запасной путь: содержимое в NBT.
     *
     * <p>Компоненты из NBT не восстанавливаются — только предмет и количество.
     * Так же поступает {@code ShulkerDataParser} в аддоне: полное восстановление
     * требует реестрового кодека, а на этих серверах NBT-зачасто обрезан.
     */
    private static void readFromNbt(ItemStack stack, List<ItemStack> slots) {
        CustomData custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag nbt = custom.copyTag();

        if (nbt == null || !nbt.contains("BlockEntityTag")) return;

        Optional<CompoundTag> blockEntityTag = nbt.getCompound("BlockEntityTag");
        if (blockEntityTag.isEmpty() || !blockEntityTag.get().contains("Items")) return;

        Optional<ListTag> itemsOpt = blockEntityTag.get().getList("Items");
        if (itemsOpt.isEmpty()) return;

        ListTag items = itemsOpt.get();
        for (int i = 0; i < items.size() && i < SLOTS; i++) {
            Optional<CompoundTag> itemOpt = items.getCompound(i);
            if (itemOpt.isEmpty()) continue;

            ItemStack parsed = parseItem(itemOpt.get());
            if (parsed.isEmpty()) continue;

            // тот же маркер нулевого количества, что и в компонентном пути
            if (parsed.getCount() == 0) parsed.setCount(ContainerTooltipComponent.ZERO_MARKER);
            slots.set(i, parsed);
        }
    }

    /** Предмет по идентификатору и количеству из NBT. */
    private static ItemStack parseItem(CompoundTag tag) {
        String id = tag.getStringOr("id", "");
        if (id.isEmpty()) return ItemStack.EMPTY;

        int count = 1;
        if (tag.contains("count")) count = tag.getIntOr("count", 1);
        else if (tag.contains("Count")) count = tag.getByteOr("Count", (byte) 1);

        Identifier itemId = Identifier.tryParse(id);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY;

        return new ItemStack(BuiltInRegistries.ITEM.getValue(itemId), count);
    }

    /** Есть ли что показывать. */
    public static boolean hasContents(List<ItemStack> slots) {
        return slots.stream().anyMatch(stack -> !stack.isEmpty());
    }
}