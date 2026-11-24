package me.collective.client.utils.collections

import net.minecraft.resources.Identifier

class EmptyRegistryGroupMatcher : RegistryGroupMatcher {
   override fun getRegistryId(): Identifier? {
      return null
   }

   override fun getGroupNames(): Collection<String> {
      return emptyList()
   }

   override fun getGroupEntries(groupName: String): Collection<NamedRegistryEntry> {
      return emptyList()
   }
}
