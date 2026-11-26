package me.collective.client.utils.collections

import me.collective.client.utils.inventory.ItemRegistryGroupMatcher
import me.collective.client.utils.world.BlockRegistryGroupMatcher
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier

interface RegistryGroupMatcher {
   fun getRegistryId(): Identifier?

   fun getGroupNames(): Collection<String>

   fun getGroupEntries(groupName: String): Collection<NamedRegistryEntry>

   companion object {
      @JvmField
      val MATCHERS: List<RegistryGroupMatcher> = createDefaultMatchers()

      @JvmField
      val EMPTY: EmptyRegistryGroupMatcher = EmptyRegistryGroupMatcher()

      @JvmStatic
      fun forRegistry(registry: Registry<*>): RegistryGroupMatcher {
         for (matcher in MATCHERS) {
            val registryKey = registry.key()
            val registryId = registryKey.identifier()
            if (registryId == matcher.getRegistryId()) {
               return matcher
            }
         }
         return EMPTY
      }

      private fun createDefaultMatchers(): List<RegistryGroupMatcher> {
         val blocks = BlockRegistryGroupMatcher()
         val items = ItemRegistryGroupMatcher()
         return listOf(blocks, items)
      }
   }
}
