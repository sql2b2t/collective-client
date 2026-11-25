package me.collective.client.utils.collections

import net.minecraft.resources.ResourceLocation

class EmptyRegistryGroupMatcher : RegistryGroupMatcher {
   override fun getRegistryId(): ResourceLocation? {
      return null
   }

   override fun getGroupNames(): Collection<String> {
      return emptyList()
   }

   override fun getGroupEntries(groupName: String): Collection<NamedRegistryEntry> {
      return emptyList()
   }
}
