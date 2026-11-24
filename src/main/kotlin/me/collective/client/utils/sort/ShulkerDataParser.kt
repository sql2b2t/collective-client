package me.collective.client.utils.sort

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemContainerContents

/**
 * Чтение содержимого шалкера. В 1.21.9+ содержимое лежит в компоненте
 * CONTAINER, но на части серверов (и в старых сохранениях) ещё в NBT —
 * поэтому читаем оба варианта.
 */
class ShulkerDataParser private constructor() {
    companion object {
        /** Сколько всего предметов каждого вида внутри шалкера. */
        @JvmStatic
        fun parseShulkerContents(shulkerStack: ItemStack): MutableMap<Item, Int> {
            val itemCounts = HashMap<Item, Int>()

            val container: ItemContainerContents? = shulkerStack.get(DataComponents.CONTAINER)
            if (container != null) {
                container.stream().forEach { stack ->
                    if (!stack.isEmpty) itemCounts.merge(stack.item, stack.count) { a, b -> a + b }
                }
                if (itemCounts.isNotEmpty()) return itemCounts
            }

            val customData = shulkerStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            val nbt: CompoundTag = customData.copyTag()
            if (nbt.contains("BlockEntityTag")) {
                nbt.getCompound("BlockEntityTag").ifPresent { blockEntityTag ->
                    if (!blockEntityTag.contains("Items")) return@ifPresent
                    blockEntityTag.getList("Items").ifPresent { items ->
                        for (i in 0 until items.size) {
                            items.getCompound(i).ifPresent { itemTag ->
                                val parsed = parseItemFromNbt(itemTag)
                                if (!parsed.isEmpty) itemCounts.merge(parsed.item, parsed.count) { a, b -> a + b }
                            }
                        }
                    }
                }
            }

            return itemCounts
        }

        /** Содержимое списком стопок — нужно, чтобы понять набор внутри кита. */
        @JvmStatic
        fun parseShulkerContentsAsList(shulkerStack: ItemStack): MutableList<ItemStack> {
            val items = ArrayList<ItemStack>()

            val container: ItemContainerContents? = shulkerStack.get(DataComponents.CONTAINER)
            if (container != null) {
                container.stream().forEach { stack ->
                    if (stack != null && !stack.isEmpty) items.add(stack.copy())
                }
                if (items.isNotEmpty()) return items
            }

            // Из общего количества собираем обратно полные стопки
            for (entry in parseShulkerContents(shulkerStack).entries) {
                var count = entry.value
                val maxStack = entry.key.defaultMaxStackSize
                while (count > 0) {
                    val stackSize = minOf(count, maxStack)
                    items.add(ItemStack(entry.key, stackSize))
                    count -= stackSize
                }
            }

            return items
        }

        private fun parseItemFromNbt(itemTag: CompoundTag): ItemStack {
            val id: String = itemTag.getString("id").orElse("")
            if (id.isEmpty()) return ItemStack.EMPTY

            var count = 1
            if (itemTag.contains("count")) count = itemTag.getInt("count").orElse(1)
            else if (itemTag.contains("Count")) count = itemTag.getByte("Count").orElse(1.toByte()).toInt()

            val itemId = Identifier.tryParse(id)
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return ItemStack.EMPTY

            return ItemStack(BuiltInRegistries.ITEM.getValue(itemId), count)
        }
    }
}
