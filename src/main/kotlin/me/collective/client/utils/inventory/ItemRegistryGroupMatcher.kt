package me.collective.client.utils.inventory

import me.collective.client.utils.collections.NamedRegistryEntry
import me.collective.client.utils.collections.RegistryGroupMatcher

import java.util.Collections
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

class ItemRegistryGroupMatcher : RegistryGroupMatcher {
   override fun getRegistryId(): ResourceLocation {
      return Registries.ITEM.location()
   }

   override fun getGroupNames(): Collection<String> {
      val blockRegistry: Registry<Block> = BuiltInRegistries.BLOCK
      return RegistryGroupMatcher.forRegistry(blockRegistry).getGroupNames()
   }

   override fun getGroupEntries(groupName: String): Collection<NamedRegistryEntry> {
      val blockRegistry: Registry<Block> = BuiltInRegistries.BLOCK
      val blockEntries = RegistryGroupMatcher.forRegistry(blockRegistry).getGroupEntries(groupName)
      if (!blockEntries.isEmpty()) {
         val blockStream = blockEntries.stream()
         return blockStream
            .map { entry -> asItemEntry(entry) }
            .toList()
      }

      return Collections.emptyList()
   }

   private fun asItemEntry(entry: NamedRegistryEntry): NamedRegistryEntry {
      return NamedRegistryEntry(entry.name, itemForEntry(entry))
   }

   private fun itemForEntry(entry: NamedRegistryEntry): Item {
      return (entry.value as Block).asItem()
   }
}
