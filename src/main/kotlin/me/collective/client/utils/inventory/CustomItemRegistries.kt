package me.collective.client.utils.inventory

import com.mojang.serialization.Lifecycle
import java.util.function.Consumer
import java.util.function.Predicate
import net.minecraft.core.DefaultedMappedRegistry
import net.minecraft.core.DefaultedRegistry
import net.minecraft.core.Holder
import net.minecraft.core.RegistrationInfo
import net.minecraft.core.Registry
import net.minecraft.core.WritableRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item

class CustomItemRegistries private constructor() {

   companion object {
      private val registryInstallers = ArrayList<RegistryInstaller>()

      @JvmField
      val foodItems: DefaultedRegistry<Item> = createFilteredItemRegistry(
         "food", Predicate { item: Item -> ItemUtils.isFood(item) }
      )

      @JvmField
      val smithingTemplates: DefaultedRegistry<Item> = createFilteredItemRegistry(
         "templates", Predicate { item: Item -> ItemUtils.isSmithingTemplate(item) }
      )

      @JvmField
      val trimMaterials: DefaultedRegistry<Item> = createFilteredItemRegistry(
         "trim_materials", Predicate { item: Item -> ItemUtils.providesTrimMaterial(item) }
      )

      @JvmStatic
      fun initialize() {
         registryInstallers.forEach { it.installContents() }
      }

      private fun createDeferredRegistry(
         path: String, defaultEntry: String, installer: Consumer<WritableRegistry<Item>>
      ): DefaultedRegistry<Item> {
         val registryId = Identifier.fromNamespaceAndPath("collective", path)
         val registryKey: ResourceKey<Registry<Item>> = ResourceKey.createRegistryKey(registryId)
         val registry: DefaultedMappedRegistry<Item> =
            DefaultedMappedRegistry(defaultEntry, registryKey, Lifecycle.stable(), false)
         registryInstallers.add(RegistryInstaller(registry, installer))
         return registry
      }

      private fun createFilteredItemRegistry(
         path: String, itemFilter: Predicate<Item>
      ): DefaultedRegistry<Item> {
         return createDeferredRegistry(
            path,
            "air"
         ) { targetRegistry: WritableRegistry<Item> ->
            BuiltInRegistries.ITEM.listElements()
               .filter { candidateHolder: Holder.Reference<Item> ->
                  itemFilter.test(candidateHolder.value())
               }
               .forEach { matchingHolder: Holder.Reference<Item> ->
                  val itemKey = matchingHolder.key()
                  targetRegistry.register(itemKey, matchingHolder.value(), RegistrationInfo.BUILT_IN)
               }
         }
      }

      private data class RegistryInstaller(
         val registry: WritableRegistry<Item>,
         val installer: Consumer<WritableRegistry<Item>>
      ) {
         fun installContents() {
            this.installer.accept(this.registry)
         }
      }
   }
}
