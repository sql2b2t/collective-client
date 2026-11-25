package me.collective.client.gui.registry

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.BarrelBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.level.block.ShulkerBoxBlock
import java.util.Collections

object RegistryEntryCatalog {
    private val BLOCK_ENTRIES: List<Block> = createBlockEntries()
    private val ITEM_ENTRIES: List<Item> = createItemEntries()
    private val ENTITY_ENTRIES: List<EntityType<*>> = createEntityEntries()
    private val ENTRIES_BY_REGISTRY: Map<Registry<*>, EntryList> = mapOf(
        BuiltInRegistries.BLOCK to EntryList.of(BLOCK_ENTRIES),
        BuiltInRegistries.ITEM to EntryList.of(ITEM_ENTRIES),
        BuiltInRegistries.ENTITY_TYPE to EntryList.of(ENTITY_ENTRIES)
    )

    @JvmStatic
    fun entriesFor(registry: Registry<*>): EntryList {
        return ENTRIES_BY_REGISTRY.getOrDefault(registry, EntryList.empty())
    }

    private fun createBlockEntries(): List<Block> {
        val specialBlocks = mutableListOf<Block>(
            Blocks.OBSIDIAN,
            Blocks.ENDER_CHEST,
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL
        )
        val shulkerBoxes = mutableListOf<Block>()
        val ores = mutableListOf<Block>()
        val storageBlocks = mutableListOf<Block>()

        for (block in BuiltInRegistries.BLOCK) {
            if (block.descriptionId.contains("_ore")) {
                ores.add(block)
            }
            if (block is ShulkerBoxBlock) {
                shulkerBoxes.add(block)
            }
            if (block is ChestBlock || block is HopperBlock || block is BarrelBlock) {
                storageBlocks.add(block)
            }
        }

        return flatten(shulkerBoxes, ores, storageBlocks, specialBlocks)
    }

    private fun createItemEntries(): List<Item> {
        val items = mutableListOf<Item>(
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.GOLDEN_APPLE
        )
        BLOCK_ENTRIES.map { it.asItem() }
            .filter { it != Items.AIR }
            .forEach { items.add(it) }
        return Collections.unmodifiableList(items)
    }

    private fun createEntityEntries(): List<EntityType<*>> {
        return listOf(
            EntityType.LLAMA,
            EntityType.MULE,
            EntityType.DONKEY,
            EntityType.ITEM_FRAME,
            EntityType.ITEM,
            EntityType.ENDER_PEARL
        )
    }

    @SafeVarargs
    private fun <T> flatten(vararg categories: List<out T>): List<T> {
        val entries = mutableListOf<T>()
        for (category in categories) {
            entries.addAll(category)
        }
        return Collections.unmodifiableList(entries)
    }

    @JvmRecord
    data class EntryList(
        val orderedEntries: List<*>,
        val entrySet: Set<*>
    ) {
        companion object {
            @JvmStatic
            fun empty(): EntryList = of(emptyList<Any>())

            @JvmStatic
            fun of(entries: List<*>): EntryList = EntryList(entries, java.util.Set.copyOf(entries))
        }

        fun contains(entry: Any?): Boolean = entrySet.contains(entry)
    }
}
